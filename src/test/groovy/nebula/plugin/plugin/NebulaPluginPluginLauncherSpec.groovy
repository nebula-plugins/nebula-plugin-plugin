package nebula.plugin.plugin

import nebula.test.IntegrationSpec

import java.util.jar.JarFile

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
    integrationTestCompile 'junit:junit:4.8.2'
}
"""

        writeHelloWorld('nebula.plugin.plugin')
        writeTest('src/integrationTest/java/', 'nebula.plugin.plugin', false)
        runTasksSuccessfully('integrationTest')

        then:
        File integrationTestClassFile = new File(projectDir, 'build/classes/integrationTest/nebula/plugin/plugin/HelloWorldTest.class')
        integrationTestClassFile.exists()
        File integrationTestTestResultsFile = new File(projectDir, 'build/test-results/TEST-nebula.plugin.plugin.HelloWorldTest.xml')
        integrationTestTestResultsFile.exists()
    }

    def "Configures Idea project files for created integration test SourceSet and task"() {
        when:
        buildFile << """
apply plugin: 'nebula-plugin'
apply plugin: 'idea'

dependencies {
    integrationTestCompile 'junit:junit:4.8.2'
    integrationTestRuntime 'mysql:mysql-connector-java:5.1.27'
}
"""

        writeHelloWorld('nebula.plugin.plugin')
        writeTest('src/integrationTest/java/', 'nebula.plugin.plugin', false)
        runTasksSuccessfully('idea')

        then:
        File ideaModuleFile = new File(projectDir, "${moduleName}.iml")
        ideaModuleFile.exists()
        def moduleXml = new XmlSlurper().parseText(ideaModuleFile.text)
        def testSourceFolders = moduleXml.component.content.sourceFolder.findAll { it.@isTestSource.text() == 'true' }
        def testSourceFolder = testSourceFolders.find { it.@url.text() == "file://\$MODULE_DIR\$/src/integrationTest/java" }
        testSourceFolder
        def orderEntries = moduleXml.component.orderEntry.findAll { it.@type.text() == 'module-library' && it.@scope.text() == 'TEST' }
        def junitLibrary = orderEntries.find { it.library.CLASSES.root.@url.text().contains('junit-4.8.2.jar') }
        junitLibrary
        def mysqlLibrary = orderEntries.find { it.library.CLASSES.root.@url.text().contains('mysql-connector-java-5.1.27.jar') }
        mysqlLibrary
    }
}