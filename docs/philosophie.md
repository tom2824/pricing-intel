# Philosophie du projet

> Brouillon à réécrire à la première personne. Il reprend ce qui a été dit pendant la conception ; c'est la
> page qui explique le *pourquoi*, là où les [ADR](adr/README.md) expliquent le *comment*.

## D'où vient le projet

Pendant un an en alternance dans une équipe pricing, j'ai travaillé sur des outils qui relèvent les prix des
concurrents et aident à positionner les nôtres. Ce projet reconstruit ce type d'outil à partir de zéro, sur des
produits que je connais et que je suis moi-même : jeux vidéo et composants PC. Les stratégies qu'il implémente
sont celles des manuels, pas celles d'une entreprise en particulier.

## Un prix proposé doit s'expliquer

Un moteur qui sort « 549,99 € » sans dire pourquoi n'est pas utilisable par une équipe : personne ne validera
un prix qu'il ne comprend pas. Chaque recommandation sort avec sa trace : la médiane du marché, le nombre de
sources en stock, l'index visé, l'arrondi appliqué, le plancher de marge respecté. C'est aussi ce qui permet
de repérer une erreur de matching avant qu'elle ne coûte de l'argent.

## Un historique fiable avant des fonctionnalités

Un relevé manqué ne se rattrape pas. Tout est construit pour que la collecte tourne tous les jours sans
dépendre d'une machine allumée, pour que ce qui a été relevé soit sur disque même si le batch s'interrompt, et
pour que la page brute soit archivée afin de pouvoir réextraire plus tard. Les fonctionnalités viennent après,
pendant que l'historique s'accumule.

## Être un bon citoyen du web

L'outil s'identifie par son User-Agent, respecte robots.txt, attend entre deux requêtes vers un même site,
recule en cas d'erreur, et n'appelle pas les API internes des sites. Il n'a pas de proxy par défaut, et s'il
devait en avoir besoin pour ne pas être bloqué, ce serait le signe que le volume est mal cadré. En entretien,
« un scraper qui respecte robots.txt avec rate limiting et backoff » est une meilleure phrase qu'« un pool de
proxys ».

## Le matching est le vrai problème

Comparer des prix est facile. Savoir que deux pages vendent le même produit est difficile : références qui
diffèrent, bundles, occasion, variantes. L'automatique propose, l'humain valide, et chaque correspondance
garde sa preuve. Et il y a deux façons d'être « comparable » : identique (même référence) et équivalent
(même classe fonctionnelle, comme deux pneus de même dimension). Cette distinction vient directement du
terrain, et c'est elle qui rend l'outil réutilisable hors tech.

## Des briques indépendantes, un seul projet

Chaque brique (collecte, moteur, sorties) est un module Java sans framework, utilisable seul, testé en
millisecondes. Spring n'apparaît que pour assembler. Un test d'architecture fait échouer le build si une
frontière est franchie. Pas plusieurs dépôts pour autant : pour une personne, c'est de la friction sans bénéfice.

## Décider en écrivant

Chaque choix structurant est un ADR, écrit au moment où il est pris, avec les options écartées et ce qui ferait
changer d'avis. Un ADR ne se réécrit pas : s'il devient faux, un nouveau le remplace. L'historique du
raisonnement vaut autant que la décision.
