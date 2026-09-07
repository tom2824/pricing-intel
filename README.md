# Pricing Intel

Veille tarifaire multi-sources et moteur de recommandation de prix, appliqués aux composants PC.
Un produit, N sources, un historique fiable, puis un prix proposé selon la stratégie choisie, avec son explication.

État : **collecte par scraping** et **persistance PostgreSQL** (catalogue, correspondances, relevés quotidiens avec
quarantaine, échecs de collecte) et **moteur de stratégies** (marché expliqué, six stratégies, garde-fous, recommandations stockées chaque jour) livrés, collecte quotidienne réelle sur quatre enseignes (LDLC, TopAchat, Materiel.net, Cybertek). Viennent ensuite l'API de lecture et l'onglet du portfolio.

Les choix d'architecture sont documentés dans [docs/adr](docs/adr/README.md), la démarche dans
[docs/philosophie.md](docs/philosophie.md).

## Architecture

Monolithe modulaire, ports et adaptateurs. Le domaine et le cœur ne connaissent aucun framework ;
les adaptateurs implémentent des ports ; seul le module d'application connaît Spring. Ces règles sont
vérifiées par ArchUnit à chaque build.

```mermaid
flowchart LR
    subgraph adapters_in [Sources]
        scraper[source-scraper<br/>YAML sites · JSON-LD → JSON embarqué → CSS]
        api[source API<br/>port prêt, aucune prévue (ADR 0019)]
    end
    subgraph core [Cœur]
        domain[domain<br/>Listing · PriceSnapshot · Money]
        collector[collector-core<br/>ports + CollectionRun]
    end
    subgraph adapters_out [Sorties]
        file[sink-file<br/>JSON Lines · archives distillées]
        pg[persistence<br/>PostgreSQL · Flyway · catalogue · relevés]
    end
    http[collector-http<br/>proxy · rate limit · retry · robots.txt]
    pricing[pricing-engine<br/>marché · stratégies · explication]
    batch[app-batch<br/>Spring Boot, mode batch]

    scraper -- PriceSource --> collector
    api -. PriceSource .-> collector
    http -- PageFetcher --> scraper
    collector -- PriceSink --> file
    collector -- PriceSink --> pg
    pg -- ListingProvider --> collector
    collector --> domain
    pg -- offres observées --> pricing
    pricing -- recommandations --> pg
    batch -- assemble --> scraper & http & file & pg & collector
```

