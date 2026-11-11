package pl.rynek.shipping.api;

import java.net.URI;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import pl.rynek.shipping.carrier.SandboxCarrier;
import pl.rynek.shipping.shipments.Shipment;
import pl.rynek.shipping.shipments.ShipmentService;
import pl.rynek.shipping.shipments.ShipmentStatus;

/**
 * Strona symulatora przewoźnika (tylko {@code rynek.shipping.carrier=sandbox}): przycisk przesuwa przesyłkę do następnego
 * statusu tak jak webhook przewoźnika. „Odebrana od nadawcy” zmienia zamówienie na „W drodze”, „Doręczona” na
 * „Zrealizowane”.
 * <p>
 * {@code <meta name="referrer" content="same-origin">}: gateway wysyła {@code Referrer-Policy: no-referrer}, a przy takiej
 * polityce przeglądarka wysyła formularz z {@code Origin: null}, który filtr CORS gatewaya odrzuca (403).
 */
@RestController
@ConditionalOnProperty(name = "rynek.shipping.carrier", havingValue = SandboxCarrier.NAME)
public class SandboxController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Warsaw"));

    private final ShipmentService shipments;

    public SandboxController(ShipmentService shipments) {
        this.shipments = shipments;
    }

    @GetMapping(value = "/api/v1/shipping/sandbox/{shipmentId}", produces = MediaType.TEXT_HTML_VALUE)
    public String page(@PathVariable UUID shipmentId) {
        Shipment s = shipments.byId(shipmentId);
        String history = s.getEvents().stream()
                .map(e -> "<li><b>" + e.status().label() + "</b><span>" + TIME.format(e.at())
                        + (e.location() == null ? "" : " · " + HtmlUtils.htmlEscape(e.location())) + "</span></li>")
                .collect(Collectors.joining());
        String action = s.getStatus() == ShipmentStatus.DELIVERED
                ? "<p>Przesyłka jest doręczona.</p>"
                : "<form method=\"post\"><button class=\"pay\">Następny status: " + ShipmentService.nextStatus(s).label() + "</button></form>";
        return """
               <!doctype html><html lang="pl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
               <meta name="referrer" content="same-origin">
               <title>Symulator przewoźnika</title><style>
               body{font:16px/1.5 system-ui,sans-serif;background:#f4f5f7;color:#1b1d21;margin:0;display:grid;place-items:center;min-height:100vh}
               main{background:#fff;border-radius:16px;padding:24px;max-width:400px;width:calc(100%% - 32px);box-shadow:0 2px 12px #0001}
               h1{font-size:20px;margin:0 0 4px}.note{font-size:13px;color:#5b6170}.num{font-size:18px;font-weight:800;word-break:break-all;margin:8px 0}
               ol{padding-left:20px;margin:12px 0}li span{display:block;font-size:13px;color:#5b6170}
               button{display:block;width:100%%;min-height:48px;border-radius:12px;font:inherit;font-weight:700;margin-top:10px;cursor:pointer}
               .pay{background:#1b1d21;color:#fff;border:0}
               </style></head><body><main><h1>Symulator przewoźnika</h1>
               <p class="note">Środowisko testowe: żadna paczka nie jest nadawana.</p>
               <p>%s · zamówienie %s</p><div class="num">%s</div><p>%s</p><ol>%s</ol>%s</main></body></html>
               """.formatted(s.getCarrier().label(), HtmlUtils.htmlEscape(s.getOrderNumber()), s.getTrackingNumber(),
                HtmlUtils.htmlEscape(s.getReceiverAddress()), history, action);
    }

    @PostMapping("/api/v1/shipping/sandbox/{shipmentId}")
    public ResponseEntity<Void> advance(@PathVariable UUID shipmentId) {
        shipments.sandboxNext(shipmentId);
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, URI.create(shipmentId.toString()).toString()).build();
    }
}
