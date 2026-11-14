-- Produkty testowej aplikacji: encja trzymana w cache drugiego poziomu.
create table if not exists product (
    id   bigint primary key,
    name varchar(64) not null
);

insert into product (id, name)
select n, 'produkt-' || n
  from generate_series(1, 30) as n
on conflict (id) do nothing;
