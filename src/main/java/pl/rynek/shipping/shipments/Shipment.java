package pl.rynek.shipping.shipments;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

/**
 * Przesyłka do jednego zamówienia. {@code @Version}: dwa webhooki przewoźnika naraz nie zgubią statusu. Zmiany ważne
 * dla zamówienia (odebrana, doręczona) czekają w {@code orderStatus}, aż order-service je przyjmie.
 */
@Entity
@Table(name = "shipments")
public class Shipment {

    @Id
    private UUID id;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "order_number")
    private String orderNumber;

    @Column(name = "seller_id")
    private UUID sellerId;

    @Column(name = "buyer_id")
    private UUID buyerId;

    @Enumerated(EnumType.STRING)
    private Carrier carrier;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    private ShipmentStatus status;

    @Column(name = "receiver_name")
    private String receiverName;

    @Column(name = "receiver_phone")
    private String receiverPhone;

    @Column(name = "receiver_address")
    private String receiverAddress;

    @Column(name = "receiver_city")
    private String receiverCity;

    @Column(name = "pickup_point_id")
    private String pickupPointId;

    @Column(name = "order_attached")
    private boolean orderAttached;

    @Column(name = "order_status")
    private String orderStatus;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private int version;

    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "tracking_events", joinColumns = @JoinColumn(name = "shipment_id"))
    @OrderColumn(name = "position")
    private List<TrackingEvent> events = new ArrayList<>();

    protected Shipment() {
    }

    Shipment(UUID orderId, String orderNumber, UUID sellerId, UUID buyerId, Carrier carrier, String trackingNumber, Receiver receiver,
             Instant now) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.sellerId = sellerId;
        this.buyerId = buyerId;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.receiverName = receiver.name();
        this.receiverPhone = receiver.phone();
        this.receiverAddress = receiver.address();
        this.receiverCity = receiver.city();
        this.pickupPointId = receiver.pickupPointId();
        this.status = ShipmentStatus.CREATED;
        this.createdAt = now;
        this.updatedAt = now;
        this.events.add(new TrackingEvent(ShipmentStatus.CREATED, null, now));
    }

    /**
     * Nowy status od przewoźnika. Status nie późniejszy niż obecny jest pomijany (zwraca {@code false}). Pierwszy status
     * po odebraniu paczki i doręczenie trafiają do kolejki dla order-service.
     */
    boolean advance(ShipmentStatus next, String location, Instant at, Instant now) {
        if (next.rank() <= status.rank()) {
            return false;
        }
        if (next == ShipmentStatus.DELIVERED) {
            orderStatus = "DELIVERED";
        } else if (status.rank() < ShipmentStatus.PICKED_UP.rank()) {
            orderStatus = "PICKED_UP";
        }
        status = next;
        updatedAt = now;
        events.add(new TrackingEvent(next, location, at));
        return true;
    }

    void orderAttached() {
        orderAttached = true;
    }

    /** order-service przyjął status; jeśli w międzyczasie przyszedł późniejszy, ten czeka dalej. */
    void orderStatusSent(String sent) {
        if (sent.equals(orderStatus)) {
            orderStatus = null;
        }
    }

    /** Odbiór w paczkomacie: kończy się „Czeka w paczkomacie”, a nie „Wydana do doręczenia”. */
    public boolean toPickupPoint() {
        return pickupPointId != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public UUID getBuyerId() {
        return buyerId;
    }

    public Carrier getCarrier() {
        return carrier;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public String getReceiverAddress() {
        return receiverAddress;
    }

    public String getReceiverCity() {
        return receiverCity;
    }

    public String getPickupPointId() {
        return pickupPointId;
    }

    public boolean isOrderAttached() {
        return orderAttached;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<TrackingEvent> getEvents() {
        return List.copyOf(events);
    }

    @Embeddable
    public record TrackingEvent(@Enumerated(EnumType.STRING) ShipmentStatus status, String location, Instant at) {
    }

    /** Odbiorca z zamówienia: adres albo paczkomat ({@code pickupPointId}). */
    public record Receiver(String name, String phone, String address, String city, String pickupPointId) {
    }
}
