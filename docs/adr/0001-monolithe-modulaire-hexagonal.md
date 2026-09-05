# 0001. Monolithe modulaire en architecture hexagonale

**Date** : 2026-09-05
**Statut** : Accepté

## Contexte

Le projet a trois briques de nature différente : la collecte (scraping et API), l'analyse de marché et le moteur
de stratégies, et plus tard une API de lecture. On veut pouvoir utiliser chaque brique indépendamment (lancer
une collecte vers un fichier sans base de données, réutiliser le moteur de stratégies dans un autre contexte),
sans multiplier les projets à maintenir pour un développeur seul.

## Options envisagées

1. **Un seul module, tout ensemble.** Le plus rapide au départ. Mais rien n'empêche le domaine d'appeler Spring
   ou JPA, et « chaque brique indépendante » devient un vœu pieux au bout de trois semaines.
2. **Plusieurs dépôts, un par brique.** Indépendance maximale. Mais versions à synchroniser, CI multipliée,
   refactorings transverses pénibles. Pour une personne, c'est de la friction sans bénéfice avant longtemps.
3. **Un dépôt, plusieurs modules Maven, frontières strictes.** Chaque module est un jar autonome avec ses
   dépendances déclarées. Le domaine et le moteur ne connaissent aucun framework. Les adaptateurs
   (HTTP, scraper, sinks) implémentent des ports définis dans le cœur. Extraire un module dans son propre
   dépôt plus tard reste possible sans réécriture.

## Décision

Option 3 : monolithe modulaire, ports et adaptateurs.

- `domain` : modèle métier, dépend uniquement du JDK.
- `collector-core` : ports (`PriceSource`, `PriceSink`, `PageFetcher`, `RawSnapshotStore`, `ListingProvider`)
  et orchestration. Dépend du domaine.
- `collector-http`, `source-*`, `sink-*` : adaptateurs. Chacun dépend du cœur, jamais d'un adaptateur frère.
- `app-*` : points d'entrée qui assemblent les briques. Seuls modules qui connaissent Spring.
- `architecture-tests` : les règles ci-dessus sous forme de tests ArchUnit. Le build échoue si une frontière
  est franchie.

## Conséquences

- Plus de fichiers `pom.xml` et un peu de cérémonie à chaque nouveau module.
- Les tests du cœur et du domaine tournent en millisecondes sans contexte Spring.
- Ajouter une source ou une sortie, c'est ajouter un module qui implémente un port, sans toucher aux autres.
- Révision à envisager si une brique doit être déployée et versionnée séparément (par exemple le moteur de
  stratégies publié comme bibliothèque) : ce serait le moment de la sortir dans son propre dépôt.
