package pl.rynek.shipping.rates;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CarriersTests {

    private final Carriers carriers = new Carriers();

    @Test
    void methodsMatchFrontendIds() {
        assertThat(carriers.methods()).extracting(ShippingMethod::id).containsExactly("inpost", "courier", "pickup");
        assertThat(carriers.method("inpost")).get().extracting(ShippingMethod::needsPickupPoint).isEqualTo(true);
        assertThat(carriers.method("nie-ma")).isEmpty();
    }

    @Test
    void pointsInCityAreSortedByDistance() {
        List<PickupPoint> krakow = carriers.points(null, null, " kraków ");
        assertThat(krakow).extracting(PickupPoint::id).containsExactly("KRA01M", "KRA02M");
        assertThat(krakow.getFirst().distanceKm()).isZero();
        assertThat(krakow.get(1).distanceKm()).isPositive();
    }

    @Test
    void coordinatesWinOverCityAndUnknownCityFallsBackToWarsaw() {
        assertThat(carriers.points(51.11, 16.99, "Kraków").getFirst().id()).isEqualTo("WRO01M");
        assertThat(carriers.points(null, null, "Radom")).allMatch(p -> p.address().endsWith("Warszawa"));
        assertThat(carriers.point("GDA01M")).get().extracting(PickupPoint::address).isEqualTo("ul. Grunwaldzka 141, Gdańsk");
    }
}
