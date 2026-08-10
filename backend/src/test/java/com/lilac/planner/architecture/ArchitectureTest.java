package com.lilac.planner.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Mechanically enforces the port + adapters architecture, so it cannot erode silently in future changes.
 */
@AnalyzeClasses(packages = "com.lilac.planner", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** Domain models must stay persistence-free; mapping lives in the adapters. */
    @ArchTest
    static final ArchRule domainModelsCarryNoPersistenceAnnotations =
            noClasses().that().resideInAPackage("..model..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("jakarta.persistence..", "org.springframework.data..",
                            "software.amazon.awssdk..")
                    .because("domain models are mapped to adapter-specific entities inside each adapter package");

    /** The service layer talks to the PlannerStore port, never to a concrete adapter. */
    @ArchTest
    static final ArchRule servicesDependOnlyOnThePort =
            noClasses().that().resideInAPackage("..service..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..persistence.jpa..", "..persistence.neo4j..", "..persistence.dynamodb..")
                    .because("PlannerService must work unchanged with every storage backend");

    /** Controllers never reach into a concrete adapter either. */
    @ArchTest
    static final ArchRule controllersDependOnlyOnThePort =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..persistence.jpa..", "..persistence.neo4j..", "..persistence.dynamodb..");

    /**
     * Controllers delegate business logic to services. Direct PlannerStore use is
     * frozen to the one existing exception: UserController (plain CRUD passthrough).
     * New controllers must go through a service.
     */
    @ArchTest
    static final ArchRule controllersGoThroughServices =
            noClasses().that().resideInAPackage("..controller..")
                    .and().haveSimpleNameNotEndingWith("UserController")
                    .should().dependOnClassesThat().haveSimpleName("PlannerStore")
                    .because("controllers are thin routing layers; business logic belongs in services");

    /** Adapters must not depend on each other. */
    @ArchTest
    static final ArchRule jpaAdapterIsIsolated =
            noClasses().that().resideInAPackage("..persistence.jpa..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..persistence.neo4j..", "..persistence.dynamodb..");

    @ArchTest
    static final ArchRule neo4jAdapterIsIsolated =
            noClasses().that().resideInAPackage("..persistence.neo4j..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..persistence.jpa..", "..persistence.dynamodb..");

    @ArchTest
    static final ArchRule dynamoAdapterIsIsolated =
            noClasses().that().resideInAPackage("..persistence.dynamodb..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..persistence.jpa..", "..persistence.neo4j..");

    /** DTOs are a controller-facing concern; adapters must not use them. */
    @ArchTest
    static final ArchRule adaptersDoNotUseDtos =
            noClasses().that().resideInAPackage("..persistence..")
                    .should().dependOnClassesThat().resideInAPackage("..dto..");

    /** Nothing outside the persistence layer may touch adapter entity classes. */
    @ArchTest
    static final ArchRule adapterEntitiesStayInsidePersistence =
            classes().that().resideInAnyPackage("..persistence.jpa..", "..persistence.neo4j..", "..persistence.dynamodb..")
                    .should().onlyBeAccessed().byClassesThat()
                    .resideInAnyPackage("..persistence..", "..config..");
}
