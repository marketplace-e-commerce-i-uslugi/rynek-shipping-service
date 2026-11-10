package pl.rynek.shipping.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.shipping.api.dto.CreateShipmentRequest;
import pl.rynek.shipping.rates.Carriers;
import pl.rynek.shipping.rates.PickupPoint;
import pl.rynek.shipping.rates.ShippingMethod;

import java.util.List;

import static pl.rynek.shipping.common.NotImplemented.notImplemented;

@RestController
public class ShippingController {

    private final Carriers carriers;

    public ShippingController(Carriers carriers) {
        this.carriers = carriers;
    }

    /**
     * Metody i ceny dostawy dla pozycji. Dostęp: pub. Na razie cennik nie zależy od ofert ani kodu pocztowego.
     */
    @GetMapping("/api/v1/shipping/methods")
    public List<ShippingMethod> listShippingMethods(@RequestParam(required = false) String offerIds, @RequestParam(required = false) String postalCode) {
        return carriers.methods();
    }

    /**
     * Punkty odbioru (paczkomaty). Dostęp: pub. Najbliższe 10 od współrzędnych albo w mieście.
     */
    @GetMapping("/api/v1/shipping/pickup-points")
    public List<PickupPoint> listPickupPoints(@RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng, @RequestParam(required = false) String city) {
        return carriers.points(lat, lng, city);
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
