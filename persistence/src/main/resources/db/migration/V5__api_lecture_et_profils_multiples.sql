-- ADR 0005 (complété le 2026-09-07) : l'API REST automatique de Supabase sert l'onglet du portfolio.
-- 1. Plusieurs profils de prix précalculés par produit (le simulateur du front choisit parmi eux).
-- 2. Un schéma "api" de vues, contrat stable pour le front ; les tables restent privées.
-- 3. Sécurité par ligne sur toutes les tables, sans politique : la clé publique ne lit que les vues.

-- ---------------------------------------------------------------------------------------------------------
-- 1. Profils multiples
-- ---------------------------------------------------------------------------------------------------------
alter table recommendation add column profile_key text not null default 'default';
alter table recommendation add column is_default boolean not null default true;
alter table recommendation drop constraint recommendation_unique;
alter table recommendation add constraint recommendation_unique unique (product_id, scope, profile_key, computed_at);

drop view recommendation_latest;
create view recommendation_latest as
select distinct on (r.product_id, r.scope, r.profile_key) r.*
from recommendation r
order by r.product_id, r.scope, r.profile_key, r.computed_at desc;

-- ---------------------------------------------------------------------------------------------------------
-- 2. Schéma api : une vue par écran
-- ---------------------------------------------------------------------------------------------------------
create schema if not exists api;

create view api.sources as
select code, label, kind, homepage from source;

create view api.families as
select code, label, attribute_schema from product_family;

create view api.products as
select p.id, p.family_code, f.label as family_label, p.brand, p.mpn, p.name, p.attributes, p.equivalence_key,
       p.current_price, p.purchase_price, p.currency, p.status
from product p
join product_family f on f.code = p.family_code;

-- Matrice produit × enseigne : le dernier relevé de chaque annonce active, avec le minimum en stock du produit.
create view api.price_matrix as
with latest as (
    select distinct on (s.listing_id) s.*
    from price_snapshot s
    order by s.listing_id, s.observed_at desc
)
select lcp.product_id, p.name as product_name, p.family_code, p.brand, p.equivalence_key, p.current_price,
       l.code as listing_code, l.source_code, src.label as source_label, l.url,
       s.price, s.list_price, s.currency, s.availability, s.item_condition, s.seller_type, s.quarantine,
       s.observed_at, s.observed_date, s.extraction_method,
       (s.seller_type = 'MARKETPLACE') as is_marketplace,
       min(s.price) filter (where s.availability = 'IN_STOCK' and s.quarantine in ('none', 'confirmed'))
           over (partition by lcp.product_id) as min_in_stock
from latest s
join listing l on l.id = s.listing_id and l.active
join listing_current_product lcp on lcp.listing_id = l.id
join product p on p.id = lcp.product_id
join source src on src.code = l.source_code;

-- Historique : une ligne par relevé ; un jour absent est un vrai trou (voir api.collection_failures).
create view api.price_history as
select lcp.product_id, l.code as listing_code, l.source_code, s.observed_date, s.observed_at,
       s.price, s.list_price, s.availability, s.item_condition, s.quarantine
from price_snapshot s
join listing l on l.id = s.listing_id
join listing_current_product lcp on lcp.listing_id = l.id;

create view api.collection_failures as
select f.listing_code, l.source_code, lcp.product_id, f.occurred_at, f.reason, f.retryable
from collection_failure f
left join listing l on l.id = f.listing_id
left join listing_current_product lcp on lcp.listing_id = l.id;

create view api.collection_runs as
select id, started_at, finished_at, attempted, collected, failed from collection_run;

-- Dernière recommandation par produit, périmètre et profil, marché et explication inclus.
create view api.recommendations as
select r.product_id, p.name as product_name, p.family_code, r.scope, r.profile_key, r.is_default, r.strategy,
       r.fell_back, r.price, r.currency, r.index_vs_median, r.market, r.explanation, r.profile, r.computed_at
from recommendation_latest r
join product p on p.id = r.product_id;

-- Synthèse : une ligne par produit actif, sur le marché strict et le profil par défaut.
create view api.summary as
select p.id as product_id, p.name as product_name, p.family_code, f.label as family_label, p.brand,
       p.equivalence_key, p.current_price, p.purchase_price, p.currency,
       (r.market ->> 'sourceCount')::int              as source_count,
       (r.market ->> 'min')::numeric                  as market_min,
       (r.market ->> 'median')::numeric               as market_median,
       (r.market ->> 'mean')::numeric                 as market_mean,
       (r.market ->> 'max')::numeric                  as market_max,
       case when p.current_price is not null and (r.market ->> 'median')::numeric > 0
            then round(p.current_price * 100 / (r.market ->> 'median')::numeric, 2) end as current_index,
       case when p.current_price is not null and (r.market ->> 'sourceCount')::int > 0
            then (select count(*) + 1 from jsonb_array_elements(r.market -> 'retained') o
                  where (o ->> 'price')::numeric < p.current_price) end as current_rank,
       r.price                                        as recommended_price,
       r.strategy, r.fell_back,
       r.index_vs_median                              as recommended_index,
       r.explanation ->> 'text'                       as explanation,
       r.computed_at
from product p
join product_family f on f.code = p.family_code
left join recommendation_latest r on r.product_id = p.id and r.scope = 'strict' and r.is_default
where p.status = 'active';

-- ---------------------------------------------------------------------------------------------------------
-- 3. Sécurité : les tables sont privées, seules les vues du schéma api sont lisibles par la clé publique.
--    Les vues s'exécutent avec les droits de leur propriétaire (le rôle du batch), qui possède les tables.
-- ---------------------------------------------------------------------------------------------------------
alter table product_family     enable row level security;
alter table product            enable row level security;
alter table product_identifier enable row level security;
alter table source             enable row level security;
alter table listing            enable row level security;
alter table listing_match      enable row level security;
alter table price_snapshot     enable row level security;
alter table collection_run     enable row level security;
alter table collection_failure enable row level security;
alter table recommendation     enable row level security;

do $$
begin
    -- Rôles propres à Supabase : absents sur un PostgreSQL de test, d'où la garde.
    if exists (select 1 from pg_roles where rolname = 'anon') then
        execute 'revoke all on all tables in schema public from anon, authenticated';
        execute 'revoke all on all sequences in schema public from anon, authenticated';
        execute 'revoke all on all functions in schema public from anon, authenticated';
        execute 'grant usage on schema api to anon, authenticated';
        execute 'grant select on all tables in schema api to anon, authenticated';
        execute 'alter default privileges in schema api grant select on tables to anon, authenticated';
        execute 'alter default privileges in schema public revoke all on tables from anon, authenticated';
    end if;
end
$$;
