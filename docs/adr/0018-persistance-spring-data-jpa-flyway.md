# 0018. Persistance : un adaptateur Spring Data JPA + Flyway, SQL natif pour les relevés

**Date** : 2026-09-05
**Statut** : Accepté. Amende l'ADR 0003 : Spring est autorisé dans les modules d'adaptateur de persistance.

## Contexte

Le modèle (ADR 0015 à 0017) doit devenir un schéma PostgreSQL. L'ADR 0003 réserve Spring aux modules
d'application ; un adaptateur de persistance utilisant Spring Data JPA le contredit. Il faut aussi choisir
entre JPA et SQL direct, et une façon de tester sans Docker (absent du poste de développement).

## Options envisagées

1. **JDBC pur, sans framework, dans le respect strict de l'ADR 0003.** Cohérent, mais on réécrit un mapping et
   des transactions que Spring fournit, et on renonce à montrer Spring Data JPA, attendu sur le marché visé.
2. **Spring Data JPA partout, y compris pour les séries de prix.** Hibernate est mal adapté aux upserts et aux
   requêtes analytiques (dernier relevé par annonce, médiane par segment) : requêtes natives partout, entités
   qui n'apportent rien.
3. **Hybride** : JPA pour le catalogue (familles, produits, identifiants, sources, annonces, correspondances),
   SQL natif via `JdbcClient` pour les relevés, les échecs et les requêtes d'analyse. Flyway pour le schéma.

## Décision

Option 3, dans un module `persistence` qui est un adaptateur : il implémente les ports du cœur (`PriceSink`,
`ListingProvider`, `CollectionReportSink`) et expose des dépôts pour le catalogue. Le domaine et le cœur
restent sans framework ; la règle ArchUnit devient « Spring et Jakarta autorisés dans `..batch..` et
`..persistence..` seulement ».

- **Flyway** : le schéma est versionné dans `db/migration`, appliqué au démarrage. Les données de référence
  (familles avec leur schéma de caractéristiques, sources) sont une migration ; le catalogue de démonstration
  s'importe depuis un fichier YAML par une commande, ce n'est pas du schéma.
- **JSONB** pour le schéma de caractéristiques d'une famille, les caractéristiques d'un produit et la preuve
  d'une correspondance : le catalogue est ouvert, le schéma SQL ne bouge pas quand une famille arrive.
- **Clés naturelle et d'équivalence** calculées par l'application, stockées en colonnes indexées, avec une
  contrainte d'unicité sur la clé naturelle (ADR 0015).
- **Upsert** des relevés sur (annonce, jour) : rejouer une collecte le même jour remplace le relevé, ne le duplique
  pas et n'échoue pas.
- **Tests** sur un PostgreSQL embarqué (binaires téléchargés par Maven, pas de Docker), avec les vraies migrations :
  le SQL est testé, pas simulé.

## Conséquences

- Le module `persistence` dépend de Spring Data JPA, Hibernate, Flyway et du pilote PostgreSQL. C'est le seul
  adaptateur avec un framework, et c'est assumé.
- La base cible reste du PostgreSQL standard (Supabase aujourd'hui, un VPS demain) ; rien de propriétaire.
- Les tests de persistance sont plus lents (démarrage d'un Postgres) : ils vivent dans leur module, les autres
  modules gardent leurs tests en millisecondes.
