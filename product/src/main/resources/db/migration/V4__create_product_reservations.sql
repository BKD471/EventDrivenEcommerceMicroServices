create table product_reservation
(
    id              bigserial primary key,
    order_reference varchar(255)     not null,
    product_id      integer          not null,
    quantity        double precision not null,
    name            varchar(255)     not null,
    description     varchar(255),
    price           numeric(38, 2)   not null,
    status          varchar(32)      not null,
    constraint uk_product_reservation_order_product unique (order_reference, product_id)
);
