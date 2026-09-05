# Architecture Decision Records

Chaque décision structurante du projet est consignée ici, au moment où elle est prise, dans un fichier numéroté
qui suit le [modèle](0000-modele.md). Un ADR ne se réécrit pas : si une décision change, un nouvel ADR la remplace
et l'ancien passe au statut « Remplacé par ». L'historique du raisonnement est aussi précieux que la décision.

| N°   | Titre                                                                                  | Statut  |
|------|----------------------------------------------------------------------------------------|---------|
| 0001 | [Monolithe modulaire en architecture hexagonale](0001-monolithe-modulaire-hexagonal.md) | Accepté |
| 0002 | [Java 21, Maven multi-modules et wrapper](0002-java-21-maven-wrapper.md)               | Accepté |
| 0003 | [Spring confiné aux modules d'application](0003-spring-confine-aux-modules-application.md) | Accepté |
| 0004 | [Collecte planifiée par GitHub Actions, pas de serveur permanent](0004-collecte-github-actions.md) | Accepté |
| 0005 | [PostgreSQL sur Supabase comme stockage et API de lecture](0005-supabase-stockage-et-api-lecture.md) | Accepté (à implémenter) |
| 0006 | [API pour les jeux, scraping léger pour le hardware, pas de proxy par défaut](0006-api-jeux-scraping-hardware.md) | Accepté |
| 0007 | [Chaîne d'extraction et archivage des pages brutes](0007-chaine-extraction-et-snapshots-bruts.md) | Accepté |
| 0008 | [Matching semi-automatique avec score et preuve](0008-matching-semi-automatique.md)     | Accepté (à implémenter) |
| 0009 | [Deux relations de matching : identité et équivalence](0009-identite-et-equivalence.md) | Accepté (à implémenter) |
| 0010 | [Sites déclarés en YAML avec échappatoire en code](0010-sites-declaratifs-yaml.md)      | Accepté |
| 0011 | [Pas de bus d'événements pour l'instant](0011-pas-de-bus-evenements.md)                | Accepté |
| 0012 | [Sortie paramétrable : le port PriceSink](0012-sorties-parametrables-price-sink.md)     | Accepté |
| 0013 | [Pas de pilotage de navigateur ni de camouflage : fournisseur externe pour l'usage pro](0013-fournisseur-externe-pour-usage-pro.md) | Accepté |
