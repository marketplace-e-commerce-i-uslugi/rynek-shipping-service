package pl.rynek.shipping.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.shipping.rates.Carriers;
import pl.rynek.shipping.rates.PickupPoint;
import pl.rynek.shipping.rates.ShippingMethod;
import pl.rynek.shipping.shipments.ShipmentService;

/** Dla koszyka i zamówień: cena wybranej metody i dane punktu odbioru do zapisania w zamówieniu. Dostęp: int. */
@RestController
public class InternalShippingController {

    private final Carriers carriers;
    private final ShipmentService shipments;

    public InternalShippingController(Carriers carriers, ShipmentService shipments) {
        this.carriers = carriers;
        this.shipments = shipments;
    }

    @GetMapping("/internal/shipping/methods/{methodId}")
    public ShippingMethod getMethod(@PathVariable String methodId) {
        return carriers.method(methodId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie ma takiego sposobu dostawy."));
    }

    /**
     * Zamówienie anulowane (order-service): przesyłka z etykietą, której nikt jeszcze nie odebrał, zostaje anulowana.
     * Idempotentne; brak przesyłki albo przesyłka w drodze/doręczona to też 204 (bez zmian). 503 = ponów. Dostęp: int.
     */
    @PostMapping("/internal/orders/{orderId}/cancelled")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void orderCancelled(@PathVariable UUID orderId) {
        shipments.orderCancelled(orderId);
    }

    @GetMapping("/internal/shipping/pickup-points/{pointId}")
    public PickupPoint getPickupPoint(@PathVariable String pointId) {
        return carriers.point(pointId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie ma takiego punktu odbioru."));
    }
}
