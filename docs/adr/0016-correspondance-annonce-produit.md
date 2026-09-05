# 0016. Correspondance annonce ↔ produit : une relation datée avec statut, score et preuve

**Date** : 2026-09-05
**Statut** : Accepté, à implémenter

## Contexte

L'ADR 0008 fixe le principe du matching semi-automatique. Il faut maintenant dire comment une annonce est
reliée à un produit dans le modèle, et ce qui se passe quand on se trompe ou quand un site change le produit
vendu derrière une URL.

## Options envisagées

1. **Clé étrangère directe `listing.product_id`.** Simple, mais sans histoire : on ne sait ni pourquoi ni depuis
   quand, et corriger une erreur réécrit le passé (les relevés d'hier changent de produit rétroactivement).
2. **Une table de correspondance datée**, avec statut, méthode, score, preuve, auteur et horodatages.

## Décision

Option 2. Table `listing_match` :

- une annonce, un produit, un statut (`proposed`, `validated`, `rejected`) ;
- la méthode et le score : `gtin` (1.0), `brand_mpn` (0.95), `title_similarity` (0.6 à 0.9), `manual` (1.0) ;
- la preuve, en JSON : les valeurs qui ont fondé la décision (« gtin 4711377114363 = identifiant confirmé ») ;
- l'auteur (`auto` ou un humain), la date de création, la date de fin de validité si la correspondance a été
  remplacée ou rejetée, et une raison.

Règles :

- **Au plus une correspondance validée à la fois par annonce.** Des propositions et des rejets peuvent coexister ;
  deux validations simultanées, jamais.
- **Le relevé pointe vers l'annonce, pas vers le produit.** Le produit d'un relevé est celui de la correspondance
  validée à la date du relevé. Corriger un matching ne réécrit aucun relevé : on clôt l'ancienne correspondance,
  on en valide une nouvelle, et l'historique reste cohérent avant et après.
- **Les identités observées enrichissent le catalogue.** Le GTIN, la marque et la référence lus à chaque relevé sont
  stockés avec lui. Quand une correspondance est validée, les identifiants observés inconnus deviennent des
  identifiants du produit au statut « à confirmer ». Un humain confirme ; le catalogue apprend.
- **Ordre du matching automatique** : identifiant `gtin` connu → `mpn` + marque connus → similarité de titre.
  Au-dessus du seuil de validation automatique (1.0 seulement, au départ), la correspondance est validée
  directement ; sinon elle est proposée et attend un humain.

## Conséquences

- Une file « à valider » existe dès la première version, même si elle n'est alimentée que par le manuel.
- Les requêtes « prix d'un produit » passent par une jointure datée ; une vue SQL la masquera.
- Le cas « le site a remplacé le produit derrière l'URL » est traité sans perte : rejet daté avec raison,
  nouvelle correspondance, et les courbes de chaque produit restent justes.
