package com.locgym.arquitetura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.locgym.LocGymApplication;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Regras de arquitetura válidas para todos os módulos: fronteiras do Spring Modulith
 * e camadas da Clean Architecture (dominio → aplicacao → infraestrutura/web).
 */
class ArquiteturaTest {

    static final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.locgym");

    @Test
    void modulosRespeitamFronteiras() {
        ApplicationModules.of(LocGymApplication.class).verify();
    }

    @Test
    void dominioNaoDependeDeFramework() {
        noClasses().that().resideInAPackage("..dominio..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "com.fasterxml..", "tools.jackson..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void dominioNaoDependeDeOutrasCamadas() {
        noClasses().that().resideInAPackage("..dominio..")
                .should().dependOnClassesThat().resideInAnyPackage("..aplicacao..", "..infraestrutura..", "..web..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void aplicacaoNaoDependeDeInfraOuWeb() {
        noClasses().that().resideInAPackage("..aplicacao..")
                .should().dependOnClassesThat().resideInAnyPackage("..infraestrutura..", "..web..")
                .allowEmptyShould(true)
                .check(classes);
    }
}
