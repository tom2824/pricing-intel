# 0004. Collecte planifiée par GitHub Actions, pas de serveur permanent

**Date** : 2026-09-05
**Statut** : Accepté

## Contexte

Un historique de prix n'a de valeur que s'il est continu. La collecte doit tourner tous les jours, sur une
machine toujours allumée, sans coût au démarrage du projet.

## Options envisagées

1. **Sur le poste du développeur (tâche planifiée Windows).** Gratuit, mais des trous dès que la machine est
   éteinte ou en veille. Incompatible avec l'objectif.
2. **Spring Boot avec `@Scheduled` sur un hébergement gratuit (Render, Koyeb).** Les offres gratuites mettent
   l'application en veille sans trafic, et un scheduler en veille ne se réveille pas. Ça marche mal.
3. **Un VPS à quelques euros par mois.** Le plus proche d'un déploiement d'entreprise (Docker, nginx, logs),
   mais un coût et de l'entretien dès le premier jour, avant même d'avoir des données.
4. **GitHub Actions en cron.** Un workflow lance l'application en mode batch chaque jour : elle collecte,
   écrit, se termine. Gratuit pour un dépôt public, logs de chaque exécution visibles, aucun serveur à
   maintenir. Le cron peut avoir quelques minutes de retard, sans importance pour un relevé quotidien.

## Décision

Option 4. L'application est conçue comme un batch : elle démarre, fait une collecte, renvoie un code de sortie
(0 si au moins un relevé, 1 sinon) et s'arrête. Le stockage durable est externe (voir ADR 0005) ; en attendant,
les relevés sont publiés comme artefacts du workflow.

## Conséquences

- Pas de `@Scheduled` dans le code : la planification est la responsabilité de l'infrastructure.
- L'IP sortante est celle des runners GitHub, partagée avec beaucoup d'autres : une raison de plus de rester
  très en dessous de tout seuil de blocage (voir ADR 0006).
- Migration possible vers un VPS plus tard sans changer le code : le même jar, lancé par cron, avec le même
  fichier de configuration. Ce serait le moment de revoir cet ADR.
