package com.hotelopai.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.RestController

class ArchitectureConsistencyTest {
    private val importedClasses: JavaClasses = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.hotelopai")

    @Test
    fun `feature modules should be free of cyclic dependencies`() {
        slices()
            .matching("com.hotelopai.(*)..")
            .should()
            .beFreeOfCycles()
            .check(importedClasses)
    }

    @Test
    fun `controllers should not depend on repositories`() {
        noClasses()
            .that()
            .areAnnotatedWith(RestController::class.java)
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Repository")
            .check(importedClasses)
    }

    @Test
    fun `application should not depend on infrastructure`() {
        noClasses()
            .that()
            .resideInAPackage("com.hotelopai..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.hotelopai..infrastructure..")
            .check(importedClasses)
    }

    @Test
    fun `domain should remain free of framework and adapter dependencies`() {
        noClasses()
            .that()
            .resideInAPackage("com.hotelopai..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.hotelopai..api..",
                "com.hotelopai..application..",
                "com.hotelopai..infrastructure..",
                "com.hotelopai.integration..",
                "org.springframework..",
                "jakarta.."
            )
            .check(importedClasses)
    }

    @Test
    fun `assistant feature should not depend on foreign infrastructure`() {
        noClasses()
            .that()
            .resideInAPackage("com.hotelopai.assistant..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.hotelopai.task.infrastructure..",
                "com.hotelopai.auth.infrastructure..",
                "com.hotelopai.hotel.infrastructure..",
                "com.hotelopai.employee.infrastructure.."
            )
            .check(importedClasses)
    }

    @Test
    fun `task feature should not depend on assistant infrastructure`() {
        noClasses()
            .that()
            .resideInAPackage("com.hotelopai.task..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.hotelopai.assistant.infrastructure..")
            .check(importedClasses)
    }

    @Test
    fun `auth feature should not depend on task infrastructure`() {
        noClasses()
            .that()
            .resideInAPackage("com.hotelopai.auth..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.hotelopai.task.infrastructure..",
                "com.hotelopai.assistant.infrastructure.."
            )
            .check(importedClasses)
    }

    @Test
    fun `workflow feature should remain isolated from business adapters`() {
        noClasses()
            .that()
            .resideInAPackage("com.hotelopai.workflow..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.hotelopai.assistant..",
                "com.hotelopai.task.api..",
                "com.hotelopai.task.infrastructure..",
                "com.hotelopai.auth..",
                "com.hotelopai.hotel..",
                "com.hotelopai.employee.."
            )
            .check(importedClasses)
    }
}
