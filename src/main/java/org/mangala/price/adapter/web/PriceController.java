package org.mangala.price.adapter.web;

import lombok.RequiredArgsConstructor;
import org.mangala.price.domain.PriceUpdateEvent;
import org.mangala.price.service.PriceFetchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST endpoints for price queries.
 * Main purpose is Kafka publishing, but this provides direct access.
 */
@RestController
@RequestMapping("/api/v1/prices")
@RequiredArgsConstructor
public class PriceController {

    private final PriceFetchService priceFetchService;

    /**
     * Get all current prices.
     */
    @GetMapping
    public Flux<PriceUpdateEvent> getAllPrices() {
        return priceFetchService.fetchAndPublishAll();
    }

    /**
     * Get cached price for a specific symbol.
     */
    @GetMapping("/{symbol}")
    public Mono<ResponseEntity<PriceUpdateEvent>> getPrice(@PathVariable String symbol) {
        return priceFetchService.getCachedPrice(symbol)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Health check / ping.
     */
    @GetMapping("/health")
    public Mono<String> health() {
        return Mono.just("OK");
    }
}
