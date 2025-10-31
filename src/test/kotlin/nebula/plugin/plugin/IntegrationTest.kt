package nebula.plugin.plugin

import nebula.test.dsl.*
import nebula.test.dsl.TestKitAssertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class IntegrationTest {
    @TempDir
    lateinit var projectDir: File

    @TempDir
    lateinit var remoteGitDir: File

    private fun TestProjectBuilder.sampleMultiProjectSetup() {
        rootProject {
            plugins {
                id("com.netflix.nebula.root")
            }
            rawBuildScript(DISABLE_MAVEN_CENTRAL_TASKS)
        }
        subProject("library") {
            plugins {
                id("com.netflix.nebula.library")
            }
            rawBuildScript(
                """
$DISABLE_PUBLISH_TASKS
""".trimIndent()
            )
            mockSign()
            src {
                main {
                    java("example/Main.java", SAMPLE_JAVA_MAIN_CLASS)
                }
            }
        }
        subProject("plugin") {
            plugins {
                id("com.netflix.nebula.plugin-plugin")
            }
            rawBuildScript(
                """
$DISABLE_PUBLISH_TASKS
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
            mockSign()
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
                sampleMultiProjectSetup()
            }
        }
        val result = runner.run(
            "candidate",
            "-Prelease.useLastTag=true",
            "-PnetflixOss.username=user",
            "-PnetflixOss.password=password",
            "--stacktrace"
        )

        assertThat(result.task(":generatePomFileForNebulaPublication")).isNull()

        // library publication
        assertThat(result.task(":library:javadoc")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":library:generatePomFileForNebulaPublication"))
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":library:publishNebulaPublicationToNetflixOSSRepository"))
            .hasOutcome(TaskOutcome.SKIPPED)

        // plugin publication
        assertThat(result.task(":plugin:validatePlugins")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":plugin:generatePomFileForNebulaPublication"))
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":plugin:publishExamplePluginMarkerMavenPublicationToNetflixOSSRepository")).hasOutcome(TaskOutcome.SKIPPED)
        assertThat(result.task(":plugin:publishNebulaPublicationToNetflixOSSRepository")).hasOutcome(TaskOutcome.SKIPPED)
        assertThat(result.task(":plugin:publishPlugins")).hasOutcome(TaskOutcome.SKIPPED)

        // global
        assertThat(result.task(":postRelease")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":candidate")).hasOutcome(TaskOutcome.SUCCESS)

        val pluginPom = projectDir.resolve("plugin/build/publications/nebula/pom-default.xml")
        assertThat(pluginPom)
            .exists()
            .content()
            .contains("""<groupId>com.netflix.nebula</groupId>""")
        val libraryPom = projectDir.resolve("library/build/publications/nebula/pom-default.xml")
        assertThat(libraryPom)
            .exists()
            .content()
            .contains("""<groupId>com.netflix.nebula</groupId>""")
    }

    @Test
    fun `test final`() {
        val runner = withGitTag(projectDir, remoteGitDir, "v0.0.1") {
            testProject(projectDir) {
                sampleMultiProjectSetup()
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

        // library publication
        assertThat(result.output).contains("library:generatePomFileForNebulaPublication SKIPPED")
        assertThat(result.output).contains("library:publishNebulaPublicationToSonatypeRepository SKIPPED")

        // plugin publication
        assertThat(result.output).contains("plugin:generatePomFileForNebulaPublication SKIPPED")
        assertThat(result.output).contains("plugin:publishExamplePluginMarkerMavenPublicationToSonatypeRepository SKIPPED")
        assertThat(result.output).contains("plugin:publishNebulaPublicationToSonatypeRepository SKIPPED")
        assertThat(result.output).contains("plugin:publishPlugins SKIPPED")

        // global
        assertThat(result.output).contains(":publishNebulaPublicationToSonatypeRepository SKIPPED")
        assertThat(result.output).contains(":closeSonatypeStagingRepository SKIPPED")
        assertThat(result.output).contains(":releaseSonatypeStagingRepository SKIPPED")
        assertThat(result.output).contains(":closeAndReleaseSonatypeStagingRepository SKIPPED")
        assertThat(result.output).contains(":final SKIPPED")
    }
}