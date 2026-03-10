package org.mangala.price.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Price update event published to Kafka topic 'price.updates'.
 * Consumed by portfolio-service to update holdings valuations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceUpdateEvent {
    private String symbol;
    private String name;
    private BigDecimal priceUsd;
    private BigDecimal change24h;
    private Instant timestamp;
}
