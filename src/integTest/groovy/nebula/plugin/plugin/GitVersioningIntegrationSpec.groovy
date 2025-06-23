package nebula.plugin.plugin

import nebula.test.IntegrationTestKitSpec
import org.ajoberstar.grgit.Grgit

import java.nio.file.Files

abstract class GitVersioningIntegrationSpec extends IntegrationTestKitSpec {
    protected Grgit git
    protected Grgit originGit

    def setup() {
        def origin = new File(projectDir.parent, "${projectDir.name}.git")
        if (origin.exists()) {
            origin.deleteDir()
        }
        origin.mkdirs()

        ['build.gradle', 'settings.gradle'].each {
            def file = new File(projectDir, it)
            if(!file.exists()) {
                file.createNewFile()
            }
            Files.move(file.toPath(), new File(origin, it).toPath())
        }

        originGit = Grgit.init(dir: origin)
        originGit.add(patterns: ['build.gradle', 'settings.gradle', '.gitignore'] as Set)
        originGit.commit(message: 'Initial checkout')

        if (originGit.branch.current().name == 'main') {
            originGit.checkout(branch: 'master', createBranch: true)
            originGit.branch.remove(names: ['main'])
        }

        git = Grgit.clone(dir: projectDir, uri: origin.absolutePath) as Grgit

        new File(projectDir, '.gitignore') << '''.gradle-test-kit
.gradle
build/
gradle.properties'''.stripIndent()

        // Enable configuration cache :)
        new File(projectDir, 'gradle.properties') << '''org.gradle.configuration-cache=true'''.stripIndent()

        setupBuild()


        git.add(patterns: ['build.gradle', 'settings.gradle', '.gitignore'])
        git.commit(message: 'Setup')
        git.push()
    }

    abstract def setupBuild()

    def cleanup() {
        if (git) git.close()
        if (originGit) originGit.close()
    }
}
