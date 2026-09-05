# 0010. Sites déclarés en YAML avec échappatoire en code

**Date** : 2026-09-05
**Statut** : Accepté

## Contexte

Chaque site scrapé a ses particularités : où est le prix, comment est écrite la disponibilité, quel JSON est
embarqué. Il faut décider où vit cette connaissance.

## Options envisagées

1. **Une classe Java par site.** Puissant, testable, mais ajouter un site demande un développement et un
   redéploiement. Dans un contexte pro, c'est l'équipe métier qui veut ajouter un concurrent, pas l'équipe dev.
2. **Tout déclaratif.** Un fichier YAML par site : hôte, chaîne d'extracteurs et leurs paramètres. Ajouter un
   site, c'est ajouter un fichier. Mais certains sites sont assez tordus pour dépasser ce qu'une config peut
   exprimer.
3. **Déclaratif par défaut, code quand il le faut.** Les extracteurs standards (JSON-LD, JSON embarqué, CSS)
   sont paramétrés en YAML. Un site vraiment particulier peut recevoir un extracteur codé qui implémente la
   même interface `Extractor` et s'insère dans la chaîne.

## Décision

Option 3. Chargement strict : une propriété inconnue dans un YAML fait échouer le démarrage, pour qu'une faute
de frappe dans un sélecteur se voie tout de suite et pas après une nuit de collecte vide. Un site de repli
(`generic-jsonld`, JSON-LD seulement, sur n'importe quel hôte) permet d'essayer une nouvelle enseigne sans
rien déclarer ; il se désactive par configuration pour un usage strict.

## Conséquences

- Les fichiers `config/sites/*.yml` sont de la configuration, versionnée, relue par des non-développeurs.
- Les sélecteurs CSS livrés en exemple doivent être vérifiés sur de vraies pages : le JSON-LD passe en premier
  précisément parce que les sélecteurs sont les moins fiables.
- La syntaxe de configuration est une API : la changer casse les sites déclarés, elle doit évoluer avec soin.
