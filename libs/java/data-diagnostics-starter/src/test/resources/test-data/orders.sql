-- Sześć zamówień po dwie pozycje: pętla po zamówieniach daje sześć zapytań o pozycje,
-- czyli więcej niż próg N+1 w trybie dev (5).
truncate table order_item, orders restart identity cascade;

insert into orders (number)
values ('A-1'), ('A-2'), ('A-3'), ('A-4'), ('A-5'), ('A-6');

insert into order_item (order_id, sku, quantity)
select o.id, 'sku-' || o.id || '-' || n, n
  from orders o
 cross join generate_series(1, 2) as n;
