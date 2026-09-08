# 0019. Abandon des jeux vidéo : le périmètre est le composant PC

**Date** : 2026-09-07
**Statut** : Accepté. Remplace la partie « jeux via API » de l'ADR 0006.

## Contexte

L'ADR 0006 prévoyait deux familles : jeux vidéo (via API CheapShark / IsThereAnyDeal) et composants PC (scraping).
Au moment de construire la source API, un constat métier s'est imposé.

## Options envisagées

1. **Garder les jeux.** Une source API gratuite existe (CheapShark), sans scraping. Mais un prix de jeu est une
   fonction en escalier : plat pendant des mois, puis moins 50 % pendant une semaine de soldes, identique sur toutes
   les boutiques qui suivent Steam. Aucune stratégie de positionnement n'a de sens : personne ne se place par
   rapport au marché, tout le monde suit les soldes. De plus CheapShark ne donne que des prix en dollars US.
2. **Se concentrer sur le hardware.** Les composants bougent chaque semaine, avec des écarts entre enseignes,
   des ruptures, des baisses de fin de série et un vrai raisonnement de segment (ADR 0009). Dès le premier relevé
   réel, une enseigne était 2 à 6 % sous les deux autres : il y a matière à analyse.

## Décision

Option 2. Aucune source API pour les jeux n'est construite. L'effort va dans la profondeur du catalogue
hardware : cinq marques sur le segment phare (RTX 5070) pour que le marché de segment ait de la matière,
un quatrième segment à dynamique de prix forte (alimentations 850 W ATX 3.1, où la marque pèse peu, comme la
RAM), et une quatrième source pour sortir du duo LDLC / Materiel.net, qui est le même groupe aux mêmes prix.

La famille `game` reste dans le schéma (migration V2) sans être utilisée : la retirer coûterait une migration
pour rien, et une famille sans produit est inoffensive.

## Conséquences

- Le module `source-cheapshark` évoqué dans le README n'existera pas ; le port `PriceSource` reste prêt pour une
  source API si un jour une famille le justifie (pièces auto, par exemple).
- Le temps gagné va à l'analyse de marché et au moteur de stratégies, avec des données réelles pour les tester.
- Le portfolio présentera un outil de veille sur composants PC, pas un comparateur de prix de jeux.

## Complément (2026-09-08) : cinquième segment et retrait des produits invendus

- Un segment **processeurs** (famille `cpu`, migration V7) rejoint les quatre autres : Ryzen 7 7800X3D, 9800X3D et
  9700X, équivalents par socket et nombre de cœurs, suivis chez les quatre enseignes. Ce sont les produits les plus
  demandés du moment : un marché vivant vaut mieux qu'un marché théorique.
- Les produits qu'aucune enseigne ne vend plus (ASUS PRIME RTX 5070, deux kits DDR5 en rupture depuis le début du
  suivi) ne donnaient aucun marché et un prix conseillé en repli permanent. Le catalogue les passe en
  `status: retired` et désactive leurs annonces : ils sortent des vues et du moteur, leur historique reste en base.
  Rien n'est supprimé, conformément à l'ADR 0017.
