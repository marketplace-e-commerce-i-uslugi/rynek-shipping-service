package pl.rynek.shipping.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.shipping.api.dto.CreateShipmentRequest;

import static pl.rynek.shipping.common.NotImplemented.notImplemented;

@RestController
public class ShippingController {

    /**
     * Metody i ceny dostawy dla pozycji. Dostęp: pub.
     */
    @GetMapping("/api/v1/shipping/methods")
    public void listShippingMethods(@RequestParam(required = false) String offerIds, @RequestParam(required = false) String postalCode) {
        throw notImplemented();
    }

    /**
     * Punkty odbioru (paczkomaty). Dostęp: pub.
     */
    @GetMapping("/api/v1/shipping/pickup-points")
    public void listPickupPoints(@RequestParam(required = false) String lat, @RequestParam(required = false) String lng, @RequestParam(required = false) String city) {
        throw notImplemented();
    }

    /**
     * Utworzenie przesyłki i etykiety. Dostęp: seller.
     */
    @PostMapping("/api/v1/shipping/shipments")
    public void createShipment(@RequestBody CreateShipmentRequest request) {
        throw notImplemented();
    }

    /**
     * Etykieta do wydruku. Dostęp: seller.
     */
    @GetMapping("/api/v1/shipping/shipments/{shipmentId}/label")
    public void getLabel(@PathVariable String shipmentId) {
        throw notImplemented();
    }

    /**
     * Śledzenie przesyłki. Dostęp: user / seller.
     */
    @GetMapping("/api/v1/shipping/shipments/{shipmentId}/tracking")
    public void getTracking(@PathVariable String shipmentId) {
        throw notImplemented();
    }

    /**
     * Webhook przewoźnika (zmiana statusu). Dostęp: pub (podpis).
     */
    @PostMapping("/api/v1/shipping/webhooks/{carrier}")
    public void handleCarrierWebhook(@PathVariable String carrier, @RequestBody String payload) {
        throw notImplemented();
    }
}
