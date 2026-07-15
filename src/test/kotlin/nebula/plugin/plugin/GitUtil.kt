package nebula.plugin.plugin

import nebula.test.dsl.TestProjectRunner
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.transport.URIish
import java.io.File
import java.net.URISyntaxException

fun withGitTag(
    projectDir: File,
    remote: File,
    tagName: String,
    project: () -> TestProjectRunner
): TestProjectRunner {
    return withRemoteGit(remote, projectDir) { remote, local ->
        projectDir.resolve(".gitignore").writeText(".gradle/\nbuild/")
        val runner = project()
        local.add().addFilepattern(".").call()
        local.commit().setMessage("Initial").call()
        local.tag().setName(tagName).call()
        runner
    }
}

fun <T> withRemoteGit(remote: File, workingDir: File, work: (Git, Git) -> T): T {
    try {
        Git.init().setBare(false).setDirectory(remote).setInitialBranch("main").call().use { remoteGit ->
            remoteGit.commit().setMessage("initial").call()
            return Git.cloneRepository().setCloneAllBranches(true)
                .setURI(remote.toURI().toString())
                .setDirectory(workingDir)
                .setBare(false)
                .setBranch("main").call()
                .use { cloneGit ->
                    cloneGit.remoteAdd().setName("origin").setUri(URIish().setRawPath(remote.toURI().toString()))
                    work(remoteGit, cloneGit)
                }
        }
    } catch (e: GitAPIException) {
        throw RuntimeException(e)
    } catch (e: URISyntaxException) {
        throw RuntimeException(e)
    }
}
