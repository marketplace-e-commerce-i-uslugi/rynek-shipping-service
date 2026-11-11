package pl.rynek.shipping.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Kształt z rynek-frontend: {@code {orderId, carrier}}, gdzie {@code carrier} to „InPost”, „DPD”, „DHL” albo „Poczta Polska”. */
public record CreateShipmentRequest(@NotNull(message = "Podaj zamówienie.") UUID orderId,
                                    @NotBlank(message = "Wybierz przewoźnika.") String carrier) {
}
