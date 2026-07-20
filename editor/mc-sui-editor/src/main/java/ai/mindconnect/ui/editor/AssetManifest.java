package ai.mindconnect.ui.editor;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Build-time helper for the standalone (backend-free) editor: walks an
 * assembled site directory and writes {@code data/manifest.json} — a flat,
 * sorted list of every file under the root, each path relative to it.
 *
 * <p>A browser can't enumerate its own directory over HTTP, so the in-app
 * "Download app" exporters read this manifest to know which files to re-fetch
 * and repackage. The manifest lists itself excluded to avoid a self-reference
 * (the exporter adds it back when it needs to).
 *
 * <p>Usage: {@code AssetManifest <site-dir>}.
 */
public final class AssetManifest {

    private AssetManifest() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: AssetManifest <site-dir>");
            System.exit(2);
        }
        Path dir = Path.of(args[0]);
        write(dir);
        System.out.println("AssetManifest: wrote data/manifest.json under " + dir.toAbsolutePath());
    }

    /** Walks {@code siteDir} and writes {@code data/manifest.json} into it. */
    public static void write(Path siteDir) throws IOException {
        List<String> rel = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(siteDir)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                String r = siteDir.relativize(p).toString().replace('\\', '/');
                if (!r.equals("data/manifest.json")) rel.add(r);
            });
        }
        rel.sort(String::compareTo);

        Path manifest = siteDir.resolve("data/manifest.json");
        Files.createDirectories(manifest.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(manifest.toFile(), rel);
    }
}
