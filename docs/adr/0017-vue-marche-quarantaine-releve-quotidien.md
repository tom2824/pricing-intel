# 0017. Un relevé par jour et par annonce, échecs stockés, quarantaine, et une vue marché pour le moteur

**Date** : 2026-09-05
**Statut** : Accepté, à implémenter

## Contexte

Le moteur de stratégies ne doit pas raisonner sur des relevés bruts : hors stock, occasion, prix aberrants et
relevés périmés fausseraient les recommandations. Il faut définir ce qu'est « le marché » d'un produit à une
date, et décider de la granularité des relevés.

## Options envisagées

**Granularité des relevés**

1. **Une ligne seulement quand le prix change.** Moins de lignes, mais impossible de distinguer « le prix n'a
   pas bougé » de « on n'a pas relevé ce jour-là ». Les fenêtres de fraîcheur et les courbes deviennent pénibles.
2. **Une ligne par jour et par annonce, même à prix constant.** Plus de lignes (négligeable à notre échelle :
   quelques dizaines d'annonces), mais un jour sans ligne est un vrai trou, visible et explicable.

**Ce que voit le moteur**

3. Les relevés bruts, à lui de filtrer : logique métier dupliquée dans chaque stratégie.
4. Une **vue marché** calculée en amont par le module d'analyse, objet Java pur, que le moteur consomme.

## Décision

Options 2 et 4.

**Relevés.** Une ligne par jour et par annonce, unicité sur (annonce, jour). Un relevé hors stock est une ligne
(avec la disponibilité, et le prix affiché s'il y en a un) : sur une courbe, c'est un point grisé.

**Échecs de collecte stockés.** Une table `collection_failure` : annonce, date de collecte, source, raison,
transitoire ou non. Un trou dans la courbe peut alors être expliqué (« HTTP 503 », « aucun extracteur n'a trouvé
de prix ») au lieu d'être muet, et un extracteur cassé se repère sans lire les logs.

**Quarantaine.** Un relevé dont l'écart avec le précédent de la même annonce dépasse un seuil (50 % par défaut,
réglable par famille) est marqué `suspect` et exclu du marché. Il n'est jamais supprimé. Le relevé suivant, s'il
confirme le nouveau prix, lève la quarantaine ; sinon un humain tranche.

**Vue marché d'un produit à une date.** Calculée par le module d'analyse à partir des relevés, avec des filtres
d'inclusion paramétrables par défaut et par produit :

- dernier relevé de chaque annonce dans une fenêtre de fraîcheur (48 h par défaut) ;
- état neuf seulement ; en stock seulement ; vente directe ou marketplace selon réglage ; relevés en
  quarantaine exclus ; une offre par enseigne (la moins chère si plusieurs annonces) ;
- **deux périmètres** (ADR 0009) : *strict*, les annonces du produit lui-même ; *segment*, les annonces des
  produits de même clé d'équivalence.

Elle expose les offres retenues (source, prix, date, confiance d'extraction) et les agrégats : minimum, médiane,
moyenne, nombre de sources, prix du leader déclaré s'il existe, prix actuel et prix d'achat fictif du produit.
Le moteur ne sait pas d'où viennent ces chiffres.

## Conséquences

- Le graphique distingue trois choses : un prix (point), une rupture (point grisé), pas de relevé (trou, avec
  la raison de l'échec en info-bulle quand elle est connue).
- Les stratégies restent du Java pur et testable : elles reçoivent un marché, elles rendent un prix expliqué.
- Les seuils (fraîcheur, quarantaine) sont des paramètres, avec des valeurs par défaut prudentes.
