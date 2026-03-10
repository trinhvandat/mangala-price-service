package org.mangala.price.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mangala.price.domain.PriceUpdateEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes price update events to Kafka topic 'price.updates'.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricePublisher {

    private static final String TOPIC = "price.updates";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.kafka.enabled:true}")
    private boolean kafkaEnabled;

    /**
     * Publish a price update event to Kafka.
     * Uses symbol as partition key for consistent ordering per token.
     */
    public void publish(PriceUpdateEvent event) {
        if (!kafkaEnabled) {
            log.debug("Kafka disabled, skipping price update for {}", event.getSymbol());
            return;
        }

        try {
            String key = event.getSymbol();

            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(TOPIC, key, event);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish price update for {}: {}", event.getSymbol(), ex.getMessage());
                } else {
                    log.debug("Published price update for {} @ ${} to partition {} offset {}",
                            event.getSymbol(),
                            event.getPriceUsd(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });

        } catch (Exception e) {
            log.error("Failed to send price update for {}: {}", event.getSymbol(), e.getMessage());
        }
    }

    /**
     * Publish synchronously (for testing or critical paths).
     */
    public boolean publishSync(PriceUpdateEvent event) {
        if (!kafkaEnabled) {
            log.debug("Kafka disabled, skipping price update for {}", event.getSymbol());
            return true;
        }

        try {
            String key = event.getSymbol();

            SendResult<String, Object> result = kafkaTemplate.send(TOPIC, key, event).get();
            log.info("Published price update for {} @ ${} to partition {} offset {}",
                    event.getSymbol(),
                    event.getPriceUsd(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            return true;

        } catch (Exception e) {
            log.error("Failed to publish price update for {}: {}", event.getSymbol(), e.getMessage());
            return false;
        }
    }
}
