package nebula.plugin.plugin

import nebula.test.dsl.*
import nebula.test.dsl.TestKitAssertions.assertThat
import org.ajoberstar.grgit.Grgit
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class IntegrationTest {
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
        projectDir.resolve(".gitignore").writeText("""
.gradle/
""")
        runner = testProject(projectDir) {
            rootProject {
                plugins {
                    id("com.netflix.nebula.root")
                }
            }
            subProject("library") {
                plugins {
                    id("com.netflix.nebula.info")
                    id("com.netflix.nebula.maven-publish")
                    id("com.netflix.nebula.library")
                }
                src {
                    main {
                        java(
                            "example/Main.java",
                            //language=java
                            """
package example;
class Main {
    public static void main(String[] args) {
        System.out.println("Hello world!");
    }
}
"""
                        )
                    }
                }
            }
            subProject("plugin") {
                plugins {
                    id("java-gradle-plugin")
                    id("com.netflix.nebula.info")
                    id("com.netflix.nebula.maven-publish")
                    id("com.netflix.nebula.plugin-plugin")
                }
                rawBuildScript("""
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
""")
                src {
                    main {
                        java(
                            "example/Main.java",
                            //language=java
                            """
package example;
import org.gradle.api.Project;

class MyPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
    }
}
"""
                        )
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
    fun `test final`() {
        localCopy.tag.add {
            name = "v0.0.1"
        }
        val result = runner.run(
            "final",
            "-PnetflixOss.username=user",
            "-PnetflixOss.password=password",
            "-Psonatype.username=user",
            "-Psonatype.password=password",
            "--dry-run",
            "--stacktrace"
        )
        assertThat(result.output).contains(":initializeSonatypeStagingRepository SKIPPED")

        // library publication
        assertThat(result.output).contains("library:publishNebulaPublicationToSonatypeRepository SKIPPED")

        // plugin publication
        assertThat(result.output).contains("plugin:publishExamplePluginMarkerMavenPublicationToSonatypeRepository SKIPPED")
        assertThat(result.output).contains("plugin:publishNebulaPublicationToSonatypeRepository SKIPPED")

        // global
        assertThat(result.output).contains(":publishNebulaPublicationToSonatypeRepository SKIPPED")
        assertThat(result.output).contains(":closeSonatypeStagingRepository SKIPPED")
        assertThat(result.output).contains(":releaseSonatypeStagingRepository SKIPPED")
        assertThat(result.output).contains(":closeAndReleaseSonatypeStagingRepository SKIPPED")
        assertThat(result.output).contains(":final SKIPPED")
    }
}