# 0003. Spring confiné aux modules d'application

**Date** : 2026-09-05
**Statut** : Accepté, amendé par [0018](0018-persistance-spring-data-jpa-flyway.md) : les adaptateurs de persistance peuvent utiliser Spring

## Contexte

Spring Boot est la stack visée (CV, offres d'emploi) et sera indispensable pour l'API de lecture. Mais un
framework qui s'infiltre partout rend les briques inutilisables hors de lui, et les tests lents.

## Options envisagées

1. **Spring partout.** Beans, `@Component`, `@Scheduled` dans chaque module. Confortable, mais le moteur de
   stratégies ne peut plus être utilisé sans contexte Spring, et chaque test unitaire devient un test
   d'intégration.
2. **Pas de Spring du tout.** Cohérent, mais on perd la configuration externalisée, la validation, et surtout
   la lisibilité du projet pour quelqu'un qui recrute un profil Spring.
3. **Spring uniquement dans les modules `app-*`.** Les briques sont du Java pur avec des constructeurs
   explicites. Le module d'application lit la configuration (`@ConfigurationProperties`) et instancie les
   briques dans des `@Bean`. Spring joue son rôle de colle, pas de squelette.

## Décision

Option 3. Règle vérifiée par ArchUnit : aucune classe hors de `..batch..` (et des futurs `app-*`) ne dépend
de `org.springframework..` ni de `jakarta..`.

## Conséquences

- Les briques exposent des constructeurs avec toutes leurs dépendances : c'est plus verbeux qu'une injection
  par annotation, mais chaque dépendance est visible et remplaçable par un faux dans les tests.
- Les modules d'application sont petits et sans logique : configuration, assemblage, point d'entrée.
- Le jour où une API REST arrive (`app-web`), elle réutilise les mêmes briques avec un autre assemblage.
