package pl.rynek.shipping.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.rynek.shipping.TestcontainersConfig;
import pl.rynek.shipping.carrier.WebhookSignature;
import pl.rynek.shipping.clients.OrderClient;
import pl.rynek.shipping.clients.OrderClient.Delivery;
import pl.rynek.shipping.clients.OrderClient.OrderInfo;
import pl.rynek.shipping.clients.OrderClient.Receiver;
import pl.rynek.shipping.shipments.ShipmentRepository;
import pl.rynek.shipping.shipments.ShipmentService;

/**
 * Przesyłki na prawdziwym PostgreSQL z przewoźnikiem „sandbox”. Atrapa order-service trzyma zamówienia w mapie i może
 * udawać, że nie odpowiada.
 */
@SpringBootTest(properties = "rynek.shipping.order-retry=1h")
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ShipmentFlowTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    WebhookSignature signature;

    @Autowired
    ShipmentService service;

    @Autowired
    ShipmentRepository repository;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    OrderClient orders;

    final Map<UUID, OrderInfo> known = new ConcurrentHashMap<>();
    volatile OrderClient.Result orderResult = OrderClient.Result.DONE;

    @BeforeEach
    void stubs() {
        given(jwtDecoder.decode(anyString())).willAnswer(call -> {
            String token = call.getArgument(0);
            if (!token.startsWith("user-")) {
                throw new BadJwtException("Nieważny token");
            }
            return Jwt.withTokenValue(token).header("alg", "RS256").subject(token.substring(5))
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build();
        });
        given(orders.order(any())).willAnswer(call -> Optional.ofNullable(known.get(call.<UUID>getArgument(0))));
        given(orders.attachShipment(any(), any(), anyString(), anyString())).willAnswer(call -> orderResult);
        given(orders.carrierStatus(any(), anyString())).willAnswer(call -> orderResult);
    }

    @Test
    void courierShipmentFromLabelToDelivery() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID order = order(buyer, seller, "TO_SHIP", "courier", null);
        String body = create(seller, order, "DPD")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.carrier").value("DPD"))
                .andExpect(jsonPath("$.status").value("Etykieta utworzona"))
                .andExpect(jsonPath("$.simulatorUrl", containsString("/api/v1/shipping/sandbox/")))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        String number = JsonPath.read(body, "$.trackingNumber");
        assertThat(number).matches("\\d{14}");
        verify(orders).attachShipment(order, UUID.fromString(id), "DPD", number);

        // Podwójne kliknięcie: ta sama przesyłka; inny przewoźnik: 409.
        create(seller, order, "dpd").andExpect(jsonPath("$.id").value(id));
        create(seller, order, "DHL").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("To zamówienie ma już przesyłkę DPD (" + number + ")."));

        tracking(buyer, id).andExpect(status().isOk()).andExpect(jsonPath("$[*].status", contains("Etykieta utworzona")));
        tracking(UUID.randomUUID(), id).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/shipping/shipments/" + id + "/label").header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isNotFound());
        byte[] pdf = mvc.perform(get("/api/v1/shipping/shipments/" + id + "/label").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("etykieta-1001.pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        String text = new String(pdf, java.nio.charset.StandardCharsets.US_ASCII);
        assertThat(text).startsWith("%PDF-1.4").endsWith("%%EOF\n").contains(number).contains("Anna Nowak").contains("Lodz");

        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/v1/shipping/sandbox/" + id)).andExpect(status().isSeeOther());
        }
        tracking(seller, id).andExpect(jsonPath("$[*].status",
                contains("Etykieta utworzona", "Odebrana od nadawcy", "W sortowni", "Wydana do doręczenia", "Doręczona")))
                .andExpect(jsonPath("$[2].location").value("Sortownia Stryków"))
                .andExpect(jsonPath("$[4].location").value("Łódź"));
        InOrder sent = inOrder(orders);
        sent.verify(orders).carrierStatus(order, "PICKED_UP");
        sent.verify(orders).carrierStatus(order, "DELIVERED");
        verify(orders, times(2)).carrierStatus(eq(order), anyString());
        mvc.perform(get("/api/v1/shipping/sandbox/" + id)).andExpect(content().string(containsString("Przesyłka jest doręczona.")));
    }

    @Test
    void pickupPointShipmentGoesOnlyThroughInPost() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID order = order(buyer, seller, "TO_SHIP", "inpost", "WAW02M");
        create(seller, order, "DPD").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Kupujący wybrał paczkomat InPost, więc nadasz przesyłkę tylko przez InPost."));
        String body = create(seller, order, "InPost").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        assertThat(JsonPath.<String>read(body, "$.trackingNumber")).matches("6800\\d{20}");
        for (int i = 0; i < 3; i++) {
            service.sandboxNext(UUID.fromString(id));
        }
        tracking(buyer, id).andExpect(jsonPath("$[3].status").value("Czeka w paczkomacie"))
                .andExpect(jsonPath("$[3].location").value("Warszawa"));
        assertThat(repository.findById(UUID.fromString(id)).orElseThrow().getReceiverAddress()).startsWith("Paczkomat WAW02M, ul. Puławska 145");
    }

    @Test
    void onlySellerOfOrderToShipCreatesShipment() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        create(seller, order(buyer, seller, "AWAITING_PAYMENT", "courier", null), "DPD").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Zamówienie czeka na płatność. Etykietę utworzysz po zaksięgowaniu wpłaty."));
        create(seller, order(buyer, seller, "CANCELLED", "courier", null), "DPD").andExpect(status().isConflict());
        create(UUID.randomUUID(), order(buyer, seller, "TO_SHIP", "courier", null), "DPD").andExpect(status().isNotFound());
        create(seller, order(buyer, seller, "TO_SHIP", "pickup", null), "DPD").andExpect(status().isUnprocessableContent());
        create(seller, order(buyer, seller, "TO_SHIP", "courier", null), "UPS").andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/shipping/shipments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verify(orders, never()).attachShipment(any(), any(), anyString(), anyString());
    }

    @Test
    void signedWebhookAdvancesStatusAndIgnoresLateOnes() throws Exception {
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        UUID order = order(buyer, seller, "TO_SHIP", "courier", null);
        String body = create(seller, order, "DHL").andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        String number = JsonPath.read(body, "$.trackingNumber");

        String transit = "{\"trackingNumber\":\"" + number + "\",\"status\":\"IN_TRANSIT\",\"location\":\"Sortownia Wrocław\"}";
        webhook(transit, "zly-podpis").andExpect(status().isUnauthorized());
        webhook(transit, signature.sign(transit)).andExpect(status().isNoContent());
        // Spóźnione „odebrana” po „w sortowni”: bez zmian.
        String late = "{\"trackingNumber\":\"" + number + "\",\"status\":\"PICKED_UP\"}";
        webhook(late, signature.sign(late)).andExpect(status().isNoContent());
        String unknown = "{\"trackingNumber\":\"" + number + "\",\"status\":\"LOST\"}";
        webhook(unknown, signature.sign(unknown)).andExpect(status().isBadRequest());
        tracking(seller, id).andExpect(jsonPath("$", hasSize(2))).andExpect(jsonPath("$[1].location").value("Sortownia Wrocław"));
        // Pominięte „odebrana”: zamówienie i tak przechodzi na „W drodze”.
        verify(orders).carrierStatus(order, "PICKED_UP");
    }

    @Test
    void orderServiceIsNotifiedLaterWhenItWasDown() throws Exception {
        UUID seller = UUID.randomUUID();
        UUID order = order(UUID.randomUUID(), seller, "TO_SHIP", "courier", null);
        orderResult = OrderClient.Result.UNAVAILABLE;
        String id = JsonPath.read(create(seller, order, "Poczta Polska").andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNumber", org.hamcrest.Matchers.matchesPattern("RR\\d{9}PL")))
                .andReturn().getResponse().getContentAsString(), "$.id");
        service.sandboxNext(UUID.fromString(id));
        var pending = repository.findById(UUID.fromString(id)).orElseThrow();
        assertThat(pending.isOrderAttached()).isFalse();
        assertThat(pending.getOrderStatus()).isEqualTo("PICKED_UP");
        verify(orders, never()).carrierStatus(any(), anyString());

        orderResult = OrderClient.Result.DONE;
        service.syncPending();
        var synced = repository.findById(UUID.fromString(id)).orElseThrow();
        assertThat(synced.isOrderAttached()).isTrue();
        assertThat(synced.getOrderStatus()).isNull();
        InOrder sent = inOrder(orders);
        sent.verify(orders, times(3)).attachShipment(eq(order), any(), eq("Poczta Polska"), anyString());
        sent.verify(orders).carrierStatus(order, "PICKED_UP");
    }

    private UUID order(UUID buyer, UUID seller, String status, String method, String pointId) {
        UUID id = UUID.randomUUID();
        known.put(id, new OrderInfo(id, "#1001", buyer, seller, status,
                new Receiver("Anna Nowak", "+48600100200", "ul. Piotrkowska 12", "90-001", "Łódź"),
                new Delivery(method, pointId, pointId == null ? null : "Paczkomat " + pointId, null)));
        return id;
    }

    private ResultActions create(UUID seller, UUID orderId, String carrier) throws Exception {
        return mvc.perform(post("/api/v1/shipping/shipments").header(HttpHeaders.AUTHORIZATION, bearer(seller))
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":\"" + orderId + "\",\"carrier\":\"" + carrier + "\"}"));
    }

    private ResultActions tracking(UUID user, String shipmentId) throws Exception {
        return mvc.perform(get("/api/v1/shipping/shipments/" + shipmentId + "/tracking").header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions webhook(String payload, String sig) throws Exception {
        return mvc.perform(post("/api/v1/shipping/webhooks/sandbox").header(WebhookSignature.HEADER, sig)
                .contentType(MediaType.APPLICATION_JSON).content(payload));
    }

    private static String bearer(UUID userId) {
        return "Bearer user-" + userId;
    }
}
