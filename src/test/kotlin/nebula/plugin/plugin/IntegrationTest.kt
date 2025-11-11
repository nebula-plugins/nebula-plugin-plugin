package nebula.plugin.plugin

import nebula.test.dsl.*
import nebula.test.dsl.TestKitAssertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockserver.configuration.ConfigurationProperties
import org.mockserver.integration.ClientAndServer
import java.io.File
import java.net.ServerSocket

internal class IntegrationTest {
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

    private fun TestProjectBuilder.sampleMultiProjectSetup() {
        rootProject {
            plugins {
                id("com.netflix.nebula.root")
            }
            contacts()
        }
        subProject("library") {
            plugins {
                id("com.netflix.nebula.library")
            }
            rawBuildScript("""description = "library"""")
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
description = "plugin"
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
        val version = "0.0.1-rc.1"
        val runner = withGitTag(projectDir, remoteGitDir, "v$version") {
            testProject(projectDir) {
                sampleMultiProjectSetup()
                rootProject {
                    nebulaOssPublishing("http://localhost:$port")
                }
            }
        }
        val libraryVerifications = artifactory.expectPublication(
            "gradle-plugins",
            "com.netflix.nebula",
            "library",
            version
        ) {
            withArtifact("jar")
            withArtifact("sources", "jar")
            withArtifact("javadoc", "jar")
            withGradleModuleMetadata()
        }
        val pluginVerifications = artifactory.expectPublication(
            "gradle-plugins",
            "com.netflix.nebula",
            "plugin",
            version
        ) {
            withArtifact("jar")
            withArtifact("sources", "jar")
            withArtifact("javadoc", "jar")
            withGradleModuleMetadata()
        }

        val markerVerifications = artifactory.expectPublication(
            "gradle-plugins",
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

        assertThat(result.task(":generatePomFileForNebulaPublication")).isNull()

        // library publication
        assertThat(result.task(":library:javadoc")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":library:generatePomFileForNebulaPublication"))
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":library:publishNebulaPublicationToNetflixOSSRepository"))
            .hasOutcome(TaskOutcome.SUCCESS)

        // plugin publication
        assertThat(result.task(":plugin:validatePlugins")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":plugin:generatePomFileForNebulaPublication"))
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":plugin:publishExamplePluginMarkerMavenPublicationToNetflixOSSRepository"))
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":plugin:publishNebulaPublicationToNetflixOSSRepository"))
            .hasOutcome(TaskOutcome.SUCCESS)
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

        libraryVerifications.verify(artifactory)
        pluginVerifications.verify(artifactory)
        markerVerifications.verify(artifactory)
    }

    @Test
    fun `test validation`() {
        val runner = withGitTag(projectDir, remoteGitDir, "v0.0.1") {
            testProject(projectDir) {
                sampleMultiProjectSetup()
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

        assertThat(result.task(":initializeSonatypeStagingRepository"))
            .hasOutcome(TaskOutcome.SKIPPED)

        assertThat(result.task(":plugin:verifyNebulaPublicationPomForMavenCentral"))
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":plugin:verifyPublication"))
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":plugin:signPluginMavenPublication"))
            .hasOutcome(TaskOutcome.SKIPPED)
        assertThat(result.task(":plugin:signExamplePluginMarkerMavenPublication"))
            .hasOutcome(TaskOutcome.SKIPPED)

        // maven central publish skipped
        assertThat(result.task(":plugin:publishExamplePluginMarkerMavenPublicationToSonatypeRepository"))
            .hasOutcome(TaskOutcome.SKIPPED)
        assertThat(result.task(":plugin:publishNebulaPublicationToSonatypeRepository"))
            .hasOutcome(TaskOutcome.SKIPPED)
        assertThat(result.task(":plugin:publishPluginMavenPublicationToSonatypeRepository"))
            .hasOutcome(TaskOutcome.SKIPPED)
        assertThat(result.task(":library:publishNebulaPublicationToSonatypeRepository"))
            .hasOutcome(TaskOutcome.SKIPPED)

        assertThat(result.task(":plugin:validatePlugins")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":plugin:publishPlugins")).hasOutcome(TaskOutcome.SUCCESS)

        assertThat(result.task(":closeSonatypeStagingRepository"))
            .hasOutcome(TaskOutcome.SKIPPED)

        assertThat(result.task(":postRelease")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":final")).hasOutcome(TaskOutcome.SUCCESS)
    }

    @Test
    fun `test final`() {
        val runner = withGitTag(projectDir, remoteGitDir, "v0.0.1") {
            testProject(projectDir) {
                sampleMultiProjectSetup()
                rootProject {
                    nebulaOssPublishing("http://localhost:$port")
                }
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