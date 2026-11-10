package pl.rynek.shipping.rates;

/** Kształt {@code ShippingMethod} z rynek-frontend (commerce.ts). Cena w groszach za jedną paczkę od sprzedawcy. */
public record ShippingMethod(String id, String name, Money price, String eta, boolean needsPickupPoint) {

    public record Money(long amount, String currency) {

        static Money pln(long amount) {
            return new Money(amount, "PLN");
        }
    }
}
