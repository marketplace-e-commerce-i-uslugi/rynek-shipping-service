package pl.rynek.shipping.rates;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Cennik i punkty odbioru bez integracji z przewoźnikiem. Cena jest za paczkę od jednego sprzedawcy, niezależnie od
 * pozycji i kodu pocztowego. Punkty to stała lista przykładowych paczkomatów w kilku miastach; zastąpi ją
 * {@code CarrierProvider} z API InPost (§ README).
 */
@Component
public class Carriers {

    public static final String INPOST = "inpost";

    private static final List<ShippingMethod> METHODS = List.of(
            new ShippingMethod(INPOST, "Paczkomat InPost", ShippingMethod.Money.pln(1299), "1–2 dni robocze", true),
            new ShippingMethod("courier", "Kurier", ShippingMethod.Money.pln(1599), "1–2 dni robocze", false),
            new ShippingMethod("pickup", "Odbiór osobisty", ShippingMethod.Money.pln(0), "po umówieniu ze sprzedawcą", false));

    private static final List<Point> POINTS = List.of(
            new Point("WAW01M", "Warszawa", "ul. Marszałkowska 104", 52.2319, 21.0067),
            new Point("WAW02M", "Warszawa", "ul. Puławska 145", 52.1937, 21.0215),
            new Point("WAW03M", "Warszawa", "ul. Grochowska 207", 52.2468, 21.0812),
            new Point("WAW04M", "Warszawa", "al. Jana Pawła II 82", 52.2533, 20.9850),
            new Point("KRA01M", "Kraków", "ul. Karmelicka 37", 50.0668, 19.9295),
            new Point("KRA02M", "Kraków", "ul. Wielicka 28", 50.0399, 19.9677),
            new Point("WRO01M", "Wrocław", "ul. Legnicka 58", 51.1170, 16.9900),
            new Point("WRO02M", "Wrocław", "ul. Grabiszyńska 184", 51.0911, 16.9910),
            new Point("POZ01M", "Poznań", "ul. Głogowska 92", 52.3953, 16.9007),
            new Point("GDA01M", "Gdańsk", "ul. Grunwaldzka 141", 54.3846, 18.6011));

    public List<ShippingMethod> methods() {
        return METHODS;
    }

    public Optional<ShippingMethod> method(String id) {
        return METHODS.stream().filter(m -> m.id().equals(id)).findFirst();
    }

    public Optional<PickupPoint> point(String id) {
        return POINTS.stream().filter(p -> p.id().equals(id)).findFirst().map(p -> p.view(0));
    }

    /**
     * Najbliższe punkty: od podanych współrzędnych albo od pierwszego punktu w mieście. Bez współrzędnych i bez
     * znanego miasta zwraca punkty z Warszawy.
     */
    public List<PickupPoint> points(Double lat, Double lng, String city) {
        String wanted = city == null || city.isBlank() ? null : city.trim().toLowerCase(Locale.ROOT);
        List<Point> inCity = wanted == null ? List.of()
                : POINTS.stream().filter(p -> p.city().toLowerCase(Locale.ROOT).equals(wanted)).toList();
        Point origin = lat != null && lng != null ? new Point("", "", "", lat, lng)
                : !inCity.isEmpty() ? inCity.getFirst() : POINTS.getFirst();
        List<Point> candidates = lat != null && lng != null ? POINTS : !inCity.isEmpty() ? inCity
                : POINTS.stream().filter(p -> p.city().equals("Warszawa")).toList();
        return candidates.stream()
                .map(p -> p.view(distanceKm(origin, p)))
                .sorted(Comparator.comparingDouble(PickupPoint::distanceKm))
                .limit(10)
                .toList();
    }

    private static double distanceKm(Point a, Point b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat())) * Math.pow(Math.sin(dLng / 2), 2);
        return Math.round(2 * 6371 * Math.asin(Math.sqrt(h)) * 10) / 10.0;
    }

    private record Point(String id, String city, String street, double lat, double lng) {

        PickupPoint view(double distanceKm) {
            return new PickupPoint(id, "Paczkomat " + id, street + ", " + city, distanceKm);
        }
    }
}
