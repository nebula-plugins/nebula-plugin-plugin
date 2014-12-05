package nebula.plugin.plugin

import nebula.test.ProjectSpec

class NebulaPluginPluginSpec extends ProjectSpec {
    def 'Execute plugin'() {
        when:
        project.apply plugin: 'nebula-plugin'

        then:
        project.plugins.getPlugin('groovy')
        project.configurations.getByName('integTestCompile')
        project.configurations.getByName('integTestRuntime')
        project.sourceSets.getByName('integTest')
        project.tasks.getByName('testLocal') != null
        project.tasks.getByName('createWrapper') != null
    }

    def 'create generateQualifiedPlugins task'() {
        when:
        project.apply plugin: 'nebula-plugin'

        then:
        project.tasks.getByName('generateQualifiedPlugins') != null
    }
}
