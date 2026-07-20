package ai.mindconnect.sui.demo.explorer.ui;

import ai.mindconnect.sui.demo.explorer.FileStorageService;
import ai.mindconnect.sui.demo.explorer.FileStorageService.Entry;
import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiField;
import ai.mindconnect.ui.model.UiForm;
import ai.mindconnect.ui.model.UiLink;
import ai.mindconnect.ui.model.UiList;
import ai.mindconnect.ui.model.UiPage;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiText;
import ai.mindconnect.ui.model.UiTrigger;
import ai.mindconnect.ui.model.UiUpload;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static ai.mindconnect.sui.demo.explorer.FileStorageService.join;
import static ai.mindconnect.sui.demo.explorer.FileStorageService.parentOf;

/**
 * Builds the {@link UiPage} tree for one directory listing: a breadcrumb, a
 * "new folder" form, the list of entries (each a click-to-open folder or
 * click-to-download file, with a delete action), and the upload drop zone.
 */
public class ExplorerPage {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final String path;              // current directory, relative to root ("" = root)
    private final List<Entry> entries;

    public ExplorerPage(String path, List<Entry> entries) {
        this.path = path == null ? "" : path;
        this.entries = entries;
    }

    public UiPage render() {
        var stack = UiStack.of("explorer")
                .gap(20)
                .child(UiText.of("explorer-title", "📁 File Explorer").withCssClass("explorer-title"))
                .child(breadcrumb())
                .child(newFolderForm("", null))
                .child(entryList())
                .child(uploadZone());
        return UiPage.of(listUrl(path), stack);
    }

    // ── breadcrumb ──────────────────────────────────────────────────────────

    private UiStack breadcrumb() {
        var bar = UiStack.of("breadcrumb").direction(UiStack.Direction.HORIZONTAL).gap(4);
        bar.child(UiLink.of("crumb-root", listUrl(""), "root"));
        StringBuilder acc = new StringBuilder();
        String[] segments = path.isBlank() ? new String[0] : path.split("/");
        for (int i = 0; i < segments.length; i++) {
            if (acc.length() > 0) acc.append('/');
            acc.append(segments[i]);
            bar.child(UiText.of("sep-" + i, "/").withCssClass("crumb-sep"));
            bar.child(UiLink.of("crumb-" + i, listUrl(acc.toString()), segments[i]));
        }
        return bar;
    }

    // ── new folder form ───────────────────────────────────────────────────────

    /**
     * The "new folder" form. Reused by the controller to re-render itself with
     * a field-level {@code error} (and the value the user typed) when the name
     * is rejected — the server-driven validation flow.
     */
    public UiForm newFolderForm(String value, String error) {
        var name = UiField.text("name", "New folder", value == null ? "" : value)
                .asEditable().placeholder("folder name");
        if (error != null) name.error(error);
        return UiForm.of("mkdir-form", null)
                .field(name)
                .action(UiAction.primary("create", "Create folder")
                        .dispatch("POST", "/files/mkdir?path=" + enc(path), "mkdir-form"));
    }

    // ── entry list ────────────────────────────────────────────────────────────

    private UiList entryList() {
        var list = UiList.of("entries", "Contents");
        // ".." parent entry, unless we're already at the root.
        if (!path.isBlank()) {
            list.item(UiList.Item.of("up", "⬅ ..")
                    .description("parent folder")
                    .onClick(UiTrigger.go(listUrl(parentOf(path)))));
        }
        if (entries.isEmpty()) {
            list.item(UiList.Item.of("empty", "(empty folder)").description("drop a file below to upload"));
        }
        for (Entry e : entries) {
            String rel = join(path, e.name());
            var item = UiList.Item.of("e-" + e.name(), (e.directory() ? "📁 " : "📄 ") + e.name())
                    .description(describe(e));
            if (e.directory()) {
                item.onClick(UiTrigger.go(listUrl(rel)));
            } else {
                // Clicking a file downloads it (blob + save dialog via the bus).
                item.onClick(UiTrigger.download("/files/download?path=" + enc(rel)));
                item.action(UiAction.secondary("dl-" + e.name(), "Download")
                        .onClick(UiTrigger.download("/files/download?path=" + enc(rel))));
            }
            item.action(UiAction.danger("del-" + e.name(), "Delete")
                    .confirm("Delete “" + e.name() + "”?")
                    .dispatch("POST", "/files/delete?path=" + enc(rel)));
            list.item(item);
        }
        return list;
    }

    // ── upload drop zone ──────────────────────────────────────────────────────

    private UiUpload uploadZone() {
        return UiUpload.of("upload", "Upload to this folder")
                .name("files")
                .multiple()
                .dropText("Drag files here or")
                .buttonLabel("Choose files…")
                .hint("Files are stored in the current folder on the server.")
                .uploadTo("/files/upload?path=" + enc(path));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static String listUrl(String rel) {
        return "/files?path=" + enc(rel);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String describe(Entry e) {
        String when = TIME.format(Instant.ofEpochMilli(e.modifiedEpochMs()));
        return e.directory() ? "folder · " + when : humanSize(e.size()) + " · " + when;
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.0f KB", kb);
        double mb = kb / 1024.0;
        return String.format("%.1f MB", mb);
    }
}
