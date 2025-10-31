package nebula.plugin.plugin

import nebula.plugin.publishing.expectPublication
import nebula.plugin.publishing.mockGradlePluginPortal
import nebula.test.dsl.*
import nebula.test.dsl.TestKitAssertions.assertThat
import nebula.test.dsl.run
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockserver.configuration.ConfigurationProperties
import org.mockserver.integration.ClientAndServer
import java.io.File
import java.net.ServerSocket

internal class NebulaPluginPluginTest {
    @TempDir
    lateinit var projectDir: File

    @TempDir
    lateinit var remoteGitDir: File
    private lateinit var artifactory: ClientAndServer
    private var port: Int = 0

    @BeforeEach
    fun startArtifactory() {
        port = try {
            ServerSocket(0).use { socket ->
                socket.getLocalPort()
            }
        } catch (_: Exception) {
            8080
        }
        artifactory = ClientAndServer.startClientAndServer(port)
        ConfigurationProperties.logLevel("ERROR")
    }

    @AfterEach
    fun stopArtifactory() {
        artifactory.stop()
    }

    private fun TestProjectBuilder.sampleSinglePluginSetup() {
        settings {
            name("test")
        }
        rootProject {
            plugins {
                id("com.netflix.nebula.plugin-plugin")
            }
            mockSign()
            nebulaOssPublishing("http://localhost:$port")
            rawBuildScript(
                """
description = "test"
contacts {
    addPerson("nebula-plugins-oss@netflix.com") {
        moniker = "Nebula Plugins Maintainers"
        github =  "nebula-plugins"
    }
}
gradlePlugin {
    plugins {
        create("example") {
            id = "com.netflix.nebula.example"
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
        val version = "0.0.1-rc.1"
        val runner = withGitTag(projectDir, remoteGitDir, "v$version") {
            testProject(projectDir) {
                sampleSinglePluginSetup()
            }
        }
        val verifications = artifactory.expectPublication(
            "netflix-oss",
            "com.netflix.nebula",
            "test",
            version
        ) {
            withArtifact("jar")
            withArtifact("sources", "jar")
            withArtifact("javadoc", "jar")
            withGradleModuleMetadata()
        }

        val markerVerifications = artifactory.expectPublication(
            "netflix-oss",
            "com.netflix.nebula.example",
            "com.netflix.nebula.example.gradle.plugin",
            version
        )

        val result = runner.run(
            "candidate",
            "-Prelease.useLastTag=true",
            "-PnetflixOss.username=user",
            "-PnetflixOss.password=password",
            "--stacktrace"
        )

        assertThat(result.task(":javadoc")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":generatePomFileForNebulaPublication"))
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":signPluginMavenPublication"))
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":signExamplePluginMarkerMavenPublication"))
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":validatePlugins")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":publishExamplePluginMarkerMavenPublicationToNetflixOSSRepository"))
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":publishNebulaPublicationToNetflixOSSRepository"))
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":postRelease")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":candidate")).hasOutcome(TaskOutcome.SUCCESS)

        val pom = projectDir.resolve("build/publications/nebula/pom-default.xml")
        assertThat(pom)
            .exists()
            .content()
            .contains("""<groupId>com.netflix.nebula</groupId>""")

        verifications.verify(artifactory)
        markerVerifications.verify(artifactory)
    }

    @Test
    fun `test validation`() {
        val runner = withGitTag(projectDir, remoteGitDir, "v0.0.1") {
            testProject(projectDir) {
                sampleSinglePluginSetup()
            }
        }
        artifactory.mockGradlePluginPortal("com.netflix.nebula.example")
        val result = runner.run(
            "final",
            "publishPlugin", "--validate-only",
            "-Pgradle.publish.key=key",
            "-Pgradle.publish.secret=secret",
            "-Prelease.useLastTag=true",
            "-x", "check",
            "--stacktrace",
            "-Dgradle.portal.url=http://localhost:$port"
        )

        assertThat(result.task(":verifyNebulaPublicationPomForMavenCentral"))
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":verifyPublication"))
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":signPluginMavenPublication"))
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":signExamplePluginMarkerMavenPublication"))
            .hasOutcome(TaskOutcome.SUCCESS)

        // maven central publish skipped
        assertThat(result.task(":publishExamplePluginMarkerMavenPublicationToSonatypeRepository"))
            .hasOutcome(TaskOutcome.SKIPPED)
        assertThat(result.task(":publishNebulaPublicationToSonatypeRepository"))
            .hasOutcome(TaskOutcome.SKIPPED)
        assertThat(result.task(":publishPluginMavenPublicationToSonatypeRepository"))
            .hasOutcome(TaskOutcome.SKIPPED)

        assertThat(result.task(":validatePlugins")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":publishPlugins")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":postRelease")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":final")).hasOutcome(TaskOutcome.SUCCESS)
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

    @Test
    fun `test group override`() {
        val runner = testProject(projectDir) {
            sampleSinglePluginSetup()
            rootProject {
                group("override")
            }
        }
        val result = runner.run("generatePomFileForNebulaPublication", "--stacktrace")

        assertThat(result.task(":generatePomFileForNebulaPublication"))
            .hasOutcome(TaskOutcome.SUCCESS)

        val pom = projectDir.resolve("build/publications/nebula/pom-default.xml")
        assertThat(pom)
            .exists()
            .content()
            .contains("""<groupId>override</groupId>""")
    }
}