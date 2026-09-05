# 0012. Sortie paramétrable : le port PriceSink

**Date** : 2026-09-05
**Statut** : Accepté

## Contexte

On veut lancer une collecte vers un fichier sans base de données, vers une base en production, vers un webhook
un jour, ou vers plusieurs à la fois, et choisir ça sans toucher au code.

## Options envisagées

1. **La collecte écrit directement en base.** Le plus court, mais la brique de collecte devient inutilisable
   sans base, et toute nouvelle destination est une modification de la collecte.
2. **Un port de sortie, des adaptateurs, une composition choisie en configuration.** La collecte pousse chaque
   relevé dans un `PriceSink`. Console, JSON Lines, Postgres, webhook sont des implémentations dans des
   modules séparés. Un `CompositePriceSink` envoie à plusieurs.

## Décision

Option 2. `collector.sinks.types` liste les sorties actives. Le format de fichier par défaut est JSON Lines :
une ligne JSON par relevé, ajoutée en fin de fichier et flushée immédiatement, pour qu'une collecte interrompue
laisse sur disque ce qu'elle a relevé. Le fichier se rejoue, se diff-e et se charge n'importe où.

Un sink qui échoue interrompt la collecte : perdre des relevés en silence serait pire qu'un batch en erreur.

## Conséquences

- Ajouter une destination, c'est un module et une ligne de configuration.
- Le même relevé peut aller à la fois en base et dans un fichier de secours.
- L'archivage des pages brutes est un port distinct (`RawSnapshotStore`), parce que sa politique d'échec est
  l'inverse : il ne doit jamais interrompre la collecte.
