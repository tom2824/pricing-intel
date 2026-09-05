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
 * Ces règles sont la version vérifiée des ADR 0001, 0003 et 0018.
 */
@AnalyzeClasses(packages = ModuleBoundariesTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundariesTest {

    static final String ROOT = "io.github.tom2824.pricingintel";
    static final String DOMAIN = ROOT + ".domain..";
    static final String CORE = ROOT + ".collector..";
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

    @ArchTest
    static final ArchRule http_adapter_ignores_its_siblings_and_the_application = noClasses()
            .that().resideInAPackage(HTTP)
            .should().dependOnClassesThat().resideInAnyPackage(SCRAPER, SINK, PERSISTENCE, BATCH);

    @ArchTest
    static final ArchRule scraper_uses_the_fetcher_port_never_the_http_module = noClasses()
            .that().resideInAPackage(SCRAPER)
            .should().dependOnClassesThat().resideInAnyPackage(HTTP, SINK, PERSISTENCE, BATCH);

    @ArchTest
    static final ArchRule sinks_ignore_their_siblings_and_the_application = noClasses()
            .that().resideInAPackage(SINK)
            .should().dependOnClassesThat().resideInAnyPackage(HTTP, SCRAPER, PERSISTENCE, BATCH);

    @ArchTest
    static final ArchRule persistence_ignores_its_siblings_and_the_application = noClasses()
            .that().resideInAPackage(PERSISTENCE)
            .should().dependOnClassesThat().resideInAnyPackage(HTTP, SCRAPER, SINK, BATCH);

    /** ADR 0003 amendé par 0018 : Spring et Jakarta seulement dans l'application et l'adaptateur de persistance. */
    @ArchTest
    static final ArchRule only_application_and_persistence_use_spring = noClasses()
            .that().resideOutsideOfPackages(BATCH, PERSISTENCE)
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..");

    @ArchTest
    static final ArchRule modules_are_free_of_cycles = slices()
            .matching(ROOT + ".(*)..")
            .should().beFreeOfCycles();
}
