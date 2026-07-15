package com.netflix.nebula.oss.settings

import com.netflix.nebula.SupportedGradleVersion
import nebula.test.dsl.*
import nebula.test.dsl.TestKitAssertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File

class NebulaSettingsPluginTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `plugin configures develocity`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
                configurationCache(true)
            }
            settings {
                plugins {
                    id("com.netflix.nebula.oss.settings")
                }
            }
        }
        val result = runner.run("buildEnvironment")
        assertThat(result.output)
            .`as`("scan upload disabled by default")
            .contains("The Gradle Terms of Use have not been agreed to.")
    }

    @Test
    fun `scan opt-in`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
                configurationCache(true)
            }
            properties {
                property("nebula.buildScanTerms", "true")
            }
            settings {
                plugins {
                    id("com.netflix.nebula.oss.settings")
                }
            }
        }
        val result = runner.run("buildEnvironment")
        assertThat(result.output)
            .`as`("plugin allows scan opt-in")
            .contains("Publishing Build Scan to Develocity...")
    }

    @Test
    fun `test resolve monoproject`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
                configurationCache(true)
            }
            settings {
                plugins {
                    id("com.netflix.nebula.oss.settings")
                }
            }
        }
        val result = runner.run("resolve")
        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
        assertThat(result.task(":dependencies")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":resolve")).hasOutcome(TaskOutcome.SUCCESS)
    }

    @Test
    fun `test toolchains`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
                configurationCache(true)
            }
            settings {
                plugins {
                    id("com.netflix.nebula.oss.settings")
                }
            }
            rootProject {
                plugins {
                    id("java")

                }
                javaToolchain(12) // a version that will trigger provisioning
                src {
                    main {
                        java("Main.java", "public class Main { public static void main(String[] args) {} }")
                    }
                }
            }
        }
        val result = runner.run("build")
        assertThat(result)
            .hasNoProblemsReport()
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `test resolve multiproject`(gradle: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
                configurationCache(true)
            }
            settings {
                plugins {
                    id("com.netflix.nebula.oss.settings")
                }
            }
            subProject("sub1")
            subProject("sub2")
        }
        val result = runner.run("resolve") {
            withGradle(gradle.version)
        }
        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
        assertThat(result.task(":dependencies")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":sub1:dependencies")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":sub2:dependencies")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":resolve")).hasOutcome(TaskOutcome.SUCCESS)
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `test locking multiproject`(gradle: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
                configurationCache(true)
            }
            settings {
                plugins {
                    id("com.netflix.nebula.oss.settings")
                }
            }
            subProject("sub1"){
                plugins {
                    java()
                }
            }
            subProject("sub2"){
                plugins {
                    java()
                }
            }
        }
        val result = runner.run("resolve", "--write-locks") {
            withGradle(gradle.version)
        }
        assertThat(result)
            .hasNoProblemsReport()
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
        assertThat(projectDir.resolve("sub1/gradle.lockfile"))
            .exists()
            .content()
            .contains("empty=annotationProcessor,compileClasspath,runtimeClasspath,testAnnotationProcessor,testCompileClasspath,testRuntimeClasspath")
        assertThat(projectDir.resolve("sub2/gradle.lockfile"))
            .exists()
            .content()
            .contains("empty=annotationProcessor,compileClasspath,runtimeClasspath,testAnnotationProcessor,testCompileClasspath,testRuntimeClasspath")
    }
}