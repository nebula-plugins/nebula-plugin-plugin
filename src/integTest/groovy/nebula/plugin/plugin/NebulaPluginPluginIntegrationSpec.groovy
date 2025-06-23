package nebula.plugin.plugin

import nebula.test.IntegrationTestKitSpec

class NebulaPluginPluginIntegrationSpec extends IntegrationTestKitSpec {
    def 'plugin applies'() {
        buildFile << """
        plugins {
           id 'com.netflix.nebula.plugin-plugin'
        }
        """

        expect:
        runTasks('help')
    }


    def 'plugin publishing is available'() {
        buildFile << """
        plugins {
           id 'com.netflix.nebula.plugin-plugin'
        }
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
        runTasks('help')
    }

    def 'plugin applies - disable marker tasks'() {
        buildFile << """    
        plugins {
           id 'com.netflix.nebula.plugin-plugin'
        }    
        tasks.register("helloMarkerMaven") { 
            doLast { 
                println 'Hello, World!'
            }
        }
        """

        expect:
        def result = runTasks('helloMarkerMaven')
        result.output.contains("Task :helloMarkerMaven SKIPPED")
    }

}
