package ai.mindconnect.sui.demo.shop;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Switches the {@code sui-mode} cookie. Two flavours:
 *
 * <ul>
 *   <li><strong>SET</strong>: when the {@code mode} param is {@code "ssr"} or
 *       {@code "spa"}, store that value verbatim. Used by the header
 *       dropdown ({@code <select submitOnChange>}).</li>
 *   <li><strong>TOGGLE</strong>: when {@code mode} is absent, flip between
 *       SPA and SSR. Kept for legacy single-button toggles.</li>
 * </ul>
 *
 * <p>POST + 303 redirect back to the {@code Referer} so the browser performs
 * a fresh GET in the newly selected mode. PRG pattern avoids spurious
 * mode-flipping on prefetch / double-click.
 */
@RestController
@RequestMapping("/admin/toggle-mode")
public class ModeToggleController {

    public static final String MODE_SSR = "ssr";
    public static final String MODE_SPA = "spa";

    @PostMapping
    public ResponseEntity<Void> toggle(@RequestParam(name = "mode", required = false) String requested,
                                        HttpServletRequest request,
                                        HttpServletResponse response) {
        boolean currentlySpa = isCurrentlySpa(request);
        boolean targetSpa = switch (sanitise(requested)) {
            case MODE_SPA -> true;
            case MODE_SSR -> false;
            default       -> !currentlySpa;   // toggle
        };

        Cookie cookie = new Cookie(SuiModeFilter.COOKIE_NAME,
                targetSpa ? SuiModeFilter.VALUE_SPA : "");
        cookie.setPath("/");
        // Empty cookie + maxAge=0 acts as a delete in browsers; the
        // SuiModeFilter then treats the request as SSR by default.
        cookie.setMaxAge(targetSpa ? 60 * 60 * 24 * 30 : 0);
        response.addCookie(cookie);

        // PRG: send the user back where they came from. Falls back to /
        // when no Referer is present (direct curl, link from another origin).
        String back = request.getHeader(HttpHeaders.REFERER);
        URI dest = URI.create(back != null && !back.isBlank() ? back : "/");
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(dest).build();
    }

    /** Normalises an inbound mode param to one of MODE_SSR / MODE_SPA / "". */
    private static String sanitise(String requested) {
        if (requested == null) return "";
        return switch (requested) {
            case MODE_SSR, MODE_SPA -> requested;
            default -> "";
        };
    }

    private static boolean isCurrentlySpa(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return false;
        for (Cookie c : cookies) {
            if (SuiModeFilter.COOKIE_NAME.equals(c.getName())
                    && SuiModeFilter.VALUE_SPA.equals(c.getValue())) {
                return true;
            }
        }
        return false;
    }
}
