package ai.mindconnect.sui.demo.explorer;

import ai.mindconnect.sui.demo.explorer.ui.ExplorerPage;
import ai.mindconnect.ui.model.UiForm;
import ai.mindconnect.ui.model.UiPage;
import ai.mindconnect.ui.model.UiPatch;
import ai.mindconnect.ui.model.UiToast;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.nio.file.Path;
import java.util.List;

import static ai.mindconnect.sui.demo.explorer.FileStorageService.parentOf;

/**
 * REST + SUI controller for the file explorer. Every listing / mutation
 * returns a {@link UiPage}; the browser gets HTML on a direct navigation and
 * JSON once the SPA bus is running. Downloads stream the raw file bytes.
 */
@RestController
@RequiredArgsConstructor
public class FileExplorerController {

    private final FileStorageService storage;

    /** Landing page → the root listing. */
    @GetMapping("/")
    public RedirectView index() {
        return new RedirectView("/files");
    }

    /** Directory listing (the main view). */
    @GetMapping(path = "/files", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_HTML_VALUE})
    public UiPage list(@RequestParam(defaultValue = "") String path) {
        return new ExplorerPage(path, storage.list(path)).render();
    }

    /** Multipart upload into {@code path}; re-renders that folder with a toast. */
    @PostMapping(path = "/files/upload", produces = MediaType.APPLICATION_JSON_VALUE)
    public UiPage upload(@RequestParam(defaultValue = "") String path,
                         @RequestParam("files") org.springframework.web.multipart.MultipartFile[] files) {
        int count = 0;
        for (var file : files) {
            if (file != null && !file.isEmpty()) {
                storage.store(path, file);
                count++;
            }
        }
        return new ExplorerPage(path, storage.list(path)).render()
                .toast(UiToast.success(count == 1 ? "1 file uploaded." : count + " files uploaded."));
    }

    /**
     * Creates a sub-folder in {@code path}. Body: {@code {"name": "…"}}. On an
     * invalid name it returns a {@link UiPatch} that re-renders just the form
     * with a field-level validation error (and the value the user typed) — the
     * server-driven validation flow; a valid name re-renders the whole folder.
     */
    @PostMapping(path = "/files/mkdir", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Object mkdir(@RequestParam(defaultValue = "") String path,
                        @RequestBody MkdirRequest body) {
        String name = body.name() == null ? "" : body.name().strip();
        String error = validateFolderName(path, name);
        if (error != null) {
            UiForm form = new ExplorerPage(path, List.of()).newFolderForm(name, error);
            return UiPatch.of().patch(UiPatch.Operation.replace("mkdir-form", form));
        }
        storage.mkdir(path, name);
        return new ExplorerPage(path, storage.list(path)).render()
                .toast(UiToast.success("Folder “" + name + "” created."));
    }

    /** Returns a validation message for a proposed folder name, or null if it's OK. */
    private String validateFolderName(String path, String name) {
        if (name.isBlank()) return "Please enter a folder name.";
        if (name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) {
            return "The name contains invalid characters.";
        }
        if (storage.exists(path, name)) {
            return "A file or folder named “" + name + "” already exists.";
        }
        return null;
    }

    /** Deletes the entry at {@code path}, then re-renders its parent folder. */
    @PostMapping(path = "/files/delete", produces = MediaType.APPLICATION_JSON_VALUE)
    public UiPage delete(@RequestParam String path) {
        storage.delete(path);
        String parent = parentOf(path);
        return new ExplorerPage(parent, storage.list(parent)).render()
                .toast(UiToast.info("Deleted."));
    }

    /** Streams a file for download with a {@code Content-Disposition} attachment header. */
    @GetMapping("/files/download")
    public ResponseEntity<Resource> download(@RequestParam String path) {
        Path file = storage.fileForDownload(path);
        Resource body = new FileSystemResource(file);
        String filename = file.getFileName().toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename.replace("\"", "") + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    /** JSON body for {@link #mkdir}. */
    public record MkdirRequest(String name) {}
}
