-- Schéma initial : catalogue (ADR 0015), correspondances (ADR 0016), relevés, échecs et quarantaine (ADR 0017).

-- Famille de produits : un schéma de caractéristiques typées, chacune avec ses rôles
-- (identity | equivalence | descriptive). Voir V2 pour les familles de référence.
create table product_family (
    code                  text primary key,
    label                 text        not null,
    attribute_schema      jsonb       not null default '[]',
    quarantine_threshold  numeric(4, 3) not null default 0.500
        check (quarantine_threshold > 0 and quarantine_threshold < 10),
    created_at            timestamptz not null default now()
);

-- Produit : socle commun + caractéristiques de la famille. L'identifiant interne est de la plomberie ;
-- l'identité est la clé naturelle (famille, marque, référence, caractéristiques identifiantes), unique.
create table product (
    id               bigint generated always as identity primary key,
    family_code      text        not null references product_family (code),
    brand            text        not null,
    mpn              text,
    name             text        not null,
    attributes       jsonb       not null default '{}',
    natural_key      text        not null,
    equivalence_key  text,
    purchase_price   numeric(12, 2) check (purchase_price is null or purchase_price > 0),
    current_price    numeric(12, 2) check (current_price is null or current_price > 0),
    currency         char(3)     not null default 'EUR',
    status           text        not null default 'active' check (status in ('active', 'retired')),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    constraint product_natural_key_unique unique (natural_key)
);
create index product_equivalence_key_idx on product (equivalence_key) where equivalence_key is not null;
create index product_family_idx on product (family_code);

-- Identifiants externes : plusieurs par produit (plusieurs GTIN, référence fabricant, id d'API...).
-- Une valeur appartient à un seul produit : un conflit se tranche par un humain, pas par la base.
create table product_identifier (
    id          bigint generated always as identity primary key,
    product_id  bigint      not null references product (id) on delete cascade,
    scheme      text        not null,
    value       text        not null,
    origin      text        not null default 'manual',
    confirmed   boolean     not null default false,
    created_at  timestamptz not null default now(),
    constraint product_identifier_unique unique (scheme, value)
);
create index product_identifier_product_idx on product_identifier (product_id);

-- Source de prix : un site scrapé ou une API.
create table source (
    code      text primary key,
    label     text not null,
    kind      text not null check (kind in ('scraper', 'api')),
    homepage  text
);

-- Annonce : un produit tel qu'il est vendu à un endroit précis. Le code est l'identifiant stable des relevés.
create table listing (
    id            bigint generated always as identity primary key,
    code          text        not null,
    source_code   text        not null references source (code),
    url           text        not null,
    external_ref  text,
    active        boolean     not null default true,
    created_at    timestamptz not null default now(),
    constraint listing_code_unique unique (code),
    constraint listing_source_url_unique unique (source_code, url)
);

-- Correspondance annonce <-> produit : datée, avec statut, méthode, score et preuve (ADR 0016).
create table listing_match (
    id          bigint generated always as identity primary key,
    listing_id  bigint      not null references listing (id) on delete cascade,
    product_id  bigint      not null references product (id),
    status      text        not null check (status in ('proposed', 'validated', 'rejected')),
    method      text        not null check (method in ('gtin', 'brand_mpn', 'title_similarity', 'manual')),
    score       numeric(4, 3) not null check (score >= 0 and score <= 1),
    evidence    jsonb       not null default '{}',
    author      text        not null default 'auto',
    valid_from  timestamptz not null default now(),
    valid_to    timestamptz,
    reason      text,
    created_at  timestamptz not null default now(),
    check (valid_to is null or valid_to >= valid_from)
);
create index listing_match_listing_idx on listing_match (listing_id);
create index listing_match_product_idx on listing_match (product_id);
-- Au plus une correspondance validée en vigueur par annonce.
create unique index listing_match_one_validated_idx on listing_match (listing_id)
    where status = 'validated' and valid_to is null;

-- Relevé de prix : une ligne par annonce et par jour (ADR 0017). Un jour sans ligne est un vrai trou.
create table price_snapshot (
    id                     bigint generated always as identity primary key,
    listing_id             bigint      not null references listing (id) on delete cascade,
    observed_at            timestamptz not null,
    observed_date          date        not null,
    observed_url           text        not null,
    price                  numeric(12, 2) not null check (price > 0),
    list_price             numeric(12, 2),
    shipping_cost          numeric(12, 2),
    currency               char(3)     not null,
    availability           text        not null,
    item_condition         text        not null,
    seller_type            text        not null,
    observed_gtin          text,
    observed_brand         text,
    observed_mpn           text,
    observed_sku           text,
    observed_title         text,
    extraction_method      text        not null,
    extraction_confidence  numeric(3, 2) not null,
    quarantine             text        not null default 'none'
        check (quarantine in ('none', 'suspect', 'confirmed', 'rejected')),
    created_at             timestamptz not null default now(),
    constraint price_snapshot_daily_unique unique (listing_id, observed_date)
);
create index price_snapshot_listing_time_idx on price_snapshot (listing_id, observed_at desc);

-- Une exécution de collecte et ses échecs, pour expliquer les trous des courbes.
create table collection_run (
    id           bigint generated always as identity primary key,
    started_at   timestamptz not null,
    finished_at  timestamptz not null,
    attempted    integer     not null,
    collected    integer     not null,
    failed       integer     not null
);

create table collection_failure (
    id            bigint generated always as identity primary key,
    run_id        bigint      not null references collection_run (id) on delete cascade,
    listing_id    bigint      references listing (id) on delete set null,
    listing_code  text        not null,
    source_id     text        not null,
    reason        text        not null,
    retryable     boolean     not null,
    occurred_at   timestamptz not null
);
create index collection_failure_listing_idx on collection_failure (listing_id, occurred_at desc);

-- Vue : le produit en vigueur d'une annonce (correspondance validée courante).
create view listing_current_product as
select l.id as listing_id, l.code as listing_code, m.product_id, m.method, m.score, m.valid_from
from listing l
join listing_match m on m.listing_id = l.id and m.status = 'validated' and m.valid_to is null;
