package pl.rynek.shipping.shipments;

import java.util.Arrays;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Statusy przesyłki od przewoźnika. W API polskie etykiety ({@code TrackingEvent.status} z rynek-frontend). {@code rank}
 * porządkuje statusy: webhook ze statusem nie późniejszym niż obecny (spóźniony albo ponowiony) niczego nie zmienia.
 * Paczkomat kończy się „Czeka w paczkomacie”, kurier „Wydana do doręczenia”; oba mają ten sam {@code rank}.
 */
public enum ShipmentStatus {

    CREATED("Etykieta utworzona", 0),
    PICKED_UP("Odebrana od nadawcy", 1),
    IN_TRANSIT("W sortowni", 2),
    OUT_FOR_DELIVERY("Wydana do doręczenia", 3),
    READY_FOR_PICKUP("Czeka w paczkomacie", 3),
    DELIVERED("Doręczona", 4),
    /** Zamówienie anulowane przed odebraniem paczki. Najwyższy rank: późniejsze statusy od przewoźnika są ignorowane. */
    CANCELLED("Anulowana", 5);

    private final String label;
    private final int rank;

    ShipmentStatus(String label, int rank) {
        this.label = label;
        this.rank = rank;
    }

    @JsonValue
    public String label() {
        return label;
    }

    int rank() {
        return rank;
    }

    /** Nazwa stałej z webhooka przewoźnika. */
    public static Optional<ShipmentStatus> parse(String value) {
        return value == null ? Optional.empty() : Arrays.stream(values()).filter(s -> s.name().equalsIgnoreCase(value.trim())).findFirst();
    }
}
