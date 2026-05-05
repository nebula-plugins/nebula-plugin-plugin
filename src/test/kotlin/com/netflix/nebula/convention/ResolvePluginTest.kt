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

class ResolvePluginTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `test multiproject no root`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
                configurationCache(true)
            }
            subProject("sub1") {
                plugins {
                    id("com.netflix.nebula.resolve")
                }
            }
            subProject("sub2") {
                plugins {
                    id("com.netflix.nebula.resolve")
                }
            }
        }
        val result = runner.run("resolve")
        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
        assertThat(result.task(":sub1:dependencies"))
            .`as`("dependencies task of sub1 is run")
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":sub2:dependencies"))
            .`as`("dependencies task of sub1 is run")
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":dependencies"))
            .`as`("root dependencies task is not run")
            .isNull()
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `test monoproject`(gradle: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
                configurationCache(true)
            }
            rootProject {
                plugins {
                    id("com.netflix.nebula.resolve")
                }
            }
        }
        val result = runner.run("resolve") {
            withGradle(gradle.version)
        }
        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
        assertThat(result.task(":dependencies"))
            .`as`("dependencies task of project is run")
            .hasOutcome(TaskOutcome.SUCCESS)
    }

    @Test
    fun `test multiproject with root application works`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
                configurationCache(true)
            }
            rootProject {
                plugins {
                    id("com.netflix.nebula.resolve")
                }
            }
            subProject("sub1") {
                plugins {
                    id("com.netflix.nebula.resolve")
                }
            }
            subProject("sub2") {
                plugins {
                    id("com.netflix.nebula.resolve")
                }
            }
        }
        val result = runner.run("resolve")
        assertThat(result)
            .hasNoDeprecationWarnings()
            .hasNoMutableStateWarnings()
        assertThat(result.task(":sub1:dependencies"))
            .`as`("dependencies task of sub1 is run")
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":sub2:dependencies"))
            .`as`("dependencies task of sub1 is run")
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":dependencies"))
            .`as`("root dependencies task is run")
            .hasOutcome(TaskOutcome.SUCCESS)
    }
}