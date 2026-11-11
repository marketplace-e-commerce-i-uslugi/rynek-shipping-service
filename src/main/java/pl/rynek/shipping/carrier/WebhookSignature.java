package pl.rynek.shipping.carrier;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Podpis webhooka przewoźnika: HMAC-SHA256 z treści żądania, szesnastkowo, w nagłówku {@code X-Signature}. */
@Component
public class WebhookSignature {

    public static final String HEADER = "X-Signature";

    private final byte[] secret;

    public WebhookSignature(@Value("${rynek.shipping.webhook-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Brak HMAC-SHA256", e);
        }
    }

    /** Porównanie w stałym czasie, żeby czas odpowiedzi nie zdradzał poprawnych znaków podpisu. */
    public boolean valid(String payload, String signature) {
        return signature != null && MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.US_ASCII),
                signature.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }
}
