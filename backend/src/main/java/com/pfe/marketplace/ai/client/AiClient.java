package com.pfe.marketplace.ai.client;

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

    public AiClient(RestClient.Builder builder, AppProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.ai().timeout());
        factory.setReadTimeout(properties.ai().timeout());
        this.client = builder.clone()
                .baseUrl(properties.ai().baseUrl())
                .requestFactory(factory)
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

    private <T> Optional<T> call(String path, Object body, Class<T> type) {
        try {
            return Optional.ofNullable(client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(type));
        } catch (RestClientException e) {
            log.debug("AI service call {} failed: {}", path, e.getMessage());
            return Optional.empty();
        }
    }
}
