# 0006. API pour les jeux, scraping léger pour le hardware, pas de proxy par défaut

**Date** : 2026-09-05
**Statut** : Accepté

## Contexte

Deux familles de produits sont suivies : jeux vidéo et composants PC. Les prix de jeux sont disponibles via
des API publiques gratuites (CheapShark, IsThereAnyDeal, endpoint JSON de Steam). Les prix de hardware ne le
sont pas : il faut lire les pages des enseignes. Question : jusqu'où aller, et faut-il des proxys ?

## Options envisagées

1. **Tout scraper, y compris les boutiques de jeux.** Inutile : les API existent, sont plus stables, et
   fournissent l'historique. Scraper ce qu'une API donne gratuitement est une perte de temps et de crédibilité.
2. **Scraper à grande échelle avec un pool de proxys.** Nécessaire au-delà de quelques milliers de pages par
   jour et par site. Coûteux, fragile, et surtout ce n'est pas le besoin : quelques dizaines de produits, un
   relevé par jour.
3. **API là où elles existent, scraping poli et minuscule ailleurs.** Un relevé par annonce et par jour, une
   requête toutes les quelques secondes par hôte, User-Agent qui identifie le projet, respect de robots.txt,
   retry avec backoff sur erreur transitoire. Sites de taille moyenne (LDLC, TopAchat, Materiel.net) plutôt
   que ceux à protection anti-bot agressive (Amazon, marketplaces).

## Décision

Option 3. Le proxy reste possible (`ProxyPolicy` : aucun, fixe, rotation) parce qu'il a un usage légitime
(réseau d'entreprise, usage à plus grande échelle assumé), mais il est désactivé par défaut. Si le projet se
retrouve à en avoir besoin pour ne pas être bloqué, c'est que le volume ou le rythme est mal cadré : la première
réponse est de ralentir, pas de contourner.

Cadre légal retenu : relever des prix publics à faible volume, pour un usage personnel et sans republication
massive des données, ne contourne aucune protection technique et ne viole pas de conditions d'utilisation de
façon caractérisée. L'outil n'appelle pas les API internes (XHR) des sites : plus fragile et plus discutable.

## Conséquences

- Le module HTTP est conçu autour de la politesse (rate limit par hôte, robots.txt, backoff), pas de l'évasion.
- Les pages qui exigent un rendu JavaScript ne sont pas supportées pour l'instant (voir ADR 0007).
- Si un site bloque malgré tout, on le retire ou on ralentit : on n'ajoute pas de proxy pour insister.
