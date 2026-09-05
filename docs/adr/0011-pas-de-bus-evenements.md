# 0011. Pas de bus d'événements pour l'instant

**Date** : 2026-09-05
**Statut** : Accepté

## Contexte

Un relevé de prix pourrait déclencher des traitements en aval : recalcul du marché, recommandation, alerte de
baisse. Un bus d'événements (`PriceObserved`, `MarketChanged`...) est la façon « propre » de découpler ces
étapes, et c'est tentant dès le départ.

## Options envisagées

1. **Bus d'événements dès maintenant.** Découplage maximal, mais une couche d'indirection de plus à comprendre,
   à tester et à déboguer, pour un seul consommateur qui n'existe pas encore.
2. **Appels directs à travers les ports.** La collecte pousse les relevés dans un `PriceSink`. L'analyse
   lira la base plus tard, dans un autre batch. Simple, lisible, suffisant.

## Décision

Option 2. Les modules communiquent par appels directs à travers les ports du cœur. Cet ADR existe pour dire
non explicitement, et pour fixer ce qui ferait changer d'avis :

- plusieurs consommateurs indépendants d'un même relevé (base + alerte + recalcul temps réel) ;
- un besoin de traitement asynchrone ou de reprise après échec partiel ;
- une brique déployée séparément qui doit réagir aux collectes d'une autre.

## Conséquences

- Moins de code, moins de concepts, des tests directs.
- Le `CompositePriceSink` couvre déjà « envoyer à plusieurs endroits » sans bus.
- Si un des déclencheurs ci-dessus apparaît, un nouvel ADR remplacera celui-ci, et l'introduction se fera
  derrière le port `PriceSink` sans toucher aux sources.
