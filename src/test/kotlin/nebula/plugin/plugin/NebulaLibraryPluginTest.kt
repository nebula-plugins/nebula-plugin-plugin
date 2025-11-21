package nebula.plugin.plugin

import nebula.test.dsl.*
import nebula.test.dsl.TestKitAssertions.assertThat
import org.ajoberstar.grgit.Grgit
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockserver.configuration.ConfigurationProperties
import org.mockserver.integration.ClientAndServer
import java.io.File
import java.net.ServerSocket

internal class NebulaLibraryPluginTest {
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

    @Test
    fun `test build`() {
        val runner = testProject(projectDir) {
            properties {
                gradleCache(true)
            }
            rootProject {
                plugins {
                    id("com.netflix.nebula.root")
                    id("com.netflix.nebula.library")
                }
                disableMavenPublishTasks()
                src {
                    main {
                        java("example/Main.java", SAMPLE_JAVA_MAIN_CLASS)
                    }
                }
            }
        }
        val result = runner.run("build", "--stacktrace")
        assertThat(result.task(":check"))
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)
        assertThat(result.task(":build")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result)
            .hasNoDeprecationWarnings()
            .hasNoMutableStateWarnings()

        assertThat(result.task(":archRulesConsoleReport"))
            .`as`("archRules are checked")
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.output)
            .contains("ArchRule summary:")
    }

    @Test
    fun `test candidate`() {
        val runner = withGitTag(projectDir, remoteGitDir, "v0.0.1-rc.1") {
            testProject(projectDir) {
                rootProject {
                    plugins {
                        id("com.netflix.nebula.root")
                        id("com.netflix.nebula.library")
                    }
                    disableMavenPublishTasks()
                    src {
                        main {
                            java("example/Main.java", SAMPLE_JAVA_MAIN_CLASS)
                        }
                    }
                }
            }
        }
        val result = runner.run(
            "candidate",
            "-Prelease.useLastTag=true",
            "-PnetflixOss.username=user",
            "-PnetflixOss.password=password",
            "--stacktrace"
        )

        assertThat(result.task(":javadoc")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":publishNebulaPublicationToNetflixOSSRepository"))
            .hasOutcome(TaskOutcome.SKIPPED)
        assertThat(result.task(":postRelease")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":candidate")).hasOutcome(TaskOutcome.SUCCESS)
    }

    @Test
    fun `test final`() {
        val runner = withGitTag(projectDir, remoteGitDir, "v0.0.1") {
            testProject(projectDir) {
                rootProject {
                    plugins {
                        id("com.netflix.nebula.root")
                        id("com.netflix.nebula.library")
                    }
                    disableMavenPublishTasks()
                    src {
                        main {
                            java("example/Main.java", SAMPLE_JAVA_MAIN_CLASS)
                        }
                    }
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
        assertThat(result.output).contains(":publishNebulaPublicationToSonatypeRepository SKIPPED")
        assertThat(result.output).contains(":publishNebulaPublicationToNetflixOSSRepository SKIPPED")
        assertThat(result.output).contains(":closeSonatypeStagingRepository SKIPPED")
        assertThat(result.output).contains(":releaseSonatypeStagingRepository SKIPPED")
        assertThat(result.output).contains(":closeAndReleaseSonatypeStagingRepository SKIPPED")
        assertThat(result.output).contains(":final SKIPPED")
    }

    @Test
    fun `test multi-project library only`() {
        val version = "0.0.1"
        val runner = withGitTag(projectDir, remoteGitDir, "v$version") {
            testProject(projectDir) {
                rootProject {
                    plugins {
                        id("com.netflix.nebula.root")
                    }
                    nebulaOssPublishing("http://localhost:$port")
                    overrideSonatypeUrlRoot("http://localhost:$port")
                    contacts()
                }
                subProject("library") {
                    plugins {
                        id("com.netflix.nebula.library")
                    }
                    rawBuildScript("""description = "description"""")
                    mockSign()
                    allowInsecure()
                    src {
                        main {
                            java("example/Main.java", SAMPLE_JAVA_MAIN_CLASS)
                        }
                    }
                }
            }
        }
        artifactory.mockNexus()
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
        val sonatypeVerifications = artifactory.expectPublication(
            "staging/deployByRepositoryId/1",
            "com.netflix.nebula",
            "library",
            version
        ) {
            withArtifact("jar")
            withArtifact("sources", "jar")
            withArtifact("javadoc", "jar")
            withGradleModuleMetadata()
        }
        val result = runner.run(
            "final",
            "-Prelease.useLastTag=true",
            "-PnetflixOss.username=user",
            "-PnetflixOss.password=password",
            "-Psonatype.username=user",
            "-Psonatype.password=password",
            "--stacktrace"
        )
        assertThat(result.task(":generatePomFileForNebulaPublication")).isNull()

        assertThat(result.task(":initializeSonatypeStagingRepository")).hasOutcome(TaskOutcome.SUCCESS)

        // library publication
        assertThat(result.task(":library:javadoc")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":library:generatePomFileForNebulaPublication"))
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":library:publishNebulaPublicationToNetflixOSSRepository"))
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":library:publishNebulaPublicationToSonatypeRepository"))
            .hasOutcome(TaskOutcome.SUCCESS)

        // global
        assertThat(result.task(":postRelease")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":closeSonatypeStagingRepository")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":releaseSonatypeStagingRepository")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":closeAndReleaseSonatypeStagingRepository")).hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":final")).hasOutcome(TaskOutcome.SUCCESS)


        libraryVerifications.verify(artifactory)
        sonatypeVerifications.verify(artifactory)
    }
}