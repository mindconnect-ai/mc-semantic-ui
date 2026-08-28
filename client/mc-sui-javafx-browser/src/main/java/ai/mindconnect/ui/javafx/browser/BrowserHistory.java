package ai.mindconnect.ui.javafx.browser;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Back/forward over visited urls — the browser's own state, nothing the server
 * knows about.
 *
 * <p>Behaves like a real address bar: visiting a url while sitting somewhere in
 * the middle of the stack truncates everything ahead of it, so the forward
 * button never leads back to a branch the user has already left.
 */
public class BrowserHistory {

    private final List<String> entries = new ArrayList<>();
    private final ReadOnlyBooleanWrapper canGoBack = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper canGoForward = new ReadOnlyBooleanWrapper(false);
    private int cursor = -1;

    /** Records a visit. Re-visiting the current url is not a new entry. */
    public void visit(String url) {
        if (url == null || url.isBlank()) return;
        if (cursor >= 0 && url.equals(entries.get(cursor))) return;

        // Drop the forward branch: the user just took a different turn.
        while (entries.size() > cursor + 1) entries.remove(entries.size() - 1);
        entries.add(url);
        cursor = entries.size() - 1;
        update();
    }

    /** @return the url to load, or {@code null} when there is nothing behind */
    public String back() {
        if (cursor <= 0) return null;
        cursor--;
        update();
        return entries.get(cursor);
    }

    /** @return the url to load, or {@code null} when there is nothing ahead */
    public String forward() {
        if (cursor < 0 || cursor >= entries.size() - 1) return null;
        cursor++;
        update();
        return entries.get(cursor);
    }

    public String current() {
        return cursor < 0 ? null : entries.get(cursor);
    }

    public ReadOnlyBooleanProperty canGoBackProperty() {
        return canGoBack.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty canGoForwardProperty() {
        return canGoForward.getReadOnlyProperty();
    }

    private void update() {
        canGoBack.set(cursor > 0);
        canGoForward.set(cursor >= 0 && cursor < entries.size() - 1);
    }
}
