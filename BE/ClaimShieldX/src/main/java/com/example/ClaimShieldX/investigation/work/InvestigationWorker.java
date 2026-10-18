package com.example.ClaimShieldX.investigation.work;

import com.example.ClaimShieldX.investigation.config.InvestigationProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Polls the queue and works on due investigations, a bounded number at a time. It does not keep the queue in memory: everything it
 * holds is a lease in the database, renewed by the heartbeat, so if this process dies its work becomes claimable again once the
 * leases run out. Switched off with {@code app.investigation.worker.enabled=false}, which is how the tests drive the queue by hand.
 */
@Component
@ConditionalOnProperty(name = "app.investigation.worker.enabled", havingValue = "true", matchIfMissing = true)
public class InvestigationWorker {
    private static final Logger log = LoggerFactory.getLogger(InvestigationWorker.class);

    private final InvestigationRunner runner;
    private final InvestigationStore store;
    private final InvestigationProperties properties;
    private final Clock clock;
    private final String owner;
    private final ThreadPoolExecutor pool;
    private final AtomicInteger busy = new AtomicInteger();

    public InvestigationWorker(InvestigationRunner runner, InvestigationStore store, InvestigationProperties properties, Clock investigationClock) {
        this.runner = runner;
        this.store = store;
        this.properties = properties;
        this.clock = investigationClock;
        this.owner = "worker-" + host() + "-" + UUID.randomUUID().toString().substring(0, 8);
        int threads = properties.worker().threads();
        this.pool = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, "investigation-worker");
            thread.setDaemon(true);
            return thread;
        });
        log.info("Investigation worker {} started with {} thread(s)", owner, threads);
    }

    @Scheduled(fixedDelayString = "${app.investigation.worker.poll-interval:2s}", initialDelayString = "${app.investigation.worker.poll-interval:2s}")
    void poll() {
        try {
            while (busy.get() < properties.worker().threads()) {
                var claimed = runner.claim(owner);
                if (claimed.isEmpty()) {
                    return;
                }
                busy.incrementAndGet();
                pool.execute(() -> {
                    try {
                        runner.process(claimed.get(), owner);
                    } catch (RuntimeException e) {
                        log.error("Investigation {} stopped with {}", claimed.get().id(), e.getClass().getSimpleName());
                    } finally {
                        busy.decrementAndGet();
                    }
                });
            }
        } catch (RuntimeException e) {
            log.error("Polling the investigation queue failed with {}", e.getClass().getSimpleName());
        }
    }

    /** Keeps the leases of everything this worker holds. If this stops (the process died), the leases run out and others take over. */
    @Scheduled(fixedDelayString = "${app.investigation.heartbeat:30s}", initialDelayString = "${app.investigation.heartbeat:30s}")
    void heartbeat() {
        try {
            Instant now = clock.instant();
            store.heartbeat(owner, now.plus(properties.lease()));
        } catch (RuntimeException e) {
            log.error("Renewing investigation leases failed with {}", e.getClass().getSimpleName());
        }
    }

    @PreDestroy
    void stop() {
        pool.shutdown();
        try {
            pool.awaitTermination(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pool.shutdownNow();
    }

    private static String host() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "host";
        }
    }
}
