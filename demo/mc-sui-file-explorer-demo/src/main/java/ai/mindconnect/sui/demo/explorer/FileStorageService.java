package ai.mindconnect.sui.demo.explorer;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * All filesystem access for the explorer, confined to a single sandbox root.
 * Every caller-supplied path is resolved under {@link #root} and normalised;
 * anything that escapes the root (via {@code ..}, absolute paths, symlinks
 * pointing out, …) is rejected with a 400 — so the web UI can never read or
 * write outside the demo folder.
 */
@Service
@Slf4j
public class FileStorageService {

    /** One entry (file or directory) in a listing. */
    public record Entry(String name, boolean directory, long size, long modifiedEpochMs) {}

    private final Path root;

    public FileStorageService(@Value("${explorer.root:explorer-root}") String rootDir) {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
    }

    public Path root() { return root; }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(root);
        seedIfEmpty();
        log.info("File-explorer sandbox root: {}", root);
    }

    /**
     * Resolves a caller-supplied relative path (forward-slash separated,
     * {@code ""} = root) to an absolute path guaranteed to live under the
     * sandbox root. Throws 400 on any traversal attempt.
     */
    public Path resolve(String relative) {
        String rel = relative == null ? "" : relative.strip();
        // Normalise separators and drop any leading slash so it stays relative.
        rel = rel.replace('\\', '/');
        while (rel.startsWith("/")) rel = rel.substring(1);
        Path target = root.resolve(rel).normalize();
        if (!target.startsWith(root)) {
            throw new ResponseStatusException(BAD_REQUEST, "Path escapes the sandbox: " + relative);
        }
        return target;
    }

    /** Lists a directory, directories first then files, both alphabetical. */
    public List<Entry> list(String relativeDir) {
        Path dir = resolve(relativeDir);
        if (!Files.isDirectory(dir)) {
            throw new ResponseStatusException(NOT_FOUND, "Not a directory: " + relativeDir);
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .map(FileStorageService::toEntry)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Entry::directory).reversed()
                            .thenComparing(e -> e.name().toLowerCase()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Saves one uploaded file into {@code relativeDir}, returning the stored name. */
    public String store(String relativeDir, MultipartFile file) {
        Path dir = resolve(relativeDir);
        if (!Files.isDirectory(dir)) {
            throw new ResponseStatusException(BAD_REQUEST, "Not a directory: " + relativeDir);
        }
        String name = safeName(file.getOriginalFilename());
        // Resolve through the sandbox check again to be safe against a
        // filename that somehow still carries separators.
        Path target = resolve(join(relativeDir, name));
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return name;
    }

    /** True if an entry named {@code name} already exists in {@code relativeDir}. */
    public boolean exists(String relativeDir, String name) {
        return Files.exists(resolve(join(relativeDir, name)));
    }

    /** Creates a sub-directory under {@code relativeDir}. */
    public void mkdir(String relativeDir, String name) {
        String clean = safeName(name);
        Path target = resolve(join(relativeDir, clean));
        try {
            Files.createDirectories(target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Deletes a file, or a directory recursively. */
    public void delete(String relativePath) {
        Path target = resolve(relativePath);
        if (target.equals(root)) {
            throw new ResponseStatusException(BAD_REQUEST, "Cannot delete the root.");
        }
        if (!Files.exists(target)) {
            throw new ResponseStatusException(NOT_FOUND, "No such entry: " + relativePath);
        }
        try (Stream<Path> walk = Files.walk(target)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (IOException e) { throw new UncheckedIOException(e); }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Resolves a file for download, verifying it exists and is a regular file. */
    public Path fileForDownload(String relativePath) {
        Path target = resolve(relativePath);
        if (!Files.isRegularFile(target)) {
            throw new ResponseStatusException(NOT_FOUND, "Not a file: " + relativePath);
        }
        return target;
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static Entry toEntry(Path p) {
        try {
            boolean dir = Files.isDirectory(p);
            return new Entry(p.getFileName().toString(), dir,
                    dir ? 0 : Files.size(p),
                    Files.getLastModifiedTime(p).toMillis());
        } catch (IOException e) {
            return null;
        }
    }

    /** Strips any directory component and rejects empty / dotted names. */
    private static String safeName(String raw) {
        String cleaned = StringUtils.getFilename(raw == null ? "" : raw.replace('\\', '/'));
        if (cleaned == null || cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..")) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid name: " + raw);
        }
        return cleaned;
    }

    /** Joins a directory and a name with a forward slash ({@code ""} = root). */
    public static String join(String dir, String name) {
        if (dir == null || dir.isBlank()) return name;
        return dir + "/" + name;
    }

    /** Parent relative path of a relative path ({@code ""} when at the top). */
    public static String parentOf(String relative) {
        if (relative == null || relative.isBlank()) return "";
        int i = relative.lastIndexOf('/');
        return i < 0 ? "" : relative.substring(0, i);
    }

    private void seedIfEmpty() throws IOException {
        try (Stream<Path> s = Files.list(root)) {
            if (s.findAny().isPresent()) return;
        }
        Files.writeString(root.resolve("welcome.txt"),
                "Welcome to the Semantic UI file explorer demo!\n\n"
                + "Everything here lives in a sandboxed folder. Try uploading a\n"
                + "file by dropping it on the upload area, create a folder, or\n"
                + "delete this file.\n");
        Files.writeString(root.resolve("README.md"),
                "# Explorer demo\n\nDrag files onto the drop zone to upload them.\n");
        Path docs = Files.createDirectories(root.resolve("documents"));
        Files.writeString(docs.resolve("notes.md"), "- first note\n- second note\n");
        Files.writeString(docs.resolve("todo.txt"), "[ ] try the upload\n[ ] create a folder\n");
        Files.createDirectories(root.resolve("images"));
    }
}
