-- ADR 0005, 0022 : recommandations calculées à chaque collecte avec le profil par défaut, pour que l'onglet du
-- portfolio reste vivant sans serveur. Une ligne par produit, par périmètre (strict / segment) et par calcul.

create table recommendation (
    id               bigint generated always as identity primary key,
    product_id       bigint      not null references product (id) on delete cascade,
    computed_at      timestamptz not null,
    scope            text        not null check (scope in ('strict', 'segment')),
    strategy         text        not null,
    fell_back        boolean     not null default false,
    profile          jsonb       not null,
    price            numeric(12, 2) check (price is null or price > 0),
    currency         char(3)     not null,
    index_vs_median  numeric(7, 2),
    market           jsonb       not null,
    explanation      jsonb       not null,
    created_at       timestamptz not null default now(),
    constraint recommendation_unique unique (product_id, scope, computed_at)
);
create index recommendation_product_time_idx on recommendation (product_id, computed_at desc);

-- Vue : la dernière recommandation de chaque produit et périmètre.
create view recommendation_latest as
select distinct on (r.product_id, r.scope) r.*
from recommendation r
order by r.product_id, r.scope, r.computed_at desc;
