# rynek-shipping-service

`shipping-service` — dostawa

> Status: **szkielet**. Wszystkie endpointy z tabeli istnieją w kontrolerach (`src/main/java/.../api/`) i zwracają `501 Not Implemented` (RFC 7807). Ciała żądań to puste rekordy w `api/dto/`.

## Uruchomienie

```bash
./mvnw spring-boot:run   # port 8094, zmienna PORT
./mvnw verify            # testy
```

- Swagger UI: http://localhost:8094/swagger-ui.html
- Zdrowie: http://localhost:8094/actuator/health

## Zakres i endpointy

_Skopiowane z `rynek-docs/architektura/rynek-endpointy-mikroserwisy.md`. Od teraz to repozytorium jest właścicielem tej specyfikacji._

Ekrany: 4 Karta oferty (koszt dostawy), 8 Koszyk. Integracje z przewoźnikami (np. InPost, kurierzy) za interfejsem `CarrierProvider`.

| Metoda | Ścieżka | Opis | Dostęp |
|---|---|---|---|
| GET | `/api/v1/shipping/methods?offerIds=&postalCode=` | Metody i ceny dostawy dla pozycji | pub |
| GET | `/api/v1/shipping/pickup-points?lat=&lng=&city=` | Punkty odbioru (paczkomaty) | pub |
| POST | `/api/v1/shipping/shipments` | Utworzenie przesyłki i etykiety | seller |
| GET | `/api/v1/shipping/shipments/{shipmentId}/label` | Etykieta do wydruku | seller |
| GET | `/api/v1/shipping/shipments/{shipmentId}/tracking` | Śledzenie przesyłki | user / seller |
| POST | `/api/v1/shipping/webhooks/{carrier}` | Webhook przewoźnika (zmiana statusu) | pub (podpis) |

## Zdarzenia Kafka

Wiersze z §17, w których występuje `shipping` (publikuje lub słucha).

| Zdarzenie | Publikuje | Słuchają |
|---|---|---|
| `shipment.status-changed` | shipping | order, notification |

## Dane i zależności

- **Magazyn danych:** PostgreSQL
- **Zależności:** przewoźnicy za interfejsem CarrierProvider (np. InPost)
- **Kontrakty:** `rynek-contracts/openapi/` i `rynek-contracts/events/`
- **Lokalne uruchomienie:** `rynek-infra/local/`

## Kolejność budowy

Etap z §19: 5. `cart-service`, `order-service`, `payment-service`, `shipping-service` — sprzedaż produktów.
