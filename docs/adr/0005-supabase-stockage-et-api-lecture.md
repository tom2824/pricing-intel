# 0005. PostgreSQL sur Supabase comme stockage et API de lecture

**Date** : 2026-09-05
**Statut** : Accepté, à implémenter (le sink Postgres n'existe pas encore)

## Contexte

Les relevés doivent survivre aux exécutions du batch (ADR 0004) et être consultables depuis un portfolio
statique hébergé sur GitHub Pages, qui ne peut pas héberger de base ni d'API.

## Options envisagées

1. **Fichiers JSON versionnés dans le dépôt.** Simple, mais le dépôt grossit à chaque collecte et les requêtes
   analytiques (historique, médiane par produit) deviennent du travail côté client.
2. **Postgres sur un hébergement gratuit qui dort.** Même problème que pour le serveur : latence de réveil
   devant un visiteur.
3. **Postgres managé sur Supabase.** Gratuit dans les limites d'un projet perso, toujours allumé, et expose
   automatiquement une API REST en lecture sur les tables, avec une clé publique restreinte par les règles
   d'accès (RLS). Le portfolio interroge directement cette API, sans serveur intermédiaire.

## Décision

Option 3. Le batch écrit dans Postgres via un `PriceSink` dédié (`sink-postgres`, à venir). Les recommandations
avec paramètres par défaut seront précalculées chaque nuit et stockées, pour que l'onglet du portfolio reste
vivant sans serveur applicatif. L'API Spring (`app-web`) reste dans le projet pour la documentation, les tests,
et les simulations avec paramètres personnalisés ; elle sera déployée quand un hébergement permanent existera.

## Conséquences

- Dépendance à un fournisseur pour la base, mais c'est du Postgres standard : exportable, migrable vers un VPS.
- Les règles d'accès Supabase doivent être écrites avec soin : lecture publique des relevés et recommandations,
  écriture réservée au batch via une clé de service stockée en secret GitHub.
- Un snapshot JSON statique du dernier état, régénéré par le workflow, servira de filet de sécurité si l'API
  est indisponible devant un visiteur.

## Complément (2026-09-07) : l'API de lecture est en place

- Un schéma `api` de vues (`summary`, `price_matrix`, `price_history`, `recommendations`, `collection_failures`,
  `products`, `sources`, `families`, `collection_runs`) est le contrat de lecture du portfolio (migration V5).
  Les tables restent privées : sécurité par ligne activée partout sans politique, droits des rôles `anon` et
  `authenticated` retirés du schéma `public` et limités à la lecture du schéma `api`. Les vues s'exécutent avec les
  droits de leur propriétaire, le rôle du batch.
- Côté Supabase : le schéma `api` est ajouté aux schémas exposés du Data API, et l'exposition automatique des
  nouvelles tables est désactivée. Le front appelle `/rest/v1/<vue>` avec l'en-tête `Accept-Profile: api` et la clé
  publique (« publishable »), conçue pour être embarquée dans un navigateur.
- Le simulateur du front choisit parmi des profils précalculés chaque nuit (index 95, 98, 100, 103, alignement,
  undercut 1 %, marge cible 25 %) ; le premier est la référence des vues de synthèse. Un module `app-web` n'est
  pas nécessaire tant que ce choix suffit.
- Vérifié depuis l'extérieur : lecture des vues OK, lecture d'une table refusée (401), écriture refusée.
