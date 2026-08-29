package ai.mindconnect.sui.demo.merge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A page you can poke at to see what {@code MERGE} does.
 *
 * <p>{@code REPLACE} needs a whole node. Flipping one flag on something the
 * user is looking at means rebuilding and resending a subtree the server did
 * not otherwise touch — and the client throwing away the one it had. {@code
 * MERGE} names the fields that changed and leaves the rest where they are.
 *
 * <p>The demo does not ask to be believed: every response prints the JSON it
 * actually sent onto the page, beside the {@code REPLACE} that would have had
 * the same effect, with both byte counts.
 */
@SpringBootApplication
public class MergeDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(MergeDemoApplication.class, args);
    }
}
