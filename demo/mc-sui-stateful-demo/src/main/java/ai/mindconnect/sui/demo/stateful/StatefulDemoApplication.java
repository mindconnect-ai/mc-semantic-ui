package ai.mindconnect.sui.demo.stateful;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The product admin of the shop demo, written as stateful views: no
 * controller per action, Java listeners on the nodes, only the difference
 * on the wire. See README.md.
 */
@SpringBootApplication
public class StatefulDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(StatefulDemoApplication.class, args);
    }
}
