package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.model.UiRow;
import ai.mindconnect.ui.model.UiTrigger;

/**
 * Resolves the placeholders a trigger template carries.
 *
 * <p>Some triggers are written once and used many times: one row-action
 * trigger serves every row of a table, one page trigger serves every page of a
 * pager. The model says so in as many words — {@code UiList.Pagination} and
 * {@code UiTable.Pagination} both document that the renderer substitutes the
 * literal {@code {page}} — and the SPA has always done it.
 *
 * <p>The JavaFX renderers did not, so those triggers went out with the
 * placeholder still in the url. Nothing failed loudly: the request simply went
 * to a path that did not exist, and the button looked broken.
 */
final class Triggers {

    private Triggers() {
    }

    /** A copy of {@code template} with {@code {page}} replaced by {@code page}. */
    static UiTrigger forPage(UiTrigger template, int page) {
        return substitute(template, "{page}", String.valueOf(page));
    }

    /**
     * A copy of {@code template} with {@code {id}} replaced by the row's id.
     *
     * <p>The id may sit on the row itself or in its data map, depending on how
     * the server built the table, so both are tried.
     */
    static UiTrigger forRow(UiTrigger template, UiRow row) {
        if (template == null || row == null) return template;
        var id = row.getId();
        if (id == null && row.getData() != null && row.getData().get("id") != null) {
            id = String.valueOf(row.getData().get("id"));
        }
        return substitute(template, "{id}", id);
    }

    /**
     * @return {@code template} itself when there is nothing to substitute —
     *         no placeholder in the url, or no value to put in its place.
     *         Sending the template unchanged at least lets the server answer;
     *         swallowing the click would leave the user with nothing at all.
     */
    private static UiTrigger substitute(UiTrigger template, String placeholder, String value) {
        if (template == null || value == null) return template;
        var url = template.getUrl();
        if (url == null || !url.contains(placeholder)) return template;

        var copy = new UiTrigger();
        copy.setUrl(url.replace(placeholder, value));
        copy.setMethod(template.getMethod());
        copy.setPayload(template.getPayload());
        copy.setBehavior(template.getBehavior());
        copy.setHandler(template.getHandler());
        copy.setPatch(template.getPatch());
        return copy;
    }
}
