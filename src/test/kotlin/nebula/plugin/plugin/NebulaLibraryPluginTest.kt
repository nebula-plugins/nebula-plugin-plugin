package nebula.plugin.plugin

import nebula.test.dsl.*
import nebula.test.dsl.TestKitAssertions.assertThat
import org.ajoberstar.grgit.Grgit
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class NebulaLibraryPluginTest {
    @TempDir
    lateinit var projectDir: File

    @TempDir
    lateinit var remoteGitDir: File

    lateinit var runner: TestProjectRunner
    lateinit var localCopy: Grgit

    @BeforeEach
    fun beforeEach() {
        val remoteGit = Grgit.init {
            this.dir = remoteGitDir
        }
        localCopy = Grgit.clone {
            this.dir = projectDir
            this.uri = remoteGitDir.toURI().toString()
        }
        projectDir.resolve(".gitignore").writeText(
            """
.gradle/
"""
        )
        runner = testProject(projectDir) {
            rootProject {
                plugins {
                    id("com.netflix.nebula.root")
                    id("com.netflix.nebula.library")
                }
                rawBuildScript(
                    """
tasks.withType<AbstractPublishToMaven>() {
    onlyIf { false }
}
""".trimIndent()
                )
                src {
                    main {
                        java("example/Main.java", SAMPLE_JAVA_MAIN_CLASS)
                    }
                }
            }
        }
        localCopy.add {
            this.patterns = setOf(".")
        }
        localCopy.commit {
            message = "Initial"
        }
    }

    @Test
    fun `test candidate`() {
        localCopy.tag.add {
            name = "v0.0.1-rc.1"
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
        localCopy.tag.add {
            name = "v0.0.1"
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
}