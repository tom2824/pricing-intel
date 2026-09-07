# 0021. Extraction robuste aux refontes et détection rapide des cassures

**Date** : 2026-09-07
**Statut** : Accepté

## Contexte

Les sites changent souvent. Le risque principal d'un outil de veille n'est pas de rater une page un jour,
c'est de lire de travers pendant des semaines sans s'en apercevoir, ou de perdre une source sans le voir.
Il faut une méthode de ciblage qui casse le moins possible, et une détection qui remonte une cassure dès la
collecte suivante, avec une correction rapide.

## Décision

**Ciblage, du plus stable au plus fragile** (ADR 0007), en explicitant pourquoi c'est l'ordre :

1. **JSON-LD schema.org**. Standardisé, maintenu par les sites pour Google Shopping, indépendant de la mise en
   page. Une refonte graphique ne le touche pas. Donne prix, devise, disponibilité, état, GTIN, marque,
   référence, nom. C'est le ciblage par défaut ; les quatre sources actuelles l'exposent.
2. **JSON embarqué** de l'application front. Stable tant que le framework ne change pas ; adressé par pointeur
   JSON, pas par position.
3. **CSS**, en dernier recours, et alors par **attributs sémantiques** (`itemprop`, `id` fonctionnel,
   `data-*`) plutôt que par classes de mise en page ou position dans l'arbre. `#_ctl0_..._l_prix` survit à une
   refonte graphique ; `div > div:nth-child(2) > span` non.

**Les caractéristiques techniques ne sont pas extraites des pages** : elles sont déclarées au catalogue
(ADR 0015), parce que les pages ne les exposent pas de façon structurée et qu'elles ne changent pas dans le
temps. Les archives distillées (ADR 0014) conservent les tableaux de caractéristiques en Markdown si un jour
une extraction assistée est souhaitable.

**Détection**, à chaque collecte, sans intervention humaine :

- **Taux de réussite par source** : les échecs sont stockés par annonce et par collecte (ADR 0017). Une source
  qui passe sous un seuil (80 % par défaut) fait échouer le batch, donc notifie (GitHub Actions envoie un mail
  sur échec ; un webhook Discord pourra s'y ajouter).
- **Dérive de méthode** : un relevé qui passe de `jsonld` à `css` sur une annonce est un signal faible de
  refonte ; comptabilisé par source et remonté dans le bilan.
- **Contrôle d'identité** : le GTIN ou la référence observés sur la page sont comparés aux identifiants du
  produit apparié. Un écart signifie une mauvaise page ou un mauvais élément : relevé mis en quarantaine
  avec la raison « identité inattendue ».
- **Quarantaine** (ADR 0017) : un prix aberrant est isolé, pas ignoré, et confirmé ou rejeté au relevé suivant.
- **Bilan de collecte** lisible : par source, réussites, échecs, méthodes utilisées, quarantaines ; c'est la
  première chose qu'on regarde le matin.

**Correction rapide** :

- Les définitions de sites sont de la configuration YAML : corriger un sélecteur est un commit, sans code.
- Les archives distillées permettent de **rejouer** un extracteur corrigé sur les pages des jours précédents,
  et de tester une nouvelle définition avant de la déployer : une commande de rejeu sur archives locales.
- Des **pages fixtures réelles** par source (une fiche capturée, anonymisée si besoin) dans les tests : une
  régression de notre code sur un site se voit en CI avant le déploiement.

## Conséquences

- Le bilan de collecte s'enrichit (par source, par méthode) et devient une table de plus, ou des colonnes de
  `collection_run`.
- Le contrôle d'identité demande que les identifiants observés soient normalisés comme ceux du catalogue
  (GTIN complété, référence normalisée).
- La commande de rejeu est un petit développement (lecture d'archives distillées, exécution des extracteurs
  JSON) ; le rejeu CSS n'est possible que sur les archives HTML complètes, à courte rétention.
