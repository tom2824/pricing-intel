# 0002. Java 21, Maven multi-modules et wrapper

**Date** : 2026-09-05
**Statut** : Accepté

## Contexte

Il faut choisir un langage, une version et un outil de build pour un projet censé durer, être lu par des
recruteurs, et tourner sur GitHub Actions sans installation préalable.

## Options envisagées

1. **Python pour la collecte, Java pour le reste.** Python est agréable pour le scraping, mais deux stacks
   pour un premier projet, c'est deux fois plus de choses à maintenir et à expliquer.
2. **Java 17 (LTS précédente).** Plus répandue en entreprise à ce jour, mais on perd les records scellés
   pleinement exploitables, le pattern matching de `switch` et les virtual threads.
3. **Java 21 (LTS) avec Maven.** Records immuables pour le domaine, interfaces scellées pour les stratégies et
   les politiques, `switch` exhaustif. Maven plutôt que Gradle : c'est ce que la majorité des équipes Java
   locales utilisent, le multi-module y est bien balisé, et le temps gagné sur l'outillage va dans le domaine.

## Décision

Java 21 et Maven multi-modules. Le dépôt embarque le Maven Wrapper (`mvnw`) : personne n'a besoin d'installer
Maven, et la version est la même partout (poste, CI). Les versions de dépendances sont gérées en un seul endroit
en important le BOM Spring Boot dans le `pom.xml` parent, même pour les modules qui n'utilisent pas Spring.

## Conséquences

- Le code utilise librement records, interfaces scellées et `switch` sur enum sans `default`.
- Un développeur qui clone le dépôt lance `./mvnw verify` et c'est tout.
- Le BOM Spring Boot dicte les versions de Jackson, SLF4J, JUnit et AssertJ : pas de conflit de versions
  entre modules, au prix d'une dépendance de gestion à un projet qu'on n'utilise pas partout.
