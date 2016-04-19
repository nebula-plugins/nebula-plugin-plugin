package nebula.plugin.plugin

import nebula.test.IntegrationSpec
import spock.util.Exceptions

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

    def 'plugin does not apply if plugin-publish plugin is not applied'() {
        buildFile << """
        apply plugin: 'nebula.plugin-plugin'
        """

        when:
        def result = runTasks('help')

        then:
        Exceptions.getRootCause(result.failure).message == 'The com.gradle.plugin-publish plugin must be applied before this plugin. Expression: project.plugins.findPlugin(id)'
    }
}
