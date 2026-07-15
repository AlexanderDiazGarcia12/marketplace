package com.ecommerce.marketplace.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the hexagonal boundary at build time: {@code domain} and {@code application} must stay
 * framework-free, and any concrete {@code application.service} must be wired via {@code @Bean} in
 * {@code infrastructure.config.SpringDependencyInjectionConfig} rather than discovered through
 * {@code @Service}/{@code @Component} component-scanning.
 *
 * <p>ArchUnit analyzes compiled bytecode, so an <em>unused</em> import of a forbidden package does
 * not trip these rules — only a real dependency (a used type, a call, an annotation) does, which
 * is the realistic violation worth guarding against.</p>
 */
class HexagonalArchitectureTest {

    private static final String DOMAIN_PACKAGE = "com.ecommerce.marketplace.domain..";
    private static final String APPLICATION_PACKAGE = "com.ecommerce.marketplace.application..";

    private static final String[] FORBIDDEN_PACKAGES = {
            "org.springframework..",
            "jakarta..",
            "com.fasterxml.jackson..",
            "tools.jackson..",
            "org.hibernate..",
            "org.apache.kafka..",
            "org.apache.commons.csv..",
            "lombok.."
    };

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.ecommerce.marketplace");
    }

    @Test
    void domainMustNotDependOnFrameworkPackages() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(DOMAIN_PACKAGE)
                .should().dependOnClassesThat().resideInAnyPackage(FORBIDDEN_PACKAGES)
                .because("the domain layer must be plain Java + Vavr, with zero Spring/Jakarta/Jackson/Hibernate dependency");

        rule.check(classes);
    }

    @Test
    void applicationMustNotDependOnFrameworkPackages() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(APPLICATION_PACKAGE)
                .should().dependOnClassesThat().resideInAnyPackage(FORBIDDEN_PACKAGES)
                .because("the application layer's ports and services must stay framework-free; wiring belongs to infrastructure.config");

        rule.check(classes);
    }

    @Test
    void domainMustNotUseSpringStereotypeAnnotations() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(DOMAIN_PACKAGE)
                .should().beAnnotatedWith("org.springframework.stereotype.Service")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Component")
                .because("domain classes are never Spring beans; they are plain objects instantiated by the application/infrastructure layers");

        rule.check(classes);
    }

    @Test
    void applicationMustNotUseSpringStereotypeAnnotations() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(APPLICATION_PACKAGE)
                .should().beAnnotatedWith("org.springframework.stereotype.Service")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Component")
                .because("application services must be registered via explicit @Bean methods in SpringDependencyInjectionConfig, not component-scanned");

        rule.check(classes);
    }
}
