package nebula.plugin.plugin

class NebulaPluginPluginPublishingIntegrationSpec extends GitVersioningIntegrationSpec {

    @Override
    def setupBuild() {
        buildFile << """
        plugins {
            id 'com.netflix.nebula.plugin-plugin'
        }
        """
    }

    def 'publish snapshot'() {
        when:
        def result = runTasks('snapshot', '-PnetflixOss.username=user',  '-PnetflixOss.password=password', '--dry-run')

        then:
        result.output.contains(':publishPluginMavenPublicationToNetflixOSSRepository SKIPPED')
    }

    def 'candidate task invokes publication to NetflixOSS repository'() {
        when:
        git.add(patterns: ['.'] as Set)
        git.commit(message: 'Setup 2')
        git.tag.add(name: 'v0.0.1-rc.1')

        def result = runTasks('candidate', '-PnetflixOss.username=user',  '-PnetflixOss.password=password', '--dry-run')

        then:
        result.output.contains(':publishNebulaPublicationToNetflixOSSRepository SKIPPED')
    }

    def 'final task invokes publication to NetflixOSS repository and sonatype'() {
        when:
        git.add(patterns: ['.'] as Set)
        git.commit(message: 'Setup 3')
        git.tag.add(name: 'v0.0.1')
        System.setProperty('ignoreDeprecations', 'true')
        def result = runTasks('final', '-PnetflixOss.username=user',  '-PnetflixOss.password=password', '-Psonatype.username=user',  '-Psonatype.password=password', '--dry-run', '--no-configuration-cache')

        then:
        result.output.contains(':validatePlugins SKIPPED')
        result.output.contains(':publishPlugins SKIPPED')
        result.output.contains(':publishNebulaPublicationToNetflixOSSRepository SKIPPED')
        result.output.contains(':initializeSonatypeStagingRepository SKIPPED')
        result.output.contains(':publishNebulaPublicationToSonatypeRepository SKIPPED')
        result.output.contains(':closeSonatypeStagingRepository SKIPPED')
        result.output.contains(':releaseSonatypeStagingRepository SKIPPED')
        result.output.contains(':closeAndReleaseSonatypeStagingRepository SKIPPED')
    }
}
