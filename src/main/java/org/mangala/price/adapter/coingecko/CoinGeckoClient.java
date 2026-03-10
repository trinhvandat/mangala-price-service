package org.mangala.price.adapter.coingecko;

import lombok.extern.slf4j.Slf4j;
import org.mangala.price.domain.PriceUpdateEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Client for CoinGecko API to fetch cryptocurrency prices.
 * Free tier: 30 calls/minute.
 */
@Slf4j
@Component
public class CoinGeckoClient {

    private final WebClient webClient;
    private final List<String> defaultTokenIds;

    public CoinGeckoClient(
            @Value("${price.coingecko.base-url:https://api.coingecko.com/api/v3}") String baseUrl,
            @Value("${price.coingecko.tokens:bitcoin,ethereum,binancecoin,matic-network,solana,usd-coin,tether}") List<String> defaultTokenIds) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.defaultTokenIds = defaultTokenIds;
    }

    /**
     * Fetch prices for configured tokens from CoinGecko.
     * Uses /simple/price endpoint for efficiency.
     */
    public Flux<PriceUpdateEvent> fetchPrices() {
        String ids = String.join(",", defaultTokenIds);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/simple/price")
                        .queryParam("ids", ids)
                        .queryParam("vs_currencies", "usd")
                        .queryParam("include_24hr_change", "true")
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(10))
                .doOnNext(response -> log.debug("CoinGecko response: {}", response))
                .flatMapMany(this::mapToPriceEvents)
                .onErrorResume(e -> {
                    log.error("Error fetching prices from CoinGecko: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    /**
     * Fetch price for a single token by CoinGecko ID.
     */
    public Mono<PriceUpdateEvent> fetchPrice(String coinGeckoId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/simple/price")
                        .queryParam("ids", coinGeckoId)
                        .queryParam("vs_currencies", "usd")
                        .queryParam("include_24hr_change", "true")
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(10))
                .flatMap(response -> mapSinglePrice(coinGeckoId, response));
    }

    @SuppressWarnings("unchecked")
    private Flux<PriceUpdateEvent> mapToPriceEvents(Map<String, Object> response) {
        return Flux.fromIterable(response.entrySet())
                .map(entry -> {
                    String coinId = entry.getKey();
                    Map<String, Object> priceData = (Map<String, Object>) entry.getValue();

                    BigDecimal priceUsd = extractBigDecimal(priceData.get("usd"));
                    BigDecimal change24h = extractBigDecimal(priceData.get("usd_24h_change"));

                    return PriceUpdateEvent.builder()
                            .symbol(mapCoinIdToSymbol(coinId))
                            .name(coinId)
                            .priceUsd(priceUsd)
                            .change24h(change24h)
                            .timestamp(Instant.now())
                            .build();
                });
    }

    @SuppressWarnings("unchecked")
    private Mono<PriceUpdateEvent> mapSinglePrice(String coinId, Map<String, Object> response) {
        if (!response.containsKey(coinId)) {
            return Mono.empty();
        }

        Map<String, Object> priceData = (Map<String, Object>) response.get(coinId);
        BigDecimal priceUsd = extractBigDecimal(priceData.get("usd"));
        BigDecimal change24h = extractBigDecimal(priceData.get("usd_24h_change"));

        return Mono.just(PriceUpdateEvent.builder()
                .symbol(mapCoinIdToSymbol(coinId))
                .name(coinId)
                .priceUsd(priceUsd)
                .change24h(change24h)
                .timestamp(Instant.now())
                .build());
    }

    private BigDecimal extractBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        return BigDecimal.ZERO;
    }

    /**
     * Map CoinGecko IDs to common token symbols.
     */
    private String mapCoinIdToSymbol(String coinGeckoId) {
        return switch (coinGeckoId.toLowerCase()) {
            case "bitcoin" -> "BTC";
            case "ethereum" -> "ETH";
            case "binancecoin" -> "BNB";
            case "matic-network" -> "MATIC";
            case "solana" -> "SOL";
            case "usd-coin" -> "USDC";
            case "tether" -> "USDT";
            case "avalanche-2" -> "AVAX";
            case "arbitrum" -> "ARB";
            case "optimism" -> "OP";
            default -> coinGeckoId.toUpperCase();
        };
    }
}
