package com.pfe.marketplace.notification;

import com.pfe.marketplace.common.exception.ApiException;
import com.pfe.marketplace.config.AppProperties;
import com.pfe.marketplace.security.InternalApiKeyFilter;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** REST client of the Laravel notification & invoice service. */
@Component
public class NotificationClient {

    private final RestClient client;

    public NotificationClient(RestClient.Builder builder, AppProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.notification().timeout());
        factory.setReadTimeout(properties.notification().timeout());
        this.client = builder.clone()
                .baseUrl(properties.notification().baseUrl())
                .requestFactory(factory)
                .defaultHeader(InternalApiKeyFilter.HEADER, properties.internalApiKey())
                .build();
    }

    public void sendOrderEvent(String event, OrderDocument.Person recipient, OrderDocument order, Integer parcelIndex) {
        var body = new java.util.HashMap<String, Object>(Map.of("event", event, "recipient", recipient, "order", order));
        if (parcelIndex != null) {
            body.put("parcelIndex", parcelIndex);
        }
        client.post().uri("/api/notifications/order-event").contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toBodilessEntity();
    }

    /** Back-in-stock or price-drop e-mail for a saved product. */
    public void sendProductAlert(String type, OrderDocument.Person recipient, ProductAlert product) {
        client.post().uri("/api/notifications/product-alert").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", type, "recipient", recipient, "product", product))
                .retrieve().toBodilessEntity();
    }

    /** Reminder for a cart left untouched. */
    public void sendCartReminder(OrderDocument.Person recipient, CartReminder cart) {
        client.post().uri("/api/notifications/cart-reminder").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("recipient", recipient, "cart", cart))
                .retrieve().toBodilessEntity();
    }

    public record ProductAlert(String name, String shopName, String currency, java.math.BigDecimal price,
                               java.math.BigDecimal previousPrice, String url) {
    }

    public record CartReminder(String currency, java.math.BigDecimal subtotal, String url,
                               java.util.List<OrderDocument.Line> items) {
    }

    public byte[] renderInvoice(OrderDocument.Person customer, OrderDocument order) {
        try {
            return client.post().uri("/api/invoices").contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_PDF)
                    .body(Map.of("customer", customer, "order", order))
                    .retrieve().body(byte[].class);
        } catch (RestClientException e) {
            throw new ServiceUnavailableException("The invoice service is unavailable, please try again later");
        }
    }

    public static class ServiceUnavailableException extends ApiException {
        public ServiceUnavailableException(String message) {
            super(HttpStatus.SERVICE_UNAVAILABLE, message);
        }
    }
}
