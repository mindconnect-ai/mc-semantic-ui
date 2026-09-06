package ai.mindconnect.ui.stateful.spring;

import ai.mindconnect.ui.model.UiPage;
import ai.mindconnect.ui.model.UiPatch;
import ai.mindconnect.ui.model.UiToast;
import ai.mindconnect.ui.stateful.Owners;
import ai.mindconnect.ui.stateful.RouteParams;
import ai.mindconnect.ui.stateful.StatefulException;
import ai.mindconnect.ui.stateful.SuiViewEngine;
import ai.mindconnect.ui.stateful.UiEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The HTTP face of the engine: one handler for every view route, one for
 * every event. Both are registered programmatically by
 * {@link SuiViewMappings}; nothing here is component-scanned.
 *
 * <p>Two clients speak to it. The SPA sends {@code Accept: application/json}
 * and gets {@code UiPage} / {@code UiPatch} bodies, which the core client
 * applies. A browser without JavaScript sends {@code Accept: text/html}: the
 * event is applied all the same and answered with a {@code 303} back to the
 * instance (Post/Redirect/Get), whose {@code GET} the core's SSR converter
 * renders. Toasts from such an event wait in the store for that {@code GET}.
 */
public class SuiViewController {

    /** Response header carrying the instance version after this request. */
    public static final String VERSION_HEADER = "Sui-State-Version";

    private static final Logger log = LoggerFactory.getLogger(SuiViewController.class);

    private final SuiViewEngine engine;
    private final ObjectMapper mapper;

    public SuiViewController(SuiViewEngine engine, ObjectMapper mapper) {
        this.engine = engine;
        this.mapper = mapper;
    }

    // ── GET <route> ────────────────────────────────────────────────────────

    public ResponseEntity<?> show(HttpServletRequest request) {
        String route = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String owner = ownerOf(request);
        String instanceId = request.getParameter(engine.settings().instanceParam());
        boolean html = wantsHtml(request);

        if (instanceId != null && !instanceId.isBlank()) {
            try {
                SuiViewEngine.PageResponse shown = engine.show(instanceId, owner);
                return page(shown, html);
            } catch (StatefulException.UnknownInstance gone) {
                // Expired, foreign or unknown: fall through and start over on this route.
                log.debug("instance {} not available, opening {} afresh", instanceId, route);
            } catch (StatefulException.Stale stale) {
                return html ? redirect(request.getRequestURI() + "?" + request.getQueryString())
                        : ResponseEntity.status(HttpStatus.CONFLICT).body(toastPatch(UiToast.warn(
                                "The page changed while loading — please try again.")));
            }
        }
        SuiViewEngine.PageResponse opened = engine.open(route, params(request), owner);
        // A fresh instance has an address of its own. The no-JS browser must be
        // sent there, or a reload would open yet another instance; the SPA
        // simply pushes it into the address bar from the page's `navigate`.
        return html ? redirect(opened.page().getNavigate()) : page(opened, false);
    }

    // ── POST <base>/{id}/{node}/{event} ────────────────────────────────────

    public ResponseEntity<?> event(@PathVariable("id") String instanceId,
                                   @PathVariable("node") String nodeId,
                                   @PathVariable("event") String event,
                                   HttpServletRequest request) throws IOException {
        boolean html = wantsHtml(request);
        String owner = ownerOf(request);
        String rowId = request.getParameter("row");
        var attachments = new ArrayList<UiEvent.Attachment>();
        Map<String, Object> payload = payload(request, attachments);
        var delivery = html ? SuiViewEngine.ToastDelivery.DEFERRED : SuiViewEngine.ToastDelivery.INLINE;

        try {
            SuiViewEngine.Response response = engine.event(instanceId, nodeId, event, rowId,
                    payload, attachments, owner, delivery);
            if (response instanceof SuiViewEngine.RedirectResponse r) {
                return redirect(r.url());
            }
            if (response instanceof SuiViewEngine.PageResponse p) {
                return html ? redirect(p.page().getNavigate()) : page(p, false);
            }
            var patch = (SuiViewEngine.PatchResponse) response;
            if (html) return redirect(backTo(request, instanceId));
            return ResponseEntity.ok()
                    .header(VERSION_HEADER, Long.toString(patch.version()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(patch.patch());
        } catch (StatefulException.UnknownInstance gone) {
            return expired(request, owner, html);
        } catch (StatefulException.Stale stale) {
            if (html) return redirect(backTo(request, instanceId));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(toastPatch(UiToast.warn(
                    "Somebody else changed this page at the same time — please reload.")));
        } catch (StatefulException.UnboundEvent unbound) {
            log.warn("{}: {}", instanceId, unbound.getMessage());
            if (html) return redirect(backTo(request, instanceId));
            return ResponseEntity.badRequest().body(toastPatch(UiToast.warn(
                    "That control is no longer on the page.")));
        } catch (StatefulException.HandlerFailed failed) {
            log.error("view handler failed on {}", instanceId, failed.getCause());
            if (html) return redirect(backTo(request, instanceId));
            // 200 on purpose: the state is intact, and the client should show
            // the message rather than a generic HTTP failure.
            return ResponseEntity.ok().body(toastPatch(UiToast.error(
                    failed.getCause() != null && failed.getCause().getMessage() != null
                            ? failed.getCause().getMessage() : "The action failed.")));
        }
    }

    // ── responses ──────────────────────────────────────────────────────────

    private ResponseEntity<?> page(SuiViewEngine.PageResponse response, boolean html) {
        var builder = ResponseEntity.ok().header(VERSION_HEADER, Long.toString(response.version()));
        if (!html) builder.contentType(MediaType.APPLICATION_JSON);
        return builder.body(response.page());
    }

    private static ResponseEntity<?> redirect(String url) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, url).build();
    }

