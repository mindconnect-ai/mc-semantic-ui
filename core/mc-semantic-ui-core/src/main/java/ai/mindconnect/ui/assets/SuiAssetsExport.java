package ai.mindconnect.ui.assets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes {@code /sui/assets.js} as a file, for a page no server stands
 * behind — a static demo, a docs site. It reads the same declarations the
 * running registry would ({@code META-INF/sui/assets.json} of every jar on
 * the classpath) and writes the module the registry would serve.
 *
 * <pre>
 * java -cp &lt;app classpath&gt; ai.mindconnect.ui.assets.SuiAssetsExport &lt;out/sui/assets.js&gt; [base]
 * </pre>
 *
 * {@code base} is put in front of every {@code href}, as a context path would
 * be. The module resolves its urls against its own location, so a relative
 * base works wherever the files are hosted: {@code ..} for a module in
 * {@code sui/} beside {@code sui-ext/}.
 */
public final class SuiAssetsExport {

    private SuiAssetsExport() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            System.err.println("usage: SuiAssetsExport <output file> [base]");
            System.exit(2);
        }
        Path out = Path.of(args[0]);
        String base = args.length > 1 ? args[1] : "";
        ClassLoader loader = Thread.currentThread().getContextClassLoader() != null
                ? Thread.currentThread().getContextClassLoader() : SuiAssetsExport.class.getClassLoader();
        var registry = new SuiAssetRegistry(loader, List.of());
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, registry.moduleScript(base), StandardCharsets.UTF_8);
        System.out.println("sui-assets: wrote " + out + " with " + registry.assets().size() + " assets: "
                + registry.assets().stream().map(SuiAsset::id).toList());
    }
}
