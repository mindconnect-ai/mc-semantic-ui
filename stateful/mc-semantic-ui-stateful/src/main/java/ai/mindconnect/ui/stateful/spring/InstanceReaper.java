package ai.mindconnect.ui.stateful.spring;

import ai.mindconnect.ui.stateful.SuiViewEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Sweeps idle instances out of the store on a fixed interval. */
public class InstanceReaper implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(InstanceReaper.class);

    private final SuiViewEngine engine;
    private final Duration interval;
    private ScheduledExecutorService executor;

    public InstanceReaper(SuiViewEngine engine, Duration interval) {
        this.engine = engine;
        this.interval = interval;
    }

    @Override
    public void afterPropertiesSet() {
        if (interval == null || interval.isZero() || interval.isNegative()) return;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sui-stateful-reaper");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::sweep, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    void sweep() {
        try {
            int gone = engine.reap();
            if (gone > 0) log.debug("dropped {} idle view instances", gone);
        } catch (RuntimeException e) {
            log.warn("view instance sweep failed", e);
        }
    }

    @Override
    public void destroy() {
        if (executor != null) executor.shutdownNow();
    }
}