    /**
     * The instance is gone. The client cannot know that from a patch, so we
     * reopen the route the page came from (the {@code Referer} says which) and
     * hand back a fresh page with a word of explanation.
     */
    private ResponseEntity<?> expired(HttpServletRequest request, String owner, boolean html) {
        String route = refererRoute(request);
        if (route != null && engine.registry().byRoute(route).isPresent()) {
            if (html) return redirect(route);
            SuiViewEngine.PageResponse opened = engine.open(route, RouteParams.empty(), owner);
            opened.page().toast(UiToast.warn("Your page had expired and was reloaded."));
            return page(opened, false);
        }
        return ResponseEntity.status(HttpStatus.GONE).body(toastPatch(UiToast.warn(
                "Your page has expired — please reload.")));
    }

    private String backTo(HttpServletRequest request, String instanceId) {
        String address = engine.addressOf(instanceId);
        if (address != null) return address;
        String referer = request.getHeader(HttpHeaders.REFERER);
        return referer != null ? referer : "/";
    }

    private static String refererRoute(HttpServletRequest request) {
        String referer = request.getHeader(HttpHeaders.REFERER);
        if (referer == null) return null;
        try {
            String path = URI.create(referer).getPath();
            String context = request.getContextPath();
            if (path != null && !context.isEmpty() && path.startsWith(context)) {
                path = path.substring(context.length());
            }
            return path == null || path.isEmpty() ? null : path;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static UiPatch toastPatch(UiToast toast) {
        return UiPatch.of().toast(toast);
    }

    // ── request reading ────────────────────────────────────────────────────

    static boolean wantsHtml(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE)
                && !accept.contains(MediaType.APPLICATION_JSON_VALUE);
    }

    private static String ownerOf(HttpServletRequest request) {
        String principal = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null;
        return Owners.of(principal, principal != null ? null : request.getSession(true).getId());
    }

    private RouteParams params(HttpServletRequest request) {
        Map<String, String[]> all = new LinkedHashMap<>(request.getParameterMap());
        all.remove(engine.settings().instanceParam());
        return RouteParams.ofArrays(all);
    }

    /**
     * The form values, whichever way they came: a JSON body from the SPA, a
     * form post from a no-JS browser, or multipart from an upload.
     */
    private Map<String, Object> payload(HttpServletRequest request, List<UiEvent.Attachment> attachments)
            throws IOException {
        String contentType = request.getContentType();
        if (request instanceof MultipartHttpServletRequest multipart) {
            multipart.getFileMap().forEach((name, file) -> {
                for (MultipartFile f : multipart.getFiles(name)) {
                    if (!f.isEmpty()) attachments.add(new SpringAttachment(f));
                }
            });
            return fromParameters(request);
        }
        if (contentType != null && contentType.contains(MediaType.APPLICATION_JSON_VALUE)) {
            Map<String, Object> out = new LinkedHashMap<>();
            try (InputStream in = request.getInputStream()) {
                Map<String, Object> body = mapper.readValue(in, new TypeReference<Map<String, Object>>() {
                });
                if (body != null) out.putAll(body);
            }
            // Placeholders the client substituted into the URL ({page}, …)
            // arrive as query parameters; the body knows nothing of them.
            fromParameters(request).forEach(out::putIfAbsent);
            return out;
        }
        return fromParameters(request);
    }

    private static Map<String, Object> fromParameters(HttpServletRequest request) {
        Map<String, Object> out = new LinkedHashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (k.equals("_method") || k.equals("row")) return;
            if (v == null || v.length == 0) out.put(k, "");
            else if (v.length == 1) out.put(k, v[0]);
            else out.put(k, List.of(v));
        });
        return out;
    }

    private record SpringAttachment(MultipartFile file) implements UiEvent.Attachment {
        @Override
        public String name() {
            return file.getName();
        }

        @Override
        public String filename() {
            return file.getOriginalFilename();
        }

        @Override
        public String contentType() {
            return file.getContentType();
        }

        @Override
        public long size() {
            return file.getSize();
        }

        @Override
        public InputStream open() throws IOException {
            return file.getInputStream();
        }
    }
}
