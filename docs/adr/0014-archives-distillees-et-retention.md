# 0014. Archives distillées (JSON embarqué + Markdown) et rétention

**Date** : 2026-09-05
**Statut** : Accepté

## Contexte

L'ADR 0007 archive chaque page brute pour pouvoir rejouer une extraction et prouver ce qu'un site affichait.
Une fiche produit pèse 100 à 200 Ko de HTML, dont l'essentiel est de la navigation, du script et du style.
Sur des mois de collecte quotidienne, c'est le premier poste de volume, pour une valeur qui se concentre dans
quelques kilo-octets. Retour d'expérience d'un outil de veille en entreprise : ne garder qu'une version
« accessibilité » de la page, bien plus légère, suffisait.

## Options envisagées

1. **HTML complet, rétention courte.** Rejouable à 100 %, mais on perd toute preuve au-delà de quelques jours.
2. **Version distillée seulement.** Les blocs JSON embarqués (JSON-LD, `__NEXT_DATA__`, variables globales)
   conservés intacts, plus le contenu visible converti en Markdown dans l'esprit de l'arbre d'accessibilité :
   titres, texte, listes, tableaux, définitions, textes alternatifs, libellés ARIA ; sans navigation, en-tête,
   pied de page, scripts, styles, formulaires. Dix à trente fois plus léger, lisible tel quel. On perd le DOM,
   donc la possibilité de rejouer un extracteur CSS sur le passé.
3. **Les deux, avec des rétentions différentes.** Distillé pendant des mois, HTML complet quelques jours et
   seulement quand on débogue.

## Décision

Option 3, distillé activé par défaut (rétention 180 jours), HTML complet désactivé par défaut (rétention 7 jours
quand il est activé). Les deux formats vivent sous le même dossier, dans des fichiers dont le nom porte
l'horodatage ; la purge se fait sur ce nom, à la fin de chaque collecte, et n'échoue jamais bruyamment.

Sans navigateur, le vrai arbre ARIA n'est pas disponible : la version distillée s'en approche par la sémantique
HTML (`main`, `article`, rôles, `alt`, `aria-label`). C'est un convertisseur maison sur Jsoup, d'une centaine de
lignes, plutôt qu'une bibliothèque générique : on choisit ce qu'on garde et ce qu'on jette, et il se teste sur
nos fixtures.

Les relevés de prix eux-mêmes ne sont pas concernés : quelques dizaines d'octets par relevé, ils se gardent
au moins un an, car les courbes longues (soldes, Black Friday) sont ce qui donne sa valeur à l'historique.

## Conséquences

- Perdre la capacité de rejouer un extracteur CSS au-delà de sept jours est accepté : c'est la méthode de dernier
  recours, et le Markdown permet encore de vérifier à l'œil ce que la page affichait.
- Les extracteurs JSON restent rejouables sur toute la rétention distillée.
- Le format distillé est un document JSON gzippé par relevé : il pourra être chargé tel quel dans Postgres
  (colonne JSONB) quand le stockage durable arrivera.
