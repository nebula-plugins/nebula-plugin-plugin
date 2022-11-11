package nebula.plugin.plugin

import nebula.test.IntegrationSpec

class NebulaPluginPluginIntegrationSpec extends IntegrationSpec {
    def 'plugin applies'() {
        buildFile << """
        plugins {
           id 'com.gradle.plugin-publish' version '0.9.4'
        }
        apply plugin: 'com.netflix.nebula.plugin-plugin'
        """

        expect:
        runTasksSuccessfully('help')
    }

    def 'plugin applies when plugin-publish is not applied'() {
        buildFile << """
        apply plugin: 'com.netflix.nebula.plugin-plugin'
        """

        expect:
        runTasksSuccessfully('help')
    }

    def 'plugin publishing is available'() {
        buildFile << """
        apply plugin: 'com.netflix.nebula.plugin-plugin'
      gradlePlugin {
            plugins {
                pluginPlugin {
                    id = 'some-id'
                    displayName = 'Nebula Plugin'
                    implementationClass = 'nebula.plugin.SomePlugin'
                    tags.set(['nebula', 'nebula-plugin'])
                }
            }
        }

        """

        expect:
        runTasksSuccessfully('help')
    }

    def 'plugin applies - disable marker tasks'() {
        buildFile << """
        apply plugin: 'com.netflix.nebula.plugin-plugin'
        
        tasks.register("helloMarkerMaven") { 
            doLast { 
                println 'Hello, World!'
            }
        }
        """

        expect:
        def result = runTasksSuccessfully('helloMarkerMaven')
        result.standardOutput.contains("Task :helloMarkerMaven SKIPPED")
    }

}
