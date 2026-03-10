package org.mangala.price.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mangala.price.adapter.coingecko.CoinGeckoClient;
import org.mangala.price.domain.PriceUpdateEvent;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Orchestrates price fetching from external APIs and caching.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceFetchService {

    private static final String REDIS_KEY_PREFIX = "price:";
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final CoinGeckoClient coinGeckoClient;
    private final PricePublisher pricePublisher;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    /**
     * Fetch all configured prices and publish updates.
     */
    public Flux<PriceUpdateEvent> fetchAndPublishAll() {
        log.info("Fetching prices from CoinGecko...");

        return coinGeckoClient.fetchPrices()
                .doOnNext(event -> {
                    // Cache in Redis
                    cachePrice(event).subscribe();
                    // Publish to Kafka
                    pricePublisher.publish(event);
                })
                .doOnComplete(() -> log.info("Price update cycle completed"))
                .doOnError(e -> log.error("Price fetch failed: {}", e.getMessage()));
    }

    /**
     * Get cached price for a symbol.
     */
    public Mono<PriceUpdateEvent> getCachedPrice(String symbol) {
        String key = REDIS_KEY_PREFIX + symbol.toUpperCase();
        return redisTemplate.opsForValue().get(key)
                .map(this::deserializePrice)
                .switchIfEmpty(Mono.empty());
    }

    /**
     * Cache a price event in Redis.
     */
    private Mono<Boolean> cachePrice(PriceUpdateEvent event) {
        String key = REDIS_KEY_PREFIX + event.getSymbol();
        String value = serializePrice(event);
        return redisTemplate.opsForValue().set(key, value, CACHE_TTL);
    }

    private String serializePrice(PriceUpdateEvent event) {
        return String.format("%s|%s|%s|%s",
                event.getSymbol(),
                event.getPriceUsd().toPlainString(),
                event.getChange24h() != null ? event.getChange24h().toPlainString() : "0",
                event.getTimestamp().toString());
    }

    private PriceUpdateEvent deserializePrice(String value) {
        String[] parts = value.split("\\|");
        if (parts.length < 4) {
            return null;
        }
        return PriceUpdateEvent.builder()
                .symbol(parts[0])
                .priceUsd(new java.math.BigDecimal(parts[1]))
                .change24h(new java.math.BigDecimal(parts[2]))
                .timestamp(java.time.Instant.parse(parts[3]))
                .build();
    }
}
