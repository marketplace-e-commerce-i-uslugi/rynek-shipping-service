# rynek-shipping-service

`shipping-service` — dostawa

> Status: **cennik, punkty odbioru, przesyłki, etykiety, śledzenie i webhooki działają** z przewoźnikiem `sandbox` (symulator bez prawdziwych paczek, PostgreSQL). Metody: Paczkomat InPost 12,99 zł, Kurier 15,99 zł, Odbiór osobisty 0 zł, cena za paczkę od jednego sprzedawcy. Punkty odbioru to stała lista przykładowych paczkomatów w kilku miastach (`rates/Carriers`). Integracje z API InPost, DPD, DHL i Poczty Polskiej za interfejsem `CarrierProvider` to osobna decyzja.

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
| GET | `/internal/shipping/methods/{methodId}` | Metoda i cena (koszyk, zamówienie) | int |
| GET | `/internal/shipping/pickup-points/{pointId}` | Dane punktu do zapisania w zamówieniu | int |
| POST | `/api/v1/shipping/shipments` | Utworzenie przesyłki i etykiety | seller |
| GET | `/api/v1/shipping/shipments/{shipmentId}/label` | Etykieta do wydruku | seller |
| GET | `/api/v1/shipping/shipments/{shipmentId}/tracking` | Śledzenie przesyłki | user / seller |
| POST | `/api/v1/shipping/webhooks/{carrier}` | Webhook przewoźnika (zmiana statusu) | pub (podpis) |
| GET, POST | `/api/v1/shipping/sandbox/{shipmentId}` | Strona symulatora przewoźnika: przesuwa przesyłkę do następnego statusu (tylko `carrier=sandbox`) | pub |

### Zasady

- `POST /shipments {orderId, carrier}`: `carrier` to „InPost”, „DPD”, „DHL” albo „Poczta Polska”. Etykietę tworzy sprzedawca zamówienia (inny dostaje 404), tylko dla zamówienia „Do wysłania” (409 dla nieopłaconego, anulowanego i wysłanego). Paczkomat: tylko InPost (422). Odbiór osobisty nie ma przesyłki (422). Odbiorca (adres albo paczkomat) jest kopią z zamówienia.
- Jedna przesyłka na zamówienie. Ponowienie z tym samym przewoźnikiem zwraca tę samą przesyłkę, a inny przewoźnik daje 409. Odpowiedź: `{id, carrier, trackingNumber, orderId, status, createdAt, simulatorUrl}`.
- Po utworzeniu numer przesyłki trafia do zamówienia (`PUT /internal/orders/{id}/shipment` w order-service), a status zamówienia się nie zmienia.
- `GET /shipments/{id}/label`: PDF A6 (tylko sprzedawca), `etykieta-<numer zamówienia>.pdf`. Etykieta symulatora jest bez polskich znaków, bo standardowa czcionka PDF ich nie ma, i bez kodu kreskowego. Prawdziwy przewoźnik zwraca własną etykietę.
- `GET /shipments/{id}/tracking`: statusy od najstarszego, dla kupującego i sprzedawcy (`{at, status, location}`). Statusy: Etykieta utworzona → Odebrana od nadawcy → W sortowni → Wydana do doręczenia (kurier) albo Czeka w paczkomacie (paczkomat) → Doręczona.
- Webhook `POST /webhooks/{carrier}` z `X-Signature` (HMAC-SHA256 treści, sekret `SHIPPING_WEBHOOK_SECRET`), treść `{trackingNumber, status, location?, at?}`, gdzie `status` to nazwa stałej (`PICKED_UP`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`, `READY_FOR_PICKUP`, `DELIVERED`). Status nie późniejszy niż obecny (spóźniony albo ponowiony) niczego nie zmienia.
- Zamiast zdarzenia `shipment.status-changed` shipping woła order-service (`POST /internal/orders/{id}/shipment-status`). Pierwszy status po odebraniu paczki daje `PICKED_UP` („W drodze”), a doręczenie `DELIVERED` („Zrealizowane”, środki sprzedawcy stają się dostępne). Gdy order-service nie odpowiada, numer i statusy są ponawiane co `rynek.shipping.order-retry` (60 s), zawsze najpierw numer. Odrzucenie 4xx (np. zamówienie anulowane) nie jest ponawiane i trafia do logu.
- Symulator: numery w formacie przewoźnika (InPost 24 cyfry, DPD 14, DHL 10, Poczta Polska `RR…PL`). Strona `/shipping/sandbox/{id}` ma przycisk „Następny status”. Frontend pokazuje do niej link przy śledzeniu, ale tylko poza produkcją.
- Anulowanie zamówienia z utworzoną etykietą nie anuluje przesyłki u przewoźnika. Przy prawdziwym przewoźniku to będzie do dodania.

## Zdarzenia Kafka

Wiersze z §17, w których występuje `shipping` (publikuje lub słucha).

| Zdarzenie | Publikuje | Słuchają |
|---|---|---|
| `shipment.status-changed` | shipping | order, notification |

## Dane i zależności

- **Magazyn danych:** PostgreSQL (baza `shipping`, Flyway)
- **Zależności:** przewoźnicy za interfejsem `CarrierProvider` (na razie `SandboxCarrier`), order-service (`GET /internal/orders/{id}`, `PUT /internal/orders/{id}/shipment`, `POST /internal/orders/{id}/shipment-status`); JWT z JWKS identity-service
- **Kontrakty:** `rynek-contracts/openapi/` i `rynek-contracts/events/`
- **Lokalne uruchomienie:** `rynek-infra/local/`

## Kolejność budowy

Etap z §19: 5. `cart-service`, `order-service`, `payment-service`, `shipping-service` — sprzedaż produktów.