| Module               | Rôle                                                                                   | Dépend de        |
|----------------------|----------------------------------------------------------------------------------------|------------------|
| `domain`             | Modèle métier (records immuables)                                                      | JDK              |
| `collector-core`     | Ports `PriceSource`, `PriceSink`, `PageFetcher`, `RawSnapshotStore`, `ListingProvider` ; orchestration | `domain` |
| `collector-http`     | Client HTTP poli : `ProxyPolicy` (aucun / fixe / rotation), rate limit par hôte, retry avec backoff, robots.txt | `collector-core` |
| `source-scraper`     | Sites déclarés en YAML, chaîne d'extraction, parsing de prix FR/EN                     | `collector-core`, Jsoup, Jackson |
| `sink-file`          | Relevés en JSON Lines, archives de pages distillées (JSON + Markdown) ou HTML complet, rétention | `collector-core`, Jackson, Jsoup |
| `pricing-engine`     | Vue marché (règles de l'ADR 0020, chaque exclusion expliquée), six stratégies, garde-fous ordonnés, explication étape par étape (ADR 0022) | `domain` |
| `persistence`        | Adaptateur PostgreSQL : schéma Flyway, catalogue en JPA (familles, produits, identifiants, annonces, correspondances), relevés avec quarantaine et échecs en SQL natif, import de catalogue YAML | `collector-core`, Spring Data JPA, Flyway |
| `app-batch`          | Point d'entrée Spring Boot sans serveur web : configuration, assemblage, code de sortie | tout             |
| `architecture-tests` | Règles ArchUnit sur les frontières entre modules                                       | tout (test)      |

## Démarrer

Prérequis : JDK 21. Maven est fourni par le wrapper.

```bash
./mvnw verify
```

Puis déclarer les annonces à relever et lancer une collecte :

```bash
cp config/listings.example.yml config/listings.yml
```

```bash
./mvnw -q -DskipTests -pl app-batch -am package
```

```bash
java -jar app-batch/target/app-batch-0.1.0-SNAPSHOT-exec.jar
```

Par défaut la collecte affiche chaque relevé en console, l'ajoute à `data/releves.jsonl`, et archive une version
distillée de chaque page sous `data/raw/` (blocs JSON embarqués intacts + contenu visible en Markdown, quelques Ko,
rétention 180 jours ; le HTML complet est optionnel, rétention 7 jours, voir ADR 0014). Les archives expirées sont
purgées à la fin de chaque collecte. Le code de sortie vaut 1 si aucun relevé n'a pu être produit.

Sous Windows, remplacer `./mvnw` par `mvnw.cmd`.

### Lancer une brique seule

Toute la configuration est surchargeable en ligne de commande ou par variable d'environnement
(`COLLECTOR_PROXY_MODE=fixed`). Quelques exemples :

```bash
java -jar app-batch/target/app-batch-0.1.0-SNAPSHOT-exec.jar --collector.sinks.types=jsonl --collector.sinks.jsonl-file=./sortie.jsonl --collector.raw.enabled=false
```

```bash
java -jar app-batch/target/app-batch-0.1.0-SNAPSHOT-exec.jar --collector.proxy.mode=fixed --collector.proxy.host=proxy.interne --collector.proxy.port=3128
```

```bash
java -jar app-batch/target/app-batch-0.1.0-SNAPSHOT-exec.jar --collector.min-interval-per-host=10s --collector.sites.allow-unknown-hosts=false
```

Clés disponibles : voir [`application.yml`](app-batch/src/main/resources/application.yml).

## Déclarer un site

Un fichier YAML par site dans `config/sites/`. Les extracteurs sont essayés dans l'ordre ; le premier qui
trouve un prix gagne, et le relevé garde la méthode et sa confiance.

```yaml
id: ldlc
host: "*.ldlc.com"          # exact, *.domaine, ou * pour tout
extractors:
  - type: jsonld            # schema.org Product/Offer, sans paramètre
  - type: embedded-json     # état applicatif embarqué
    script: "script#__NEXT_DATA__"       # ou variable: window.__INITIAL_STATE__
    paths:
      price: /props/pageProps/product/price
      availability: /props/pageProps/product/stock/quantity
      gtin: /props/pageProps/product/ean
  - type: css               # dernier recours
    price: ".price"
    listPrice: ".price-old"
    availability: ".stock"
    title: "h1"
```

Un hôte sans définition est tenté avec le seul extracteur JSON-LD (désactivable). Une propriété inconnue dans
un YAML fait échouer le démarrage : les fautes de frappe se voient tout de suite.

## Ce qu'un relevé contient

Prix, prix barré, devise, disponibilité, état (neuf/occasion), type de vendeur, identité observée du produit
(GTIN, marque, référence fabricant, SKU, titre) pour le futur matching, méthode d'extraction et confiance,
URL finale et horodatage. Exemple d'une ligne de `releves.jsonl` :

```json
{"listingId":{"value":"ldlc-rtx4070s-msi-ventus"},"observedAt":"2026-09-05T08:00:12.418Z","observedUrl":"https://www.ldlc.com/fiche/PB00584657.html","price":{"amount":629.95,"currency":"EUR"},"availability":"IN_STOCK","condition":"NEW","sellerType":"UNKNOWN","identity":{"gtin":"4711377114363","brand":"MSI","sku":"PB00584657","title":"MSI GeForce RTX 4070 SUPER 12G VENTUS 2X OC"},"extraction":{"method":"jsonld","confidence":0.95}}
```

## Base de données (profil `postgres`)

Sans base configurée, tout tourne en mode fichiers. Le profil Spring `postgres` branche PostgreSQL (Supabase ou
n'importe quel Postgres) : Flyway crée le schéma, les annonces sont lues en base, les relevés y sont écrits
(une ligne par annonce et par jour, upsert, quarantaine des prix aberrants), les échecs de collecte aussi.

```bash
export SPRING_PROFILES_ACTIVE=postgres PRICING_INTEL_DB_URL="jdbc:postgresql://HOST:5432/postgres?sslmode=require" PRICING_INTEL_DB_USER=postgres PRICING_INTEL_DB_PASSWORD=...
```

Importer le catalogue de démonstration (produits, identifiants, annonces et correspondances manuelles) puis collecter :

```bash
java -jar app-batch/target/app-batch-0.1.0-SNAPSHOT-exec.jar --collector.catalogue.import-file=config/catalogue.yml
```

L'import est idempotent. Le modèle est décrit dans les ADR 0015 à 0018 : familles de produits avec
caractéristiques à rôles (identité, équivalence, descriptive), clé naturelle unique, identifiants multiples
(plusieurs GTIN par produit), correspondances annonce ↔ produit datées avec preuve, relevé quotidien.
Les tests de persistance tournent sur un PostgreSQL embarqué, sans Docker.

## Recommandations de prix

Après chaque collecte sous le profil `postgres`, le moteur construit pour chaque produit deux marchés
(strict : le produit chez chaque enseigne ; segment : les produits équivalents) en appliquant les règles de
l'ADR 0020, applique le profil par défaut et stocke une recommandation avec son explication complète.
Exemple réel du 7 septembre 2026 :

```text
MSI GeForce RTX 5070 12G GAMING TRIO OC · proposé 953.99 EUR (index 98.35)
marché strict : 3 offre(s) de 3 enseigne(s), min 949.99 EUR, médiane 969.95 EUR, max 969.95 EUR, 1 écartée(s)
· index 98 % de la médiane : 969.95 EUR → 950.55 EUR
· plancher marge 15 % (954.50 EUR) appliqué : 950.55 EUR → 954.50 EUR
· plafond 110 % de la médiane (1066.95 EUR) respecté → 954.50 EUR
· variation -2.6 % dans la limite de ±5 % → 954.50 EUR
· arrondi ,99 vers le bas : 954.50 EUR → 953.99 EUR
```

Les profils se règlent sous `pricing.*` dans [`application.yml`](app-batch/src/main/resources/application.yml) :
une liste de stratégies précalculées (`index:98`, `align`, `undercut:1`, `cost-plus:25`, `leader:ldlc:-2`, `hold`),
la première étant la référence, plus les garde-fous communs (sources minimum, plancher de marge, plafond, variation
maximale par jour, arrondi) et les règles du marché (fraîcheur, stock, reconditionné, quarantaine, marketplace).
Les vues du schéma `api` (matrice, historique, synthèse, recommandations) sont le contrat de lecture du portfolio,
servies par l'API REST de Supabase ; les tables restent privées (sécurité par ligne, migration V5).

## Politesse et cadre d'usage

Un relevé par annonce et par jour, une requête toutes les trois secondes par hôte, User-Agent qui identifie
le projet, robots.txt respecté (y compris `Crawl-delay`), retry avec backoff sur erreur transitoire seulement,
pas de proxy par défaut, pas d'appel aux API internes des sites. Détails et raisonnement dans l'ADR 0006.

## Feuille de route

1. ~~Base Supabase branchée, cron GitHub Actions actif, premier vrai relevé~~ fait
2. ~~Quatrième source et quatrième segment (ADR 0019)~~ fait
3. ~~Analyse de marché et moteur de stratégies avec explication~~ fait (ADR 0020, 0022)
4. API de lecture et onglet portfolio (matrice produit × enseigne, synthèse avec prix conseillé et explication)
5. Détection des cassures d'extraction (ADR 0021) : taux de réussite par source, contrôle d'identité, rejeu sur archives
6. Matching semi-automatique (GTIN, marque + référence, similarité de titre)

## Licence

MIT.
