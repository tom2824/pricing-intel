# 0007. Chaîne d'extraction et archivage des pages brutes

**Date** : 2026-09-05
**Statut** : Accepté

## Contexte

Le prix d'une page HTML peut être lu à plusieurs endroits : dans les données structurées schema.org (JSON-LD),
dans l'état applicatif embarqué par les frameworks front (`__NEXT_DATA__`, `window.__INITIAL_STATE__`), ou dans
le DOM via des sélecteurs CSS. Ces sources n'ont ni la même fiabilité ni la même durée de vie. Et quand un site
change de structure, on découvre le problème le lendemain, avec la donnée du jour perdue.

## Options envisagées

1. **Sélecteurs CSS seulement.** Universel, mais casse à chaque refonte, et ne donne ni GTIN ni marque.
2. **JSON-LD seulement.** Très fiable là où il existe, mais tous les sites ne l'ont pas, ou pas complet.
3. **Chaîne ordonnée, du plus structuré au plus fragile, avec traçabilité.** Chaque site déclare quels
   extracteurs il autorise et dans quel ordre. Le premier qui trouve un prix gagne. Le relevé enregistre la
   méthode utilisée et un niveau de confiance (JSON-LD 0.95, JSON embarqué 0.85, CSS 0.7) que l'analyse pourra
   pondérer.

## Décision

Option 3, plus la séparation entre collecter et extraire : chaque réponse HTTP est archivée compressée
(`data/raw/<annonce>/<horodatage>.html.gz` avec ses métadonnées) avant toute extraction. Un extracteur corrigé
peut être rejoué sur les pages du passé, et un prix aberrant peut être confronté à ce que la page affichait.
L'archivage ne doit jamais faire échouer une collecte : une erreur est journalisée, pas propagée.

Le rendu JavaScript (navigateur headless) est explicitement hors périmètre tant qu'aucune source ciblée ne
l'exige : coût en ressources, fragilité, et ce n'est pas nécessaire sur les sites retenus.

## Conséquences

- Les extracteurs sont de petites classes sans état, testées hors ligne sur des pages fixtures.
- L'espace disque des snapshots bruts croît linéairement ; une rétention (quelques jours ou semaines) sera à
  ajouter avec le stockage durable.
- Un site sans JSON-LD ni JSON embarqué se retrouve avec la seule méthode CSS et une confiance basse : c'est
  voulu, l'analyse doit le savoir.
- Une validation en aval (prix nul, écart anormal avec le relevé précédent, mise en quarantaine plutôt que
  suppression) est prévue au niveau de l'analyse, pas de l'extraction.
