package nebula.plugin.plugin

import nebula.test.ProjectSpec

class NebulaOJOPublishingPluginSpec extends ProjectSpec {
    def 'apply plugin'() {
        when:
        project.plugins.apply('nebula-ojo-publishing')

        then:
        project.tasks.getByName('artifactoryPublish') != null
    }
}
