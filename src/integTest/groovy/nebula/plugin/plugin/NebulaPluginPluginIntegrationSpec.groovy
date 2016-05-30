package nebula.plugin.plugin

import nebula.test.IntegrationSpec

class NebulaPluginPluginIntegrationSpec extends IntegrationSpec {
    def 'plugin applies'() {
        buildFile << """
        plugins {
           id 'com.gradle.plugin-publish' version '0.9.4'
        }
        apply plugin: 'nebula.plugin-plugin'
        """

        expect:
        runTasksSuccessfully('help')
    }

    def 'plugin applies when plugin-publish is not applied'() {
        buildFile << """
        apply plugin: 'nebula.plugin-plugin'
        """

        expect:
        runTasksSuccessfully('help')
    }
}
