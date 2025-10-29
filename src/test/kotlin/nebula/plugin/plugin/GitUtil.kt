package nebula.plugin.plugin

import nebula.test.dsl.TestProjectRunner
import org.ajoberstar.grgit.Grgit
import java.io.File

fun withGitTag(
    projectDir: File,
    remoteGitDir: File,
    tag: String,
    block: () -> TestProjectRunner
): TestProjectRunner {
    Grgit.init {
        dir = remoteGitDir
    }
    val localCopy = Grgit.clone {
        dir = projectDir
        uri = remoteGitDir.toURI().toString()
    }
    projectDir.resolve(".gitignore").writeText(
        """
.gradle/
"""
    )
    val runner = block()
    localCopy.add {
        this.patterns = setOf(".")
    }
    localCopy.commit {
        message = "Initial"
    }
    localCopy.tag.add {
        name = tag
    }
    return runner
}