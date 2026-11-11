package pl.rynek.shipping.carrier;

import java.security.SecureRandom;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.rynek.shipping.shipments.Carrier;
import pl.rynek.shipping.shipments.Shipment;

/**
 * Symulator przewoźnika bez prawdziwych paczek. Numery przesyłek mają format jak u przewoźnika (InPost 24 cyfry, DPD 14,
 * DHL 10, Poczta Polska „RR…PL”). Statusy przesuwa strona {@code /shipping/sandbox/{id}} albo podpisany webhook.
 */
@Component
@ConditionalOnProperty(name = "rynek.shipping.carrier", havingValue = SandboxCarrier.NAME)
public class SandboxCarrier implements CarrierProvider {

    public static final String NAME = "sandbox";

    private final SecureRandom random = new SecureRandom();
    private final String publicApiUrl;

    public SandboxCarrier(@Value("${rynek.shipping.public-api-url}") String publicApiUrl) {
        this.publicApiUrl = publicApiUrl;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String register(Carrier carrier, Shipment.Receiver receiver, String orderNumber) {
        return switch (carrier) {
            case INPOST -> "6800" + digits(20);
            case DPD -> digits(14);
            case DHL -> digits(10);
            case POCZTA_POLSKA -> "RR" + digits(9) + "PL";
        };
    }

    @Override
    public byte[] label(Shipment shipment) {
        return LabelPdf.render(shipment);
    }

    @Override
    public Optional<String> simulatorUrl(Shipment shipment) {
        return Optional.of(publicApiUrl + "/shipping/sandbox/" + shipment.getId());
    }

    private String digits(int count) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < count; i++) {
            s.append(random.nextInt(10));
        }
        return s.toString();
    }
}
