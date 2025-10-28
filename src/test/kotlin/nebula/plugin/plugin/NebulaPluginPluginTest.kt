package nebula.plugin.plugin

import nebula.test.dsl.*
import nebula.test.dsl.TestKitAssertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class NebulaPluginPluginTest {
    @TempDir
    lateinit var projectDir: File

    @TempDir
    lateinit var remoteGitDir: File

    private fun TestProjectBuilder.sampleSinglePluginSetup() {
        rootProject {
            plugins {
                id("com.netflix.nebula.plugin-plugin")
            }
            rawBuildScript(
                """
$DISABLE_PUBLISH_TASKS
$DISABLE_MAVEN_CENTRAL_TASKS
gradlePlugin {
    plugins {
        create("example") {
            id = "com.netflix.example"
            displayName = "example"
            description = "S"
            implementationClass = "example.MyPlugin"
            tags.set(listOf("nebula"))
        }
    }
}
"""
            )
            src {
                main {
                    java("example/MyPlugin.java", SAMPLE_JAVA_PLUGIN)
                }
            }
        }
    }

    @Test
    fun `test candidate`() {
        val runner = withGitTag(projectDir, remoteGitDir, "v0.0.1-rc.1") {
            testProject(projectDir) {
                sampleSinglePluginSetup()
            }
        }
        val result = runner.run(
            "candidate",
            "-Prelease.useLastTag=true",
            "-PnetflixOss.username=user",
            "-PnetflixOss.password=password",
            "--stacktrace",
            "--info"
        )

        assertThat(result.task(":javadoc")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":signPluginMavenPublication"))
            .`as`("fails due to missing signing key")
            .hasOutcome(TaskOutcome.SKIPPED)
        assertThat(result.task(":signExamplePluginMarkerMavenPublication"))
            .`as`("fails due to missing signing key")
            .hasOutcome(TaskOutcome.SKIPPED)
        assertThat(result.task(":validatePlugins")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":publishExamplePluginMarkerMavenPublicationToNetflixOSSRepository"))
            .hasOutcome(TaskOutcome.SKIPPED)
        assertThat(result.output)
            .`as` { "ensure marker publish is only skipped b/c test setup, not because it is disabled" }
            .doesNotContain("Skipping task ':publishExamplePluginMarkerMavenPublicationToNetflixOSSRepository' as task onlyIf 'Task is enabled' is false.")
        assertThat(result.task(":publishNebulaPublicationToNetflixOSSRepository"))
            .hasOutcome(TaskOutcome.SKIPPED)
        assertThat(result.task(":postRelease")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":candidate")).hasOutcome(TaskOutcome.SUCCESS)
    }

    @Test
    fun `test final`() {
        val runner = withGitTag(projectDir, remoteGitDir, "v0.0.1") {
            testProject(projectDir) {
                sampleSinglePluginSetup()
            }
        }
        val result = runner.run(
            "final",
            "-Prelease.useLastTag=true",
            "-PnetflixOss.username=user",
            "-PnetflixOss.password=password",
            "-Psonatype.username=user",
            "-Psonatype.password=password",
            "--dry-run",
            "--stacktrace"
        )
        assertThat(result.output).contains(":initializeSonatypeStagingRepository SKIPPED")
        assertThat(result.output).contains(":validatePlugins SKIPPED")
        assertThat(result.output).contains(":publishNebulaPublicationToSonatypeRepository SKIPPED")
        assertThat(result.output).contains(":publishExamplePluginMarkerMavenPublicationToSonatypeRepository SKIPPED")
        assertThat(result.output).contains(":publishNebulaPublicationToNetflixOSSRepository SKIPPED")
        assertThat(result.output).contains(":closeSonatypeStagingRepository SKIPPED")
        assertThat(result.output).contains(":releaseSonatypeStagingRepository SKIPPED")
        assertThat(result.output).contains(":closeAndReleaseSonatypeStagingRepository SKIPPED")
        assertThat(result.output).contains(":final SKIPPED")
    }
}