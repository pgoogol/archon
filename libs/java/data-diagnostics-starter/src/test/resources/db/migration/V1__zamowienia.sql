-- Schemat testowej aplikacji startera: zamówienia i ich pozycje.
create table if not exists orders (
    id     bigserial primary key,
    number varchar(32) not null unique
);

create table if not exists order_item (
    id       bigserial primary key,
    order_id bigint      not null references orders (id),
    sku      varchar(32) not null,
    quantity integer     not null
);

create index if not exists order_item_order_id_idx on order_item (order_id);
