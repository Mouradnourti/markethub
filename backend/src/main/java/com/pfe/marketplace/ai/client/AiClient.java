package com.pfe.marketplace.ai.client;

import com.pfe.marketplace.ai.dto.AiDtos.AiCopyRequest;
import com.pfe.marketplace.ai.dto.AiDtos.AiPriceHintRequest;
import com.pfe.marketplace.ai.dto.AiDtos.AiSearchRequest;
import com.pfe.marketplace.ai.dto.AiDtos.AiSearchResult;
import com.pfe.marketplace.ai.dto.AiDtos.PriceHintDto;
import com.pfe.marketplace.ai.dto.AiDtos.CopyDraft;
import com.pfe.marketplace.ai.dto.AiDtos.ForecastRequest;
import com.pfe.marketplace.ai.dto.AiDtos.ForecastResult;
import com.pfe.marketplace.ai.dto.AiDtos.RecommendationRequest;
import com.pfe.marketplace.ai.dto.AiDtos.RecommendationResult;
import com.pfe.marketplace.ai.dto.AiDtos.ReturnRiskFeatures;
import com.pfe.marketplace.ai.dto.AiDtos.ReturnRiskResult;
import com.pfe.marketplace.config.AppProperties;
import com.pfe.marketplace.security.InternalApiKeyFilter;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * REST client of the AI microservice. Every call has a short timeout and returns {@link Optional#empty()} on failure:
 * callers always have a non-AI fallback, so the marketplace keeps working when the AI service is down.
 */
@Slf4j
@Component
public class AiClient {

    private final RestClient client;
    private final RestClient writerClient;

    public AiClient(RestClient.Builder builder, AppProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.ai().timeout());
        factory.setReadTimeout(properties.ai().timeout());
        this.client = builder.clone()
                .baseUrl(properties.ai().baseUrl())
                .requestFactory(factory)
                .defaultHeader(InternalApiKeyFilter.HEADER, properties.internalApiKey())
                .build();
        SimpleClientHttpRequestFactory writerFactory = new SimpleClientHttpRequestFactory();
        writerFactory.setConnectTimeout(properties.ai().timeout());
        writerFactory.setReadTimeout(properties.ai().writerTimeoutOrDefault());
        this.writerClient = builder.clone()
                .baseUrl(properties.ai().baseUrl())
                .requestFactory(writerFactory)
                .defaultHeader(InternalApiKeyFilter.HEADER, properties.internalApiKey())
                .build();
    }

    public Optional<ReturnRiskResult> returnRisk(ReturnRiskFeatures features) {
        return call("/return-risk", features, ReturnRiskResult.class);
    }

    public Optional<RecommendationResult> recommendations(RecommendationRequest request) {
        return call("/recommendations", request, RecommendationResult.class);
    }

    public Optional<ForecastResult> forecast(ForecastRequest request) {
        return call("/forecast", request, ForecastResult.class);
    }

    public Optional<CopyDraft> writeCopy(AiCopyRequest request) {
        return call(writerClient, "/copywriter", request, CopyDraft.class);
    }

    public Optional<AiSearchResult> search(AiSearchRequest request) {
        return call("/search", request, AiSearchResult.class);
    }

    /** Empty when the service is down or knows no comparable product. */
    public Optional<PriceHintDto> priceHint(AiPriceHintRequest request) {
        return call("/price-hint", request, PriceHintDto.class);
    }

    private <T> Optional<T> call(String path, Object body, Class<T> type) {
        return call(client, path, body, type);
    }

    private <T> Optional<T> call(RestClient client, String path, Object body, Class<T> type) {
        try {
            return Optional.ofNullable(client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(type));
        } catch (RestClientException e) {
            log.debug("AI service call {} failed: {}", path, e.getMessage());
            return Optional.empty();
        }
    }
}
