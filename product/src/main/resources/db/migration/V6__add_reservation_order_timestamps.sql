alter table product_reservation_order
    add column created_at timestamp with time zone not null default now(),
    add column released_at timestamp with time zone,
    add column confirmed_at timestamp with time zone;

update product_reservation_order
set released_at = now()
where status = 'RELEASED';

create index idx_product_reservation_order_status_created
    on product_reservation_order (status, created_at);
create index idx_product_reservation_order_status_released
    on product_reservation_order (status, released_at);
