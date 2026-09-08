-- ADR 0023 : chaque jour, une règle tirée au sort met à jour notre prix à partir de sa recommandation.
-- Chaque décision est conservée ; le prix courant du produit n'est plus une constante du catalogue.

create table product_price_decision (
    id            bigserial primary key,
    product_id    bigint        not null references product (id),
    decision_date date          not null,
    decided_at    timestamptz   not null,
    old_price     numeric(12, 2),
    new_price     numeric(12, 2),
    currency      char(3)       not null,
    profile_key   text          not null,
    strategy      text          not null,
    changed       boolean       not null,
    reason        text          not null,
    unique (product_id, decision_date)
);
create index product_price_decision_product_idx on product_price_decision (product_id, decision_date desc);
alter table product_price_decision enable row level security;

-- Historique de notre prix, pour la courbe du portfolio.
create view api.our_price_history as
select product_id, decision_date, decided_at, old_price, new_price, currency, profile_key, strategy, changed, reason
from product_price_decision;

-- La synthèse gagne la décision du jour (colonnes ajoutées en fin de vue).
create or replace view api.summary as
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
       r.computed_at,
       d.profile_key                                  as decision_profile_key,
       d.changed                                      as decision_changed,
       d.old_price                                    as decision_old_price,
       d.decision_date                                as decision_date
from product p
join product_family f on f.code = p.family_code
left join recommendation_latest r on r.product_id = p.id and r.scope = 'strict' and r.is_default
left join lateral (
    select profile_key, changed, old_price, decision_date
    from product_price_decision
    where product_id = p.id
    order by decision_date desc
    limit 1
) d on true
where p.status = 'active';
