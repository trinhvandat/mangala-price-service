package org.mangala.price.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mangala.price.service.PriceFetchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduled job to fetch and publish price updates.
 * Default interval: 30 seconds.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceUpdateScheduler {

    private final PriceFetchService priceFetchService;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    @Value("${price.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    /**
     * Fetch and publish prices every 30 seconds.
     * Uses fixedDelay to prevent overlap if fetching takes longer.
     */
    @Scheduled(fixedDelayString = "${price.scheduler.interval-ms:30000}", initialDelayString = "${price.scheduler.initial-delay-ms:5000}")
    public void scheduledPriceUpdate() {
        if (!schedulerEnabled) {
            return;
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Previous price update still running, skipping this cycle");
            return;
        }

        try {
            priceFetchService.fetchAndPublishAll()
                    .doFinally(signal -> isRunning.set(false))
                    .subscribe(
                            event -> log.debug("Updated price: {} @ ${}", event.getSymbol(), event.getPriceUsd()),
                            error -> log.error("Price update failed: {}", error.getMessage()),
                            () -> log.debug("Price update cycle completed")
                    );
        } catch (Exception e) {
            log.error("Unexpected error in price scheduler: {}", e.getMessage(), e);
            isRunning.set(false);
        }
    }
}
