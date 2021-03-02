package nebula.plugin.plugin

class NebulaPluginPluginPublishingIntegrationSpec extends GitVersioningIntegrationSpec {

    @Override
    def setupBuild() {
        buildFile << """
        apply plugin: 'nebula.plugin-plugin'
        """
    }

    def 'publish snapshot'() {
        when:
        def result = runTasksSuccessfully('snapshot', '-PnetflixOss.username=user',  '-PnetflixOss.password=password', '--dry-run')

        then:
        result.standardOutput.contains(':publishPluginMavenPublicationToNetflixOSSRepository SKIPPED')
    }

    def 'candidate task invokes publication to NetflixOSS repository'() {
        when:
        git.add(patterns: ['.'] as Set)
        git.commit(message: 'Setup 2')
        git.tag.add(name: 'v0.0.1-rc.1')

        def result = runTasksSuccessfully('candidate', '-PnetflixOss.username=user',  '-PnetflixOss.password=password', '--dry-run')

        then:
        result.standardOutput.contains(':publishNebulaPublicationToNetflixOSSRepository SKIPPED')
    }

    def 'final task invokes publication to NetflixOSS repository and sonatype'() {
        when:
        git.add(patterns: ['.'] as Set)
        git.commit(message: 'Setup 3')
        git.tag.add(name: 'v0.0.1')

        def result = runTasksSuccessfully('final', '-PnetflixOss.username=user',  '-PnetflixOss.password=password', '-Psonatype.username=user',  '-Psonatype.password=password', '-Psonatype.signingKey=user',  '-Psonatype.signingPassword=password','--dry-run')

        then:
        result.standardOutput.contains(':validatePlugins SKIPPED')
        result.standardOutput.contains(':publishPlugins SKIPPED')
        result.standardOutput.contains(':signNebulaPublication SKIPPED')
        result.standardOutput.contains(':publishNebulaPublicationToNetflixOSSRepository SKIPPED')
        result.standardOutput.contains(':initializeSonatypeStagingRepository SKIPPED')
        result.standardOutput.contains(':publishNebulaPublicationToSonatypeRepository SKIPPED')
        result.standardOutput.contains(':closeSonatypeStagingRepository SKIPPED')
        result.standardOutput.contains(':releaseSonatypeStagingRepository SKIPPED')
        result.standardOutput.contains(':closeAndReleaseSonatypeStagingRepository SKIPPED')
    }
}
