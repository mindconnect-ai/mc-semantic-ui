package ai.mindconnect.sui.demo.shop;

import ai.mindconnect.ui.model.UiPage;
import ai.mindconnect.ui.model.UiToast;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Session-scoped flash queue for toasts that survive a PRG redirect.
 *
 * <p>Typical flow:
 * <ol>
 *   <li>POST /admin/products → service creates the entity →
 *       {@code toastQueue.add(UiToast.success("Saved"))} →
 *       303 redirect to /admin/products/{id}.</li>
 *   <li>GET /admin/products/{id} → controller builds its UiPage and calls
 *       {@code toastQueue.drainOnto(page)} just before returning. The queue
 *       attaches every pending toast and clears itself so each toast is
 *       delivered exactly once.</li>
 * </ol>
 *
 * <p>Scoped session: a single browser window/session owns its own queue,
 * preventing cross-user leakage. The {@code TARGET_CLASS} proxy lets
 * singleton callers (controllers) inject it directly.
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ToastQueue {

    private final List<UiToast> pending = new ArrayList<>();

    /** Adds a toast to the queue. Survives until the next page render drains it. */
    public synchronized void add(UiToast toast) {
        if (toast != null) pending.add(toast);
    }

    /** Attaches every pending toast to {@code page} and empties the queue. */
    public synchronized void drainOnto(UiPage page) {
        if (page == null || pending.isEmpty()) return;
        for (UiToast t : pending) page.toast(t);
        pending.clear();
    }
}
