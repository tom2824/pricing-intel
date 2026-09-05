# 0008. Matching semi-automatique avec score et preuve

**Date** : 2026-09-05
**Statut** : Accepté, à implémenter (cette version ne fait que du matching manuel via `listings.yml`)

## Contexte

Pour comparer des prix entre sources, il faut savoir que telle page chez LDLC et telle page chez TopAchat
vendent le même produit. C'est le « matching », et c'est la partie la plus pénible d'un outil de veille
tarifaire : références qui diffèrent, bundles, occasion, variantes de marque. Une erreur de matching en pricing
se traduit par un prix aligné sur le mauvais produit.

## Options envisagées

1. **Tout à la main.** Fiable, mais ne passe pas à l'échelle et n'apprend rien.
2. **Tout automatique.** Passe à l'échelle, mais les erreurs sont silencieuses, et une erreur silencieuse en
   pricing coûte cher.
3. **L'automatique propose, l'humain valide au-dessus d'un seuil de doute.** Règles par niveau de confiance :
   GTIN/EAN identique (1.0, accepté automatiquement), marque + référence fabricant normalisées (haute, accepté
   si unique), similarité de titre normalisé (score entre 0 et 1, proposé à validation au-dessus de 0.9,
   ignoré en dessous), manuel (1.0, « validé par humain »). Chaque correspondance porte son statut (proposée,
   validée, rejetée), son score et sa preuve (quel critère, quelles valeurs).

## Décision

Option 3, en deux temps. Cette version : matching manuel dans `config/listings.yml` (une annonce déclare son
produit) et extraction du GTIN, de la marque, de la référence et du titre à chaque relevé pour alimenter le
futur matching. Version suivante : proposition automatique par GTIN puis par marque + référence, file « à
valider », similarité de titre en dernier.

Cas à gérer explicitement par les règles : variantes (le même GPU chez trois marques, ce sont trois produits),
bundles, occasion et reconditionné, listings marketplace. Pour les jeux, sans GTIN, l'identifiant de l'API
(CheapShark, Steam) fait office de clé.

## Conséquences

- Le modèle de relevé porte dès maintenant `ObservedIdentity` (GTIN, marque, référence, SKU, titre) même si
  rien ne l'exploite encore : la donnée s'accumule.
- Le score de confiance et la preuve seront dans le modèle de données dès la première table de matching, même
  si seul le manuel les alimente au début.
