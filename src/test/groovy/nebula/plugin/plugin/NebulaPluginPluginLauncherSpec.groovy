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

    def "Can run integration tests with sample test"() {
        when:
        buildFile << """
apply plugin: 'nebula-plugin'

dependencies {
    integTestCompile 'junit:junit:4.8.2'
}
"""

        writeHelloWorld('nebula.plugin.plugin')
        writeTest('src/integTest/java/', 'nebula.plugin.plugin', false)
        runTasksSuccessfully('integrationTest')

        then:
        File integrationTestClassFile = new File(projectDir, 'build/classes/integTest/nebula/plugin/plugin/HelloWorldTest.class')
        integrationTestClassFile.exists()
        File integrationTestTestResultsFile = new File(projectDir, 'build/integTest-results/TEST-nebula.plugin.plugin.HelloWorldTest.xml')
        integrationTestTestResultsFile.exists()
    }

    def 'exclude all versions of groovy since we depend on localGroovy provided by gradle'() {
        buildFile << """\
            //${applyPlugin(NebulaPluginPlugin)}
            apply plugin: 'java'
            dependencies {
                compile 'org.spockframework:spock-core:0.7-groovy-1.8'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'compile')

        then:
        !result.standardOutput.contains('groovy-all')

    }
}