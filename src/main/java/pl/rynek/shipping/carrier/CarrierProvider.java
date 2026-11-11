package pl.rynek.shipping.carrier;

import java.util.Optional;

import pl.rynek.shipping.shipments.Carrier;
import pl.rynek.shipping.shipments.Shipment;

/**
 * Integracja z przewoźnikami (InPost, DPD, DHL, Poczta Polska — każdy ma własne API). Rejestracja przesyłki zwraca numer
 * przesyłki, etykieta to PDF do wydruku, a statusy przychodzą webhookiem {@code /shipping/webhooks/{name}}.
 */
public interface CarrierProvider {

    /** Nazwa w ścieżce webhooka. */
    String name();

    /** Rejestracja przesyłki u przewoźnika; zwraca numer przesyłki. Błąd przewoźnika: wyjątek. */
    String register(Carrier carrier, Shipment.Receiver receiver, String orderNumber);

    /** Etykieta do wydruku (PDF). */
    byte[] label(Shipment shipment);

    /** Strona symulatora przewoźnika (tylko w środowisku testowym), na której można przesunąć status przesyłki. */
    Optional<String> simulatorUrl(Shipment shipment);
}
