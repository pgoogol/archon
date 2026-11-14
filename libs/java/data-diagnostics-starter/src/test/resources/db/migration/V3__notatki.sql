-- Notatki testowej aplikacji: tabela dla repozytorium Spring Data JDBC.
create table if not exists note (
    id   bigint primary key,
    body varchar(128) not null
);

insert into note (id, body)
values (1, 'pierwsza notatka')
on conflict (id) do nothing;
