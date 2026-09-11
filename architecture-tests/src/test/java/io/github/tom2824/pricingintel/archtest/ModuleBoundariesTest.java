package io.github.tom2824.pricingintel.archtest;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Les frontières entre modules, exprimées en règles exécutables. Si quelqu'un importe Spring dans le domaine
 * ou fait dépendre le scraper du client HTTP concret, le build échoue ici avec un message explicite.
 * Ces règles sont la version vérifiée des ADR 0001, 0003, 0018 et 0022.
 */
@AnalyzeClasses(packages = ModuleBoundariesTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundariesTest {

    static final String ROOT = "io.github.tom2824.pricingintel";
    static final String DOMAIN = ROOT + ".domain..";
    static final String CORE = ROOT + ".collector..";
    static final String PRICING = ROOT + ".pricing..";
    static final String HTTP = ROOT + ".http..";
    static final String SCRAPER = ROOT + ".scraper..";
    static final String SINK = ROOT + ".sink..";
    static final String PERSISTENCE = ROOT + ".persistence..";
    static final String BATCH = ROOT + ".batch..";

    @ArchTest
    static final ArchRule domain_depends_only_on_the_jdk = classes()
            .that().resideInAPackage(DOMAIN)
            .should().onlyDependOnClassesThat().resideInAnyPackage(DOMAIN, "java..");

    @ArchTest
    static final ArchRule core_depends_only_on_the_domain_and_the_jdk = classes()
            .that().resideInAPackage(CORE)
            .should().onlyDependOnClassesThat().resideInAnyPackage(CORE, DOMAIN, "java..");

    /** ADR 0022 : le moteur de prix est du Java pur, publiable comme bibliothèque. */
    @ArchTest
    static final ArchRule pricing_engine_depends_only_on_the_domain_and_the_jdk = classes()
            .that().resideInAPackage(PRICING)
            .should().onlyDependOnClassesThat().resideInAnyPackage(PRICING, DOMAIN, "java..");

    @ArchTest
    static final ArchRule http_adapter_ignores_its_siblings_and_the_application = noClasses()
            .that().resideInAPackage(HTTP)
            .should().dependOnClassesThat().resideInAnyPackage(SCRAPER, SINK, PERSISTENCE, PRICING, BATCH);

    @ArchTest
    static final ArchRule scraper_uses_the_fetcher_port_never_the_http_module = noClasses()
            .that().resideInAPackage(SCRAPER)
            .should().dependOnClassesThat().resideInAnyPackage(HTTP, SINK, PERSISTENCE, PRICING, BATCH);

    @ArchTest
    static final ArchRule sinks_ignore_their_siblings_and_the_application = noClasses()
            .that().resideInAPackage(SINK)
            .should().dependOnClassesThat().resideInAnyPackage(HTTP, SCRAPER, PERSISTENCE, PRICING, BATCH);

    @ArchTest
    static final ArchRule persistence_ignores_its_siblings_and_the_application = noClasses()
            .that().resideInAPackage(PERSISTENCE)
            .should().dependOnClassesThat().resideInAnyPackage(HTTP, SCRAPER, SINK, BATCH);

    /** ADR 0003 amendé par 0018 : Spring et Jakarta seulement dans l'application et l'adaptateur de persistance. */
    @ArchTest
    static final ArchRule only_application_and_persistence_use_spring = noClasses()
            .that().resideOutsideOfPackages(BATCH, PERSISTENCE)
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..");

    /**
     * L'application ne connaît la persistance que dans son assemblage (classe principale et configurations) :
     * les runners et les propriétés parlent aux ports (ListingProvider, PriceSink, MarketOffers, PriceDecisionStore,
     * RecommendationSink, Retention, CatalogueImport), jamais aux classes PostgreSQL. C'est ce qui rend
     * l'adaptateur remplaçable.
     */
    @ArchTest
    static final ArchRule application_reaches_persistence_only_through_its_assembly = noClasses()
            .that().resideInAPackage(BATCH)
            .and().haveSimpleNameNotEndingWith("Configuration").and().haveSimpleNameNotEndingWith("Application")
            .should().dependOnClassesThat().resideInAPackage(PERSISTENCE);

    /** JPA est un détail de l'adaptateur de persistance, pas un vocabulaire partagé. */
    @ArchTest
    static final ArchRule jpa_stays_inside_the_persistence_adapter = noClasses()
            .that().resideOutsideOfPackages(PERSISTENCE)
            .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..");

    /** « java.. » autorise java.sql : on ferme explicitement cette porte au domaine, au cœur et au moteur. */
    @ArchTest
    static final ArchRule domain_core_and_pricing_ignore_jdbc = noClasses()
            .that().resideInAnyPackage(DOMAIN, CORE, PRICING)
            .should().dependOnClassesThat().resideInAnyPackage("java.sql..", "javax.sql..");

    @ArchTest
    static final ArchRule modules_are_free_of_cycles = slices()
            .matching(ROOT + ".(*)..")
            .should().beFreeOfCycles();
}
