-- Une recommandation par jour, produit, périmètre et profil (ADR 0022, 0023, 0024). Jusqu'ici l'unicité portait
-- sur l'instant de calcul : une seconde exécution le même jour insérait une seconde ligne, calculée sur notre prix
-- déjà modifié par la décision du matin, et la rétention supprimait la première, celle qui avait servi à décider.
-- Désormais la première recommandation du jour fait foi ; la suivante est ignorée (voir PostgresRecommendationSink).

alter table recommendation add column computed_date date;
update recommendation set computed_date = (computed_at at time zone 'UTC')::date;
alter table recommendation alter column computed_date set not null;

-- Doublons existants : on garde la dernière ligne de chaque jour, comme la rétention le faisait.
delete from recommendation r
using recommendation later
where later.product_id = r.product_id and later.scope = r.scope and later.profile_key = r.profile_key
  and later.computed_date = r.computed_date and later.computed_at > r.computed_at;

alter table recommendation drop constraint recommendation_unique;
alter table recommendation add constraint recommendation_daily_unique unique (product_id, scope, profile_key, computed_date);

-- La rétention filtre sur la date de calcul seule ; l'index existant (product_id, computed_at) ne l'aide pas.
create index recommendation_computed_at_idx on recommendation (computed_at);
create index collection_failure_occurred_at_idx on collection_failure (occurred_at);

-- Matrice produit × enseigne : le dernier relevé de chaque annonce active par jointure latérale, soit un parcours
-- d'index par annonce, au lieu d'un tri de toute la table des relevés, jamais purgée, à chaque ouverture du portfolio.
-- Mêmes colonnes, même ordre : la vue est remplacée en place.
create or replace view api.price_matrix as
select lcp.product_id, p.name as product_name, p.family_code, p.brand, p.equivalence_key, p.current_price,
       l.code as listing_code, l.source_code, src.label as source_label, l.url,
       s.price, s.list_price, s.currency, s.availability, s.item_condition, s.seller_type, s.quarantine,
       s.observed_at, s.observed_date, s.extraction_method,
       (s.seller_type = 'MARKETPLACE') as is_marketplace,
       min(s.price) filter (where s.availability = 'IN_STOCK' and s.quarantine in ('none', 'confirmed'))
           over (partition by lcp.product_id) as min_in_stock
from listing l
join lateral (
    select *
    from price_snapshot snap
    where snap.listing_id = l.id
    order by snap.observed_at desc
    limit 1
) s on true
join listing_current_product lcp on lcp.listing_id = l.id
join product p on p.id = lcp.product_id
join source src on src.code = l.source_code
where l.active;
