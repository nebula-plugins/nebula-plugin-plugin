package nebula.plugin.plugin

import java.util.jar.JarEntry
import java.util.jar.JarFile
import nebula.test.IntegrationSpec

class NebulaPluginPluginLauncherSpec extends IntegrationSpec {

    def 'check copy'() {
        buildFile << '''\
            apply plugin: 'nebula-plugin'
        '''.stripIndent()

        def pluginCode = new File(projectDir, 'src/main/groovy')
        pluginCode.mkdirs()
        def pluginProperties = new File(projectDir, 'src/main/resources/META-INF/gradle-plugins')
        pluginProperties.mkdirs()

        def plugin1 = new File(pluginCode, 'PluginOne.groovy')
        plugin1.text = '''\
            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class PluginOne implements Plugin<Project> {
                void apply(Project project) {}
            }
        '''.stripIndent()
        def plugin2 = new File(pluginCode, 'PluginTwo.groovy')
        plugin2.text = '''\
            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class PluginTwo implements Plugin<Project> {
                void apply(Project project) {}
            }
        '''.stripIndent()

        def prop1 = new File(pluginProperties, 'pluginone.properties')
        prop1.text = 'implementation-class=PluginOne'
        def prop2 = new File(pluginProperties, 'plugintwo.properties')
        prop2.text = 'implementation-class=PluginTwo'

        when:
        runTasksSuccessfully('build')

        then:
        def qualified1 = new File(projectDir, 'build/qualifed-resources/META-INF/gradle-plugins/nebula.pluginone.properties')
        def qualified2 = new File(projectDir, 'build/qualifed-resources/META-INF/gradle-plugins/nebula.plugintwo.properties')
        qualified1.exists()
        qualified2.exists()
        def jar = new JarFile(new File(projectDir, 'build/libs/check-copy.jar'))
        def files = jar.entries().collect { it.name }
        println files
        files.contains 'META-INF/gradle-plugins/nebula.pluginone.properties'
        files.contains 'META-INF/gradle-plugins/pluginone.properties'
    }

}