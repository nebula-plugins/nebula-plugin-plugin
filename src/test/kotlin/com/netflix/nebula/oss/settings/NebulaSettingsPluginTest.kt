package com.netflix.nebula.oss.settings

import nebula.test.dsl.TestKitAssertions.assertThat
import nebula.test.dsl.plugins
import nebula.test.dsl.properties
import nebula.test.dsl.settings
import nebula.test.dsl.testProject
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class NebulaSettingsPluginTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `plugin configures develocity`() {
        val runner = testProject(projectDir) {
            properties{
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
            properties{
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
            properties{
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
    fun `test resolve multiproject`() {
        val runner = testProject(projectDir) {
            properties{
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
        val result = runner.run("resolve")
        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
        assertThat(result.task(":dependencies")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":sub1:dependencies")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":sub2:dependencies")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":resolve")).hasOutcome(TaskOutcome.SUCCESS)
    }
}