package pl.rynek.shipping.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.shipping.api.dto.CarrierWebhook;
import pl.rynek.shipping.api.dto.CreateShipmentRequest;
import pl.rynek.shipping.api.dto.ShipmentResponse;
import pl.rynek.shipping.api.dto.TrackingEventResponse;
import pl.rynek.shipping.carrier.WebhookSignature;
import pl.rynek.shipping.rates.Carriers;
import pl.rynek.shipping.rates.PickupPoint;
import pl.rynek.shipping.rates.ShippingMethod;
import pl.rynek.shipping.shipments.Shipment;
import pl.rynek.shipping.shipments.ShipmentService;
import pl.rynek.shipping.shipments.ShipmentStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RestController
public class ShippingController {

    private final Carriers carriers;
    private final ShipmentService shipments;
    private final WebhookSignature signature;
    private final ObjectMapper json;

    public ShippingController(Carriers carriers, ShipmentService shipments, WebhookSignature signature, ObjectMapper json) {
        this.carriers = carriers;
        this.shipments = shipments;
        this.signature = signature;
        this.json = json;
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
     * Utworzenie przesyłki i etykiety dla zamówienia „Do wysłania”. Dostęp: seller (sprzedawca zamówienia). Numer przesyłki
     * trafia do zamówienia; ponowienie z tym samym przewoźnikiem zwraca tę samą przesyłkę.
     */
    @PostMapping("/api/v1/shipping/shipments")
    @ResponseStatus(HttpStatus.CREATED)
    public ShipmentResponse createShipment(@AuthenticationPrincipal Jwt token, @Valid @RequestBody CreateShipmentRequest request) {
        return view(shipments.create(userId(token), request.orderId(), request.carrier()));
    }

    /**
     * Etykieta do wydruku (PDF). Dostęp: seller.
     */
    @GetMapping("/api/v1/shipping/shipments/{shipmentId}/label")
    public ResponseEntity<byte[]> getLabel(@AuthenticationPrincipal Jwt token, @PathVariable UUID shipmentId) {
        Shipment shipment = shipments.visible(userId(token), shipmentId);
        byte[] pdf = shipments.label(userId(token), shipmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("etykieta-" + shipment.getOrderNumber().replace("#", "") + ".pdf").build().toString())
                .body(pdf);
    }

    /**
     * Śledzenie przesyłki, od najstarszego statusu. Dostęp: user / seller (kupujący i sprzedawca zamówienia).
     */
    @GetMapping("/api/v1/shipping/shipments/{shipmentId}/tracking")
    public List<TrackingEventResponse> getTracking(@AuthenticationPrincipal Jwt token, @PathVariable UUID shipmentId) {
        return shipments.visible(userId(token), shipmentId).getEvents().stream().map(TrackingEventResponse::of).toList();
    }

    /**
     * Webhook przewoźnika (zmiana statusu), podpis HMAC w {@code X-Signature}. Dostęp: pub (podpis). Spóźniony albo
     * ponowiony status niczego nie zmienia.
     */
    @PostMapping("/api/v1/shipping/webhooks/{carrier}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void handleCarrierWebhook(@PathVariable String carrier, @RequestHeader(name = WebhookSignature.HEADER, required = false) String sig,
                                     @RequestBody String payload) {
        if (!carrier.equals(shipments.carrier().name())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nieznany przewoźnik.");
        }
        if (!signature.valid(payload, sig)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nieprawidłowy podpis webhooka.");
        }
        CarrierWebhook event;
        try {
            event = json.readValue(payload, CarrierWebhook.class);
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowa treść webhooka.");
        }
        ShipmentStatus status = ShipmentStatus.parse(event.status())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieznany status przesyłki."));
        if (event.trackingNumber() == null || event.trackingNumber().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brak numeru przesyłki.");
        }
        shipments.carrierUpdate(event.trackingNumber().trim(), status, event.location(), event.at());
    }

    private ShipmentResponse view(Shipment s) {
        return ShipmentResponse.of(s, shipments.carrier().simulatorUrl(s).orElse(null));
    }

    private static UUID userId(Jwt token) {
        return UUID.fromString(token.getSubject());
    }
}
