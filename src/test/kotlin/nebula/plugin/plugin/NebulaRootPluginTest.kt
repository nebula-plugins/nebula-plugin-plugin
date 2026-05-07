package nebula.plugin.plugin

import com.netflix.nebula.SupportedGradleVersion
import nebula.test.dsl.TestKitAssertions.assertThat
import nebula.test.dsl.plugins
import nebula.test.dsl.properties
import nebula.test.dsl.rootProject
import nebula.test.dsl.run
import nebula.test.dsl.subProject
import nebula.test.dsl.testProject
import nebula.test.dsl.withGradle
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File

class NebulaRootPluginTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `plugin sets group`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.netflix.nebula.root")
        assertThat(project.group).isEqualTo("com.netflix.nebula")
    }

    @Test
    fun `plugin applies archrules aggregate`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.netflix.nebula.root")
        assertThat(project.plugins.findPlugin("com.netflix.nebula.archrules.aggregate")).isNotNull
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `archrules aggregate`(gradle: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
                configurationCache(true)
            }
            rootProject {
                plugins {
                    id("com.netflix.nebula.root")
                }
            }
            subProject(":sub1") {
                plugins {
                    id("java")
                    id("com.netflix.nebula.library")
                }
            }
        }
        val result = runner.run("build", "--stacktrace") {
            withGradle(gradle.version)
        }
        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
        assertThat(result.task(":sub1:archRulesConsoleReport"))
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":archRulesAggregateMarkdownReport"))
            .`as`("aggregate reports do not run by default")
            .isNull()
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `archrules aggregate markdown`(gradle: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
                configurationCache(true)
            }
            rootProject {
                plugins {
                    id("com.netflix.nebula.root")
                }
            }
            subProject(":sub1") {
                plugins {
                    id("java")
                    id("com.netflix.nebula.library")
                }
            }
        }

        val result = runner.run("archRulesAggregateMarkdownReport") {
            forwardOutput()
            withGradle(gradle.version)
        }
        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
        assertThat(result.task(":archRulesAggregateMarkdownReport"))
            .hasOutcome(TaskOutcome.SUCCESS)
    }
}