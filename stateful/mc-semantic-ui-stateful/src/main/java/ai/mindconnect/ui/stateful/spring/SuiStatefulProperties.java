package ai.mindconnect.ui.stateful.spring;

import ai.mindconnect.ui.stateful.SuiViewEngine;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code mindconnect.sui.stateful.*}.
 *
 * <pre>
 * mindconnect:
 *   sui:
 *     stateful:
 *       enabled: true              # default; false removes the endpoint and the routes
 *       event-base-path: /sui/s    # where listeners post to
 *       instance-param: _v         # query parameter carrying the instance id
 *       idle-timeout: 30m          # an instance untouched this long is dropped
 *       max-instances-per-owner: 50
 *       full-page-threshold: 0.6   # patch/page size ratio above which the page is sent
 *       reap-interval: 1m          # how often idle instances are swept
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "mindconnect.sui.stateful")
public class SuiStatefulProperties {

    private boolean enabled = true;
    private String eventBasePath = "/sui/s";
    private String instanceParam = "_v";
    private Duration idleTimeout = Duration.ofMinutes(30);
    private int maxInstancesPerOwner = 50;
    private double fullPageThreshold = 0.6;
    private Duration reapInterval = Duration.ofMinutes(1);

    public SuiViewEngine.Settings toSettings() {
        String base = eventBasePath.endsWith("/")
                ? eventBasePath.substring(0, eventBasePath.length() - 1) : eventBasePath;
        return new SuiViewEngine.Settings(base, instanceParam, idleTimeout, maxInstancesPerOwner, fullPageThreshold);
    }
}
