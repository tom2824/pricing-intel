# 0020. Règles du marché : ce qu'une stratégie a le droit de regarder

**Date** : 2026-09-07
**Statut** : Accepté. Précise la « vue marché » de l'ADR 0017.

## Contexte

Le marché d'un produit est la photo des prix concurrents retenus pour se positionner, à une date donnée.
Il faut dire quelles offres entrent dans cette photo, lesquelles en sortent, et pourquoi, sachant que chaque
exclusion doit pouvoir être expliquée dans la recommandation finale.

## Décision

Un prix affiché par un site est un prix de vente : on ne le vérifie pas. Les règles ci-dessous ne doutent pas
des sites, elles délimitent ce qui est comparable.

Règles par défaut, toutes paramétrables par défaut global, par famille et par produit :

| Règle | Défaut | Pourquoi |
|---|---|---|
| Fraîcheur | dernier relevé de chaque annonce, au plus 48 h | un prix de la semaine dernière n'est plus le marché |
| Disponibilité | en stock seulement | on ne s'aligne pas sur un prix auquel personne ne peut acheter |
| État | neuf seulement, avec une vue alternative « neuf + reconditionné » | un reconditionné n'est pas un concurrent du neuf, mais l'utilisateur peut vouloir le voir |
| Quarantaine | relevés suspects exclus | la quarantaine doute de notre extraction (mensualité, accessoire, bundle), pas du site ; elle se lève seule au relevé suivant |
| Marketplace | exclue du marché strict, affichée comme signal | le prix d'un vendeur tiers n'est pas la décision tarifaire de l'enseigne ; c'est le plancher du marché, pas le marché |
| Dédoublonnage | une offre par enseigne, la moins chère | détail technique : « nombre de sources » compte des enseignes, pas des URL |

Deux périmètres (ADR 0009) : **strict** (annonces du produit lui-même) et **segment** (annonces des produits de
même clé d'équivalence). Une stratégie déclare sur lequel elle raisonne.

Chaque offre écartée conserve sa raison (« Cybertek 894,99 € non retenu : rupture »), et la vue marché les
expose à côté des offres retenues : l'explication d'un prix est complète ou elle n'est pas.

## Conséquences

- Le moteur de stratégies ne lit jamais la table des relevés : il reçoit une vue marché, objet Java pur.
- Les vues « neuf » et « neuf + reconditionné » supposent que les annonces reconditionnées soient au catalogue ;
  elles ne le sont pas encore.
- Le type de vendeur est aujourd'hui rarement observable (JSON-LD sans champ vendeur) : la règle marketplace
  est prête, mais inerte sur les sources actuelles, toutes vendues par l'enseigne.
