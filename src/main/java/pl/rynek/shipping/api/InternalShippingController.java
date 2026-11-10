package pl.rynek.shipping.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.shipping.rates.Carriers;
import pl.rynek.shipping.rates.PickupPoint;
import pl.rynek.shipping.rates.ShippingMethod;

/** Dla koszyka i zamówień: cena wybranej metody i dane punktu odbioru do zapisania w zamówieniu. Dostęp: int. */
@RestController
public class InternalShippingController {

    private final Carriers carriers;

    public InternalShippingController(Carriers carriers) {
        this.carriers = carriers;
    }

    @GetMapping("/internal/shipping/methods/{methodId}")
    public ShippingMethod getMethod(@PathVariable String methodId) {
        return carriers.method(methodId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie ma takiego sposobu dostawy."));
    }

    @GetMapping("/internal/shipping/pickup-points/{pointId}")
    public PickupPoint getPickupPoint(@PathVariable String pointId) {
        return carriers.point(pointId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie ma takiego punktu odbioru."));
    }
}
