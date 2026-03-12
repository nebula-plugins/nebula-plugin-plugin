package com.netflix.nebula.oss.settings

import nebula.test.dsl.plugins
import nebula.test.dsl.properties
import nebula.test.dsl.settings
import nebula.test.dsl.testProject
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class NebulaSettingsPluginTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `plugin configures develocity`() {
        val runner = testProject(projectDir) {
            settings {
                plugins {
                    id("com.netflix.nebula.oss.settings")
                }
            }
        }
        val result = runner.run("buildEnvironment")
        Assertions.assertThat(result.output)
            .`as`("scan upload disabled by default")
            .contains("The Gradle Terms of Use have not been agreed to.")
    }

    @Test
    fun `scan opt-in`() {
        val runner = testProject(projectDir) {
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
        Assertions.assertThat(result.output)
            .`as`("plugin allows scan opt-in")
            .contains("Publishing Build Scan to Develocity...")
    }
}