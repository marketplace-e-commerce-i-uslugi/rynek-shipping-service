-- Przesyłka do jednego zamówienia: etykieta u przewoźnika i jej numer. Odbiorca jest kopią adresu (albo paczkomatu)
-- z zamówienia z chwili utworzenia etykiety.
CREATE TABLE shipments (
    id               uuid PRIMARY KEY,
    order_id         uuid         NOT NULL UNIQUE,
    order_number     varchar(20)  NOT NULL,
    seller_id        uuid         NOT NULL,
    buyer_id         uuid         NOT NULL,
    carrier          varchar(30)  NOT NULL,
    tracking_number  varchar(40)  NOT NULL UNIQUE,
    status           varchar(30)  NOT NULL,
    receiver_name    varchar(120) NOT NULL,
    receiver_phone   varchar(30),
    receiver_address varchar(250) NOT NULL,
    receiver_city    varchar(80)  NOT NULL,
    pickup_point_id  varchar(20),
    -- false: order-service jeszcze nie zna przesyłki (ponawiane co minutę).
    order_attached   boolean      NOT NULL DEFAULT false,
    -- Status do przekazania order-service (PICKED_UP, DELIVERED); NULL = wszystko przekazane.
    order_status     varchar(20),
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    version          int          NOT NULL DEFAULT 0
);

CREATE INDEX shipments_order_sync_idx ON shipments (updated_at) WHERE NOT order_attached OR order_status IS NOT NULL;

-- Historia statusów od przewoźnika, od najstarszego.
CREATE TABLE tracking_events (
    shipment_id uuid         NOT NULL REFERENCES shipments (id) ON DELETE CASCADE,
    position    int          NOT NULL,
    status      varchar(30)  NOT NULL,
    location    varchar(100),
    at          timestamptz  NOT NULL,
    PRIMARY KEY (shipment_id, position)
);
