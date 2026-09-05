# 0015. Catalogue : familles de produits, caractéristiques à rôles, clé naturelle et identifiants multiples

**Date** : 2026-09-05
**Statut** : Accepté, à implémenter

## Contexte

Le modèle de données doit dire ce qu'est un produit. Deux constats issus de l'expérience terrain :
un article peut porter plusieurs EAN (changement d'emballage, code par marché, réétiquetage distributeur),
donc le GTIN ne peut pas être la clé d'un produit ; et deux produits qui ne se distingueraient que par un
identifiant interne opaque sont un doublon ou une caractéristique manquante, donc l'identité doit reposer sur
des caractéristiques déclarées.

## Options envisagées

1. **Le GTIN comme clé primaire.** Simple, mais faux : plusieurs GTIN par produit, et parfois un GTIN réutilisé.
2. **Un identifiant interne opaque comme seule identité.** Techniquement confortable, mais rien n'empêche deux
   fiches pour le même article, et un humain ne sait pas dire si deux fiches sont le même produit.
3. **Une clé naturelle composée, sans identifiant interne.** Satisfait la règle métier, mais une clé composée
   fait une mauvaise clé étrangère : longue, répétée dans chaque relevé, et une correction de référence
   fabricant oblige à réécrire tout ce qui pointe dessus.
4. **Famille de produits comme schéma, clé naturelle unique, identifiant interne comme plomberie.**

## Décision

Option 4.

- **Famille** : un schéma de caractéristiques typées (nombre avec unité, texte, énumération, booléen),
  normalisées à la saisie (« 12 Go », « 12GB », « 12 GB » sont une seule valeur). Chaque caractéristique porte
  un ou plusieurs rôles :
  - *identifiante* : entre dans la clé naturelle ;
  - *d'équivalence* : entre dans la clé de segment (ADR 0009) ;
  - *descriptive* : information, filtres, affichage.
  Exemples : GPU → chipset et VRAM d'équivalence, marque + référence + variante identifiantes, TDP descriptif.
  Pneu → dimension et indices identifiants *et* d'équivalence, saison d'équivalence, étiquette énergétique descriptive.
- **Socle commun** à toutes les familles : marque, référence fabricant, nom canonique, statut.
- **Produit** : famille, socle commun, valeurs des caractéristiques. Contrainte d'unicité sur la clé naturelle
  (socle identifiant + caractéristiques identifiantes de la famille) : la base refuse un doublon.
  Un identifiant interne stable, jamais réutilisé, sert de clé étrangère partout ; il ne définit rien.
- **Identifiants externes** : relation un-à-plusieurs (`gtin`, `mpn`, `steam_appid`, `cheapshark_id`...), avec
  origine (saisie, observé sur une annonce) et statut confirmé ou non. Un GTIN observé est une preuve forte de
  correspondance, pas une identité. Valeurs normalisées (UPC-12 complété à 13, chiffre de contrôle vérifié).
  Unicité d'une valeur entre produits par défaut : un conflit doit être tranché par un humain, pas absorbé.
- **L'état (neuf, reconditionné, occasion) est une propriété de l'annonce, pas du produit.** Le même produit
  vendu neuf et reconditionné donne deux annonces reliées au même produit ; l'analyse de marché compare le neuf
  au neuf. Un bundle, lui, est un produit distinct avec ses propres codes.

## Conséquences

- Ajouter une famille (pneus, un jour) est une déclaration de schéma, pas du code.
- La clé de segment se calcule à partir des caractéristiques d'équivalence ; elle n'est pas saisie.
- Corriger une caractéristique ne casse aucune référence : l'identifiant interne absorbe le changement, la
  contrainte d'unicité empêche que la correction crée un doublon.
- Les valeurs de caractéristiques seront stockées en JSONB avec validation applicative contre le schéma de la
  famille, plutôt qu'une table par famille : le catalogue est ouvert, le schéma SQL ne bouge pas.
