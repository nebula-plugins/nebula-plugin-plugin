package nebula.plugin.plugin

import nebula.test.ProjectSpec

class NebulaBintraySyncPublishingPluginSpec extends ProjectSpec {
    def 'apply plugin'() {
        when:
        project.plugins.apply(NebulaBintraySyncPublishingPlugin)

        then:
        def uploadTask = project.tasks.getByName('bintrayUpload')
        uploadTask != null
        uploadTask.actions.size() == 3
    }

    def 'apply plugin with credentials'() {
        when:
        project.ext.sonatypeUsername = 'username'
        project.ext.sonatypePassword = 'password'
        project.plugins.apply(NebulaBintraySyncPublishingPlugin)

        then:
        def uploadTask = project.tasks.getByName('bintrayUpload')
        uploadTask != null
        uploadTask.actions.size() == 4
    }
}
