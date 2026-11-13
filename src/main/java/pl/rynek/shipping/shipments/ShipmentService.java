package pl.rynek.shipping.shipments;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.shipping.carrier.CarrierProvider;
import pl.rynek.shipping.clients.OrderClient;
import pl.rynek.shipping.clients.OrderClient.OrderInfo;
import pl.rynek.shipping.rates.Carriers;
import pl.rynek.shipping.rates.PickupPoint;

/**
 * Przesyłki: etykieta u przewoźnika dla zamówienia „Do wysłania”, śledzenie i statusy od przewoźnika. order-service
 * dostaje numer przesyłki po utworzeniu etykiety, „W drodze” po odebraniu paczki i „Zrealizowane” po doręczeniu.
 * Gdy order-service nie odpowiada, powiadomienie jest ponawiane ({@link #syncPending()}).
 */
@Service
public class ShipmentService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);
    private static final String SORTING_CENTER = "Sortownia Stryków";

    private final ShipmentRepository shipments;
    private final OrderClient orders;
    private final CarrierProvider carrier;
    private final Carriers rates;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public ShipmentService(ShipmentRepository shipments, OrderClient orders, CarrierProvider carrier, Carriers rates,
                           PlatformTransactionManager transactions, Clock clock) {
        this.shipments = shipments;
        this.orders = orders;
        this.carrier = carrier;
        this.rates = rates;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    public CarrierProvider carrier() {
        return carrier;
    }

    /**
     * Etykieta dla zamówienia sprzedawcy. Ponowienie z tym samym przewoźnikiem zwraca tę samą przesyłkę (podwójne
     * kliknięcie nie tworzy drugiej etykiety); inny przewoźnik daje 409.
     */
    public Shipment create(UUID sellerId, UUID orderId, String carrierName) {
        if (orderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj zamówienie.");
        }
        Carrier chosen = Carrier.parse(carrierName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wybierz przewoźnika: InPost, DPD, DHL albo Poczta Polska."));
        Optional<Shipment> existing = shipments.findByOrderId(orderId).filter(s -> s.getSellerId().equals(sellerId));
        if (existing.isPresent()) {
            return sameCarrier(existing.get(), chosen);
        }
        OrderInfo order = orders.order(orderId).filter(o -> o.sellerId().equals(sellerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleźliśmy tego zamówienia."));
        if (!"TO_SHIP".equals(order.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, switch (order.status()) {
                case "AWAITING_PAYMENT" -> "Zamówienie czeka na płatność. Etykietę utworzysz po zaksięgowaniu wpłaty.";
                case "CANCELLED" -> "Zamówienie zostało anulowane.";
                default -> "Zamówienie jest już wysłane.";
            });
        }
        Shipment.Receiver receiver = receiver(order, chosen);
        String trackingNumber = carrier.register(chosen, receiver, order.number());
        Shipment shipment = new Shipment(orderId, order.number(), sellerId, order.buyerId(), chosen, trackingNumber, receiver, clock.instant());
        try {
            transaction.executeWithoutResult(status -> shipments.saveAndFlush(shipment));
        } catch (DataIntegrityViolationException e) {
            // Równoległe żądanie zapisało już przesyłkę dla tego zamówienia.
            return sameCarrier(shipments.findByOrderId(orderId).orElseThrow(() -> e), chosen);
        }
        return syncOrder(shipment.getId());
    }

    /**
     * Zamówienie anulowane (order-service, zamiast zdarzenia {@code order.cancelled}): anuluje przesyłkę, jeśli paczka
     * nie została jeszcze odebrana. Brak przesyłki albo przesyłka w drodze/doręczona: bez zmian. Idempotentne.
     */
    public Optional<Shipment> orderCancelled(UUID orderId) {
        Optional<Shipment> existing = shipments.findByOrderId(orderId);
        if (existing.isEmpty()) {
            return existing;
        }
        UUID id = existing.get().getId();
        change(id, s -> s.cancel(clock.instant()));
        return Optional.of(byId(id));
    }

    /** Etykieta do wydruku: tylko sprzedawca. */
    public byte[] label(UUID sellerId, UUID shipmentId) {
        Shipment shipment = find(shipmentId, s -> s.getSellerId().equals(sellerId));
        if (shipment.getStatus() == ShipmentStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Przesyłka została anulowana razem z zamówieniem.");
        }
        return carrier.label(shipment);
    }

    /** Śledzenie: kupujący i sprzedawca; dla innych przesyłka nie istnieje. */
    public Shipment visible(UUID userId, UUID shipmentId) {
        return find(shipmentId, s -> s.getSellerId().equals(userId) || s.getBuyerId().equals(userId));
    }

    public Shipment byId(UUID shipmentId) {
        return find(shipmentId, s -> true);
    }

    /**
     * Status od przewoźnika (webhook albo strona symulatora). Spóźniony lub ponowiony status niczego nie zmienia.
     * Kolizja z równoległym webhookiem daje 409, a przewoźnik ponawia webhook.
     */
    public Shipment carrierUpdate(String trackingNumber, ShipmentStatus status, String location, Instant at) {
        Shipment shipment = shipments.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleźliśmy tej przesyłki."));
        boolean changed = change(shipment.getId(), s -> s.advance(status, location, at == null ? clock.instant() : at, clock.instant()));
        return changed ? syncOrder(shipment.getId()) : byId(shipment.getId());
    }

    /** Następny status w kolejności przewoźnika: paczkomat kończy się „Czeka w paczkomacie”, kurier „Wydana do doręczenia”. */
    public static ShipmentStatus nextStatus(Shipment s) {
        return switch (s.getStatus()) {
            case CREATED -> ShipmentStatus.PICKED_UP;
            case PICKED_UP -> ShipmentStatus.IN_TRANSIT;
            case IN_TRANSIT -> s.toPickupPoint() ? ShipmentStatus.READY_FOR_PICKUP : ShipmentStatus.OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY, READY_FOR_PICKUP, DELIVERED -> ShipmentStatus.DELIVERED;
            case CANCELLED -> ShipmentStatus.CANCELLED;
        };
    }

    /** Symulator: przesyłka przechodzi do następnego statusu. Doręczona zostaje bez zmian. */
    public Shipment sandboxNext(UUID shipmentId) {
        Shipment s = byId(shipmentId);
        if (s.getStatus() == ShipmentStatus.DELIVERED || s.getStatus() == ShipmentStatus.CANCELLED) {
            return s;
        }
        ShipmentStatus next = nextStatus(s);
        String location = switch (next) {
            case PICKED_UP -> "Oddział nadawczy";
            case IN_TRANSIT -> SORTING_CENTER;
            default -> s.getReceiverCity();
        };
        return carrierUpdate(s.getTrackingNumber(), next, location, null);
    }

    /** Ponawia powiadomienia order-service, których wcześniej nie przyjął. */
    @Scheduled(fixedDelayString = "${rynek.shipping.order-retry:60s}", initialDelayString = "${rynek.shipping.order-retry:60s}")
    public void syncPending() {
        shipments.findOrderSyncPending().forEach(this::syncOrder);
    }

    /**
     * Najpierw numer przesyłki, potem status (order-service potrzebuje numeru, żeby oznaczyć zamówienie jako wysłane).
     * Odrzucenie 4xx (np. zamówienie anulowane) nie jest ponawiane i trafia do logu.
     */
    private Shipment syncOrder(UUID shipmentId) {
        Shipment s = byId(shipmentId);
        if (!s.isOrderAttached()) {
            OrderClient.Result result = orders.attachShipment(s.getOrderId(), s.getId(), s.getCarrier().label(), s.getTrackingNumber());
            if (result == OrderClient.Result.UNAVAILABLE) {
                log.warn("Przesyłka {}: order-service nie odpowiada, ponowimy przekazanie numeru", shipmentId);
                return s;
            }
            if (result == OrderClient.Result.REJECTED) {
                log.error("Przesyłka {}: order-service odrzucił numer przesyłki dla zamówienia {}", shipmentId, s.getOrderNumber());
            }
            s = changeQuietly(shipmentId, x -> {
                x.orderAttached();
                return x;
            });
        }
        String pending = s.getOrderStatus();
        if (pending != null) {
            OrderClient.Result result = orders.carrierStatus(s.getOrderId(), pending);
            if (result == OrderClient.Result.UNAVAILABLE) {
                log.warn("Przesyłka {}: order-service nie odpowiada, ponowimy status {}", shipmentId, pending);
                return s;
            }
            if (result == OrderClient.Result.REJECTED) {
                log.error("Przesyłka {}: order-service odrzucił status {} zamówienia {}", shipmentId, pending, s.getOrderNumber());
            }
            s = changeQuietly(shipmentId, x -> {
                x.orderStatusSent(pending);
                return x;
            });
        }
        return s;
    }

    private Shipment.Receiver receiver(OrderInfo order, Carrier chosen) {
        OrderClient.Delivery delivery = order.shipping();
        OrderClient.Receiver r = order.receiver();
        String method = delivery == null ? null : delivery.methodId();
        if ("pickup".equals(method)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Kupujący odbiera zamówienie osobiście, więc przesyłka nie jest potrzebna.");
        }
        if (delivery != null && delivery.pickupPointId() != null) {
            if (chosen != Carrier.INPOST) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "Kupujący wybrał paczkomat InPost, więc nadasz przesyłkę tylko przez InPost.");
            }
            PickupPoint point = rates.point(delivery.pickupPointId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Paczkomatu z zamówienia już nie ma."));
            String address = point.address();
            String city = address.contains(", ") ? address.substring(address.lastIndexOf(", ") + 2) : address;
            return new Shipment.Receiver(r.fullName(), r.phone(), point.name() + ", " + address, city, point.id());
        }
        return new Shipment.Receiver(r.fullName(), r.phone(), r.street() + ", " + r.postalCode() + " " + r.city(), r.city(), null);
    }

    private static Shipment sameCarrier(Shipment shipment, Carrier chosen) {
        if (shipment.getCarrier() != chosen) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "To zamówienie ma już przesyłkę " + shipment.getCarrier().label()
                    + " (" + shipment.getTrackingNumber() + ").");
        }
        return shipment;
    }

    private Shipment find(UUID shipmentId, java.util.function.Predicate<Shipment> allowed) {
        return shipments.findById(shipmentId).filter(allowed)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleźliśmy tej przesyłki."));
    }

    private <T> T change(UUID shipmentId, Function<Shipment, T> action) {
        try {
            return transaction.execute(status -> {
                Shipment s = shipments.findById(shipmentId).orElseThrow();
                T result = action.apply(s);
                shipments.saveAndFlush(s);
                return result;
            });
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Przesyłka właśnie się zmieniła. Spróbuj ponownie.");
        }
    }

    /** Zapis znacznika synchronizacji; przy kolizji zostaje na następny przebieg (powiadomienia są idempotentne). */
    private Shipment changeQuietly(UUID shipmentId, Function<Shipment, Shipment> action) {
        try {
            return change(shipmentId, action);
        } catch (ResponseStatusException e) {
            return byId(shipmentId);
        }
    }
}
