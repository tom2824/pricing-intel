# 0013. Pas de pilotage de navigateur ni de camouflage : un fournisseur externe pour l'usage pro

**Date** : 2026-09-05
**Statut** : Accepté

## Contexte

Le client HTTP du projet fait des requêtes GET simples, s'identifie par son User-Agent et respecte robots.txt
(ADR 0006). Question posée : faut-il prévoir de piloter un navigateur, de modifier les en-têtes pour imiter un
navigateur, ou de tourner sur des proxys résidentiels, pour suivre des sites mieux protégés ou monter en volume ?

## Options envisagées

1. **Imiter un navigateur (User-Agent Chrome, en-têtes copiés).** Trompe un filtre naïf, mais pas les protections
   réelles (empreinte TLS, ordre des en-têtes, absence d'exécution JavaScript, analyse comportementale). Et c'est
   l'inverse de ce que le projet affiche : un outil qui s'identifie. Coût réputationnel sans gain réel.
2. **Piloter un navigateur headless (Playwright).** Résout le rendu JavaScript, pas la détection. Un binaire
   Chromium dans la CI, des collectes dix fois plus lentes, une course perdue d'avance contre les anti-bots.
   Justifiable uniquement pour un site précis dont les prix ne sont présents que via JavaScript (ADR 0007).
3. **Déléguer à un fournisseur spécialisé** (Bright Data Web Unlocker, Oxylabs Web Scraper API, ou équivalent).
   On envoie une URL, on reçoit le HTML rendu ; proxys, challenges et rendu sont gérés par un tiers dont c'est le
   métier, avec un contrat, une facturation au volume et une responsabilité claire. C'est ce que font les outils
   de veille tarifaire professionnels.

## Décision

Pour l'usage actuel (quelques dizaines de pages par jour, sites de taille moyenne), le client poli reste seul et
sans camouflage. Si un usage professionnel réel apparaît (volume, sites à protection forte, obligation de résultat),
la réponse sera l'option 3 : une implémentation supplémentaire du port `PageFetcher` qui appelle l'API du
fournisseur, choisie par configuration, si besoin site par site. Le scraper, les extracteurs et les sinks
n'ont pas à changer, et les tests hors ligne restent valables.

L'option 2 reste possible pour un cas isolé, derrière le même port, et ne sera envisagée que sur un site nommé.
L'option 1 est écartée définitivement.

## Conséquences

- Le module `collector-http` n'a pas vocation à devenir un outil d'évasion : il reste un client HTTP correct.
- Le port `PageFetcher` est la seule frontière à respecter pour brancher un fournisseur externe ; l'archivage
  des pages brutes et la chaîne d'extraction fonctionnent tels quels sur le HTML qu'il renvoie.
- Passer à un fournisseur a un coût mensuel : ce sera une décision d'usage pro, pas un réglage de projet perso.
