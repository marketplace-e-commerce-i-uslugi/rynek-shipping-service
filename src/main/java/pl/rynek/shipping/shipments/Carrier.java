package pl.rynek.shipping.shipments;

import java.util.Arrays;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonValue;

/** Przewoźnicy, których sprzedawca wybiera przy etykiecie. W API nazwy jak w rynek-frontend („InPost”, „Poczta Polska”). */
public enum Carrier {

    INPOST("InPost"),
    DPD("DPD"),
    DHL("DHL"),
    POCZTA_POLSKA("Poczta Polska");

    private final String label;

    Carrier(String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }

    /** Przyjmuje nazwę z frontendu albo nazwę stałej, bez względu na wielkość liter. */
    public static Optional<Carrier> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String v = value.trim();
        return Arrays.stream(values()).filter(c -> c.label.equalsIgnoreCase(v) || c.name().equalsIgnoreCase(v)).findFirst();
    }
}
