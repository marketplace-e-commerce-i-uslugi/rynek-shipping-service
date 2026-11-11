package pl.rynek.shipping.shipments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    Optional<Shipment> findByOrderId(UUID orderId);

    Optional<Shipment> findByTrackingNumber(String trackingNumber);

    /** Przesyłki, o których order-service jeszcze nie wie wszystkiego (nie odpowiadał). */
    @Query("select s.id from Shipment s where s.orderAttached = false or s.orderStatus is not null order by s.updatedAt")
    List<UUID> findOrderSyncPending();
}
