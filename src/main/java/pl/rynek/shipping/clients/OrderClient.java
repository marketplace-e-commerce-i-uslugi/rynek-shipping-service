package pl.rynek.shipping.clients;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Zamówienia z order-service: dane na etykietę przed utworzeniem przesyłki, potem przesyłka i statusy przewoźnika
 * (zamiast zdarzenia {@code shipment.status-changed}).
 */
@Component
public class OrderClient {

    private final RestClient http;

    public OrderClient(@Value("${rynek.order.url}") String orderUrl) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory();
        requests.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().baseUrl(orderUrl).requestFactory(requests).build();
    }

    public Optional<OrderInfo> order(UUID orderId) {
        try {
            return Optional.ofNullable(http.get().uri("/internal/orders/{id}", orderId).retrieve().body(OrderInfo.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Serwis zamówień jest niedostępny. Spróbuj ponownie za chwilę.");
        }
    }

    /** {@code DONE}: przyjęte; {@code REJECTED}: 4xx, ponawianie nic nie da; {@code UNAVAILABLE}: spróbować później. */
    public enum Result { DONE, REJECTED, UNAVAILABLE }

    public Result attachShipment(UUID orderId, UUID shipmentId, String carrier, String trackingNumber) {
        return send(() -> http.put().uri("/internal/orders/{id}/shipment", orderId)
                .body(Map.of("shipmentId", shipmentId, "carrier", carrier, "trackingNumber", trackingNumber)).retrieve().toBodilessEntity());
    }

    /** {@code PICKED_UP} albo {@code DELIVERED}. */
    public Result carrierStatus(UUID orderId, String status) {
        return send(() -> http.post().uri("/internal/orders/{id}/shipment-status", orderId)
                .body(Map.of("status", status)).retrieve().toBodilessEntity());
    }

    private static Result send(Runnable call) {
        try {
            call.run();
            return Result.DONE;
        } catch (HttpClientErrorException e) {
            return Result.REJECTED;
        } catch (RestClientResponseException | ResourceAccessException e) {
            return Result.UNAVAILABLE;
        }
    }

    /** {@code status} to nazwa stałej z order-service, np. {@code TO_SHIP}. */
    public record OrderInfo(UUID id, String number, UUID buyerId, UUID sellerId, String status, Receiver receiver, Delivery shipping) {
    }

    public record Receiver(String fullName, String phone, String street, String postalCode, String city) {
    }

    public record Delivery(String methodId, String pickupPointId, String pickupPoint, UUID shipmentId) {
    }
}
