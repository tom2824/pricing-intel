# 0022. Moteur de stratégies : un prix candidat, des garde-fous, une explication

**Date** : 2026-09-07
**Statut** : Accepté

## Contexte

Le cœur du projet : à partir d'une vue marché (ADR 0017, 0020) et du contexte d'un produit (prix actuel,
prix d'achat, leader désigné), proposer un prix. Ce prix doit s'expliquer étape par étape, sans quoi aucune
équipe ne le validera (voir la philosophie du projet).

## Options envisagées

1. **Une seule formule paramétrable.** Court, mais vite illisible : l'explication devient une formule.
2. **Des règles métier mélangées dans chaque stratégie.** Chaque stratégie recode le plancher de marge et
   l'arrondi ; incohérences garanties.
3. **Deux étages séparés** : une *stratégie* choisit un prix candidat par rapport au marché ; des *règles
   transverses* l'encadrent ensuite, dans un ordre fixe, quelle que soit la stratégie. Chaque étage laisse une
   trace dans l'explication.

## Décision

Option 3, dans un module `pricing-engine` en Java pur (dépend du domaine et du JDK seulement, règle ArchUnit).

**Stratégies de la première version** (chacune répond à « où je veux être par rapport au marché ») :

| Stratégie | Prix candidat |
|---|---|
| Alignement | le prix le moins cher du marché |
| Undercut | le moins cher, moins X % ou moins X € |
| Index cible | X % d'une référence du marché (médiane par défaut, moyenne ou minimum) |
| Suivi du leader | le prix d'un concurrent désigné, plus ou moins un écart |
| Marge cible | prix d'achat × (1 + marge) |
| Maintien | notre prix actuel ; stratégie de repli |

Une stratégie qui ne peut pas s'appliquer (pas de leader dans le marché, pas de prix d'achat) le dit et le
moteur se replie sur Maintien, en l'expliquant.

**Règles transverses, dans cet ordre** :

1. **Sources minimum** : moins de N offres retenues (2 par défaut) → le marché n'est pas fiable, repli sur Maintien.
2. **Plancher de marge** : jamais sous prix d'achat × (1 + marge minimum).
3. **Plafond** : jamais au-dessus de X % de la médiane.
4. **Variation maximale par jour** : ± X % par rapport à notre prix actuel.
5. **Arrondi psychologique** : terminaison ,99 (ou ,95), vers le bas.

**Profil** : une stratégie et ses paramètres, plus les paramètres des règles. Un profil par défaut ; plus tard
un profil par famille ou par produit.

**Explication** : une liste d'étapes (étage, libellé, valeur avant, valeur après), rendue en français, plus
le résumé du marché (offres retenues et écartées avec leurs raisons). Exemple attendu sur la MSI RTX 5070
Ventus avec index 98, marge minimum 15 %, variation maximale 3 %, arrondi ,99 :
« marché strict : 3 offres, min 959,99, médiane 979,95 · index 98 % de la médiane → 960,35 · plancher marge
943,00 respecté · variation −1,0 % dans la limite de 3 % · arrondi ,99 → 959,99 ».

**Périmètre** : une stratégie raisonne sur la vue marché qu'on lui donne, strict ou segment (ADR 0009). C'est
l'appelant qui choisit le périmètre ; Index cible sur le marché de segment donne le « positionnement segment ».

## Conséquences

- Le moteur est testable en millisecondes avec des marchés construits à la main, et publiable comme
  bibliothèque si un jour un autre outil en a besoin.
- Les recommandations calculées chaque nuit avec le profil par défaut sont stockées (ADR 0005) pour que
  l'onglet du portfolio reste vivant sans serveur ; les paramètres personnalisés passeront par l'API.
- Ajouter une stratégie, c'est une classe qui implémente l'interface et un test ; les règles ne changent pas.
