package ai.mindconnect.sui.demo.explorer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * File-explorer demo: a SUI-rendered view over a sandboxed folder on the real
 * filesystem. Browse, download, delete, create folders, and upload files via
 * the {@code UiUpload} drop zone.
 */
@SpringBootApplication
public class FileExplorerApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileExplorerApplication.class, args);
    }
}
