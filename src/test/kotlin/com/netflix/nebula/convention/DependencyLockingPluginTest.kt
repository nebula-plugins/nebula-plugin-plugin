package com.netflix.nebula.convention

import com.netflix.nebula.SupportedGradleVersion
import nebula.test.dsl.*
import nebula.test.dsl.TestKitAssertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File

class DependencyLockingPluginTest {
    @TempDir
    lateinit var projectDir: File

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `test multiproject no root`(gradle: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
                configurationCache(true)
            }
            subProject("sub1") {
                plugins {
                    java()
                    id("com.netflix.nebula.locks")
                }
                repositories{
                    mavenCentral()
                }
                dependencies("""implementation("org.slf4j:slf4j-api:2.0.18")""")
            }
        }
        val result = runner.run(":sub1:dependencies", "--write-locks"){
            withGradle(gradle.version)
        }
        assertThat(result)
            .hasNoProblemsReport()
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
        val lockFile = projectDir.resolve("sub1/gradle.lockfile")
        assertThat(lockFile)
            .exists()
            .content()
            .contains("org.slf4j:slf4j-api")
            .contains("compileClasspath")
            .contains("runtimeClasspath")
            .contains("testCompileClasspath")
            .contains("testRuntimeClasspath")
            .contains("empty=annotationProcessor,testAnnotationProcessor")
    }

    @Test
    fun `test archrules integration`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
                configurationCache(true)
            }
            subProject("sub1") {
                plugins {
                    java()
                    id("com.netflix.nebula.archrules.runner")
                    id("com.netflix.nebula.locks")
                }
                repositories{
                    mavenCentral()
                }
                dependencies(
                    """implementation("org.slf4j:slf4j-api:2.0.18")""",
                    """archRules("com.netflix.nebula:archrules-deprecation:1.0.2")"""
                )
            }
        }
        val result = runner.run(":sub1:dependencies", "--write-locks")
        assertThat(result)
            .hasNoProblemsReport()
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
        val lockFile = projectDir.resolve("sub1/gradle.lockfile")
        assertThat(lockFile)
            .exists()
            .content()
            .contains("org.slf4j:slf4j-api")
            .contains("mainArchRulesRuntime")
            .contains("testArchRulesRuntime")
            .contains("com.netflix.nebula:archrules-deprecation:1.0.2=archRules,mainArchRulesRuntime,testArchRulesRuntime")
            .contains("empty=annotationProcessor,testAnnotationProcessor")
    }
}