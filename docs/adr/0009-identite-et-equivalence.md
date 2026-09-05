# 0009. Deux relations de matching : identité et équivalence

**Date** : 2026-09-05
**Statut** : Accepté, à implémenter

## Contexte

Dans la distribution automobile, deux pneus de même dimension, saison et indices sont comparables même si la
marque diffère : le client les met en concurrence, et un outil de pricing se positionne sur le segment, pas
seulement sur la référence identique. Question : ce raisonnement par caractéristiques a-t-il un sens pour des
jeux et du hardware ?

## Options envisagées

1. **Identité seulement.** Même produit, même référence. Simple, mais aveugle : une RTX 4070 Super MSI n'est
   jamais comparée à la même carte chez Gigabyte, alors que le client le fait.
2. **Équivalence seulement.** Tout mélanger dans un segment. Trop grossier : la prime de marque en tech est
   réelle et variable, aligner un prix sur un produit équivalent moins cher peut être une erreur.
3. **Deux relations distinctes.** *Identité* : même produit (GTIN, référence), sert à l'alignement et à
   l'undercut. *Équivalence* : même classe fonctionnelle, calculée à partir d'une clé d'équivalence définie
   par catégorie (GPU : chipset + VRAM ; SSD : capacité + interface + format ; RAM : capacité + type +
   fréquence + latence ; pneu : dimension + saison + indices ; jeu : aucune, un jeu n'a pas de substitut).

## Décision

Option 3. Chaque stratégie précisera sur quel marché elle raisonne : strict (produit identique, plusieurs
sources) ou de segment (produits équivalents, toutes marques). Le marché de segment est un signal secondaire,
jamais mélangé au marché strict. Une pondération par tiers de marque (premium, milieu, entrée de gamme) pourra
s'ajouter sans changer le modèle.

Côté données : une catégorie déclare son schéma d'attributs et lesquels forment la clé ; un produit porte ses
attributs (JSONB) ; la clé se calcule. Les attributs viennent du JSON-LD, des fiches techniques ou de la saisie
manuelle au départ.

## Conséquences

- L'outil devient réutilisable hors tech : suivre des pneus, c'est ajouter une catégorie avec sa clé, sans code.
- Des stratégies nouvelles deviennent possibles : « tiers bas du segment », « jamais plus de 5 % au-dessus de la
  marque leader du segment », alerte quand le segment bouge alors que le produit identique n'est trouvé nulle part.
- C'est le point du projet qui vient le plus directement d'une expérience métier ; il mérite d'être expliqué
  dans la page de philosophie.
