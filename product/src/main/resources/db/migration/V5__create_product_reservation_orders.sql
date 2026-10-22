create table product_reservation_order
(
    order_reference varchar(255) primary key,
    status          varchar(32) not null
);

insert into product_reservation_order (order_reference, status)
select order_reference,
       case when bool_or(status = 'RESERVED') then 'RESERVED' else 'RELEASED' end
from product_reservation
group by order_reference;
