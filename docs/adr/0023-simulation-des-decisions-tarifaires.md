# 0023. Simulation des décisions tarifaires : une règle tirée au sort chaque jour

**Date** : 2026-09-09
**Statut** : Accepté

## Contexte

« Notre prix » est une valeur fictive posée dans le catalogue (ADR 0015). Il ne bouge jamais, alors que le marché
bouge tous les jours : les index, les rangs et les prix conseillés dérivent, et la démo finit par comparer un prix
absurde à un marché réel. Il n'y a pas non plus d'historique de notre prix, donc rien à tracer face aux courbes des
enseignes.

## Options envisagées

1. **Laisser le prix fixe.** Simple, mais la démo se dégrade avec le temps.
2. **Appliquer chaque jour la recommandation du profil de référence.** Vivant, mais monotone : notre prix suit
   toujours la même règle, les écarts intéressants disparaissent.
3. **Tirer au sort chaque jour une règle parmi les profils calculés** et appliquer sa recommandation. Le prix reste
   plausible, parce que ce sont les recommandations du moteur, avec ses garde-fous ; il varie, parce que la règle
   change ; et chaque décision est expliquée par la recommandation qui l'a produite.

## Décision

Option 3. Après les recommandations, une étape de décision tire au sort, pour chaque produit actif, une règle parmi
celles qui proposent un prix sur le marché strict, et applique ce prix. Le tirage est uniforme et reproductible : la
graine est le jour et le produit, deux exécutions le même jour prennent la même décision. Une décision par produit et
par jour, enregistrée même quand elle ne change rien (marché insuffisant, prix identique), avec l'ancien prix, le
nouveau, la règle et l'explication.

Conséquence sur le catalogue : `currentPrice` devient un prix initial, utilisé à la création du produit seulement.
Le prix courant vit en base.

Le tirage est uniforme plutôt que pondéré vers la règle de référence : l'objectif est le mouvement et des écarts
lisibles sur les graphiques, pas le réalisme d'une politique tarifaire. La pondération reste un réglage possible.

## Conséquences

- Notre prix a un historique (`product_price_decision`, vue `api.our_price_history`) : le portfolio le trace en
  escalier face aux enseignes, et la synthèse affiche la règle du jour.
- Les garde-fous du moteur bornent la variation quotidienne (ADR 0022) : le prix ne peut pas s'emballer.
- Une recommandation en repli (« maintien ») ne change rien ; un produit sans marché garde son prix.
- L'étape se désactive par `pricing.daily-decision=false`, par exemple si un jour le prix courant vient d'un système
  réel plutôt que de la simulation.
