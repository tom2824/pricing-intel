# 0024. Rétention en base alignée sur le quota du fournisseur

**Date** : 2026-09-09
**Statut** : Accepté

## Contexte

La base tourne sur l'offre gratuite de Supabase (ADR 0005) : 500 Mo. L'objectif est de garder un maximum
d'historique sans jamais devoir arrêter la collecte, et de pouvoir ajouter des sources et des produits sans
faire exploser le volume. Mesure du 9 septembre 2026, après quatre jours de collecte :

| Table | Lignes par jour | Taille d'une ligne | Croissance par jour |
|---|---|---|---|
| Relevés (`price_snapshot`) | 54, une par annonce et par jour | 280 octets | 15 Ko |
| Décisions (`product_price_decision`) | 15 | 540 octets | 8 Ko |
| Recommandations (`recommendation`) | 210 par exécution, deux exécutions | 1,7 Ko (marché et explication en JSON) | 730 Ko |

Les recommandations font 97 % de la croissance : 270 Mo par an, le quota en moins de deux ans. Les relevés,
qui sont l'historique qui compte, en font moins de 6 Mo par an.

## Options envisagées

1. **Ne rien purger, et migrer un jour.** Simple, mais on sait déjà quand ça casse.
2. **Alléger les recommandations (sans le marché JSON).** Divise par deux, ne change pas la nature du problème,
   et supprime l'explication détaillée qui fait l'intérêt du projet.
3. **Élaguer les recommandations selon leur usage réel.** Le front n'affiche que la dernière recommandation de
   chaque profil ; l'historique du prix conseillé n'a de sens que pour le profil de référence. Une par jour et par
   profil, les profils secondaires sur une fenêtre courte, le profil de référence sur une fenêtre longue, la
   dernière de chaque profil toujours conservée.

## Décision

Option 3, en fin de chaque collecte (module `persistence`, étape de batch après la décision tarifaire) :

- **Jamais purgés** : relevés, décisions tarifaires, exécutions de collecte, catalogue. Ce sont les données
  d'historique, et elles sont petites.
- **Recommandations** : une seule par jour, produit, périmètre et profil, garantie par la table elle-même depuis la
  migration V11 : la première exécution du jour fait foi, c'est elle qui a servi à la décision tarifaire (ADR 0023),
  une exécution suivante le même jour est ignorée (à l'origine la dernière gagnait, ce qui faisait afficher une
  explication calculée après la décision) ;
  profils secondaires gardés 7 jours ; profil de référence gardé 365 jours, mais **compacté** au-delà de 7 jours :
  on garde les chiffres du marché (sources, min, médiane, moyenne, max), le prix, l'index et la phrase
  d'explication, on lâche les offres détaillées et les étapes, qui ne servent qu'à la lecture du jour. La
  dernière recommandation de chaque profil n'est jamais touchée : la vue `recommendation_latest` et l'API restent
  pleines.
- **Échecs de collecte** : 180 jours.

Croissance résultante : relevés 15 Ko, décisions 8 Ko, recommandations de référence compactées 10 Ko, soit environ
35 Ko par jour, moins de 15 Mo par an. Le quota tient plus de dix ans, et une source ou un produit de plus n'ajoute que des
relevés, c'est-à-dire presque rien. Les fenêtres sont des propriétés (`retention.*`), les archives de pages restent
régies par l'ADR 0014.

## Ce qui reste en base, et pourquoi

| Donnée | Rétention | Pourquoi |
|---|---|---|
| Catalogue (familles, produits, annonces, correspondances) | permanente | le référentiel |
| Relevés, un par annonce et par jour | permanente | l'historique des prix, la matière première |
| Décisions tarifaires | permanente | l'historique de notre prix et de la règle appliquée |
| Exécutions de collecte | permanente, une ligne par exécution | la traçabilité |
| Recommandation la plus récente de chaque profil | tant qu'une plus récente ne la remplace pas | ce que l'API et le portfolio affichent |
| Recommandations du profil de référence | 365 jours, compactées après 7 | l'historique du prix conseillé |
| Recommandations des autres profils | 7 jours | le simulateur ne regarde que le présent |
| Échecs de collecte | 180 jours | expliquer un trou récent dans une courbe |
| Archives de pages (fichiers, hors base) | ADR 0014 | rejouer un extracteur |

## Conséquences

- L'historique du prix conseillé des profils secondaires n'existe pas au-delà d'une semaine : c'est un choix, le
  simulateur du front choisit parmi les recommandations du jour, pas dans le passé.
- Les suppressions laissent des lignes mortes que l'autovacuum de PostgreSQL recycle ; la taille sur disque ne
  redescend pas immédiatement, elle cesse de monter.
- Le diagnostic (`diagnose-db.yml`) affiche la taille des tables et la croissance par jour : à consulter avant
  d'ajouter des sources en nombre.
