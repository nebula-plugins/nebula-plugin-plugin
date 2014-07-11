package nebula.plugin.plugin

import nebula.core.ClassHelper
import nebula.core.GradleHelper
import nebula.plugin.bintray.BintrayPlugin
import nebula.plugin.info.InfoBrokerPlugin
import nebula.plugin.plugin.tasks.CreateQualifiedPluginPropertiesTask
import nebula.plugin.publishing.maven.NebulaBaseMavenPublishingPlugin
import nebula.plugin.responsible.NebulaResponsiblePlugin
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.internal.project.AbstractProject
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.GradleBuild
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.plugins.ide.idea.IdeaPlugin
import release.GitReleasePluginConvention
import release.ReleasePlugin
import release.ReleasePluginConvention

/**
 * Provide an environment for a Gradle plugin
 */
class NebulaPluginPlugin implements Plugin<Project> {
    private static Logger logger = Logging.getLogger(NebulaPluginPlugin)

    protected Project project

    @Override
    void apply(Project project) {
        this.project = project

        project.logger.lifecycle("Enabling Nebula for this project ${project.name}")

        // Maven publication want group upon publication creation, so establishing early. User will have to set group
        // before applying this plugin.
        new GradleHelper(project).addDefaultGroup('com.netflix.nebula')

        // Status can effect a few things in an Ivy publish, so try to set status early. Assumes version is already set,
        // somewhere like gradle.properties
        project.status = project.version.toString().endsWith('-SNAPSHOT') ? 'integration' : 'release'

        // Relevant plugins
        project.plugins.apply(BintrayPlugin)
        new NebulaBintrayPluginConfiguration().configure(project)

        project.plugins.apply(NebulaResponsiblePlugin)

        // These projects need to be Groovy enabled, even if they don't actually write groovy code. This assumption makes it easier for this infrastructure
        project.plugins.apply(GroovyPlugin)
        project.dependencies.add('compile', project.dependencies.gradleApi()) // We are a plugin after all
        project.dependencies.add('compile', project.dependencies.localGroovy())

        refreshPom()

        addIntegrationTests(project)

        addLocalTests(project)

        addWrapper(project)

        addManifestAttributes(project)

        resourcesCopyQualifiedPluginNames()

        // Add jcenter, they'll have had to already include jcenter into buildscipt, at least we can do this for them.
        project.repositories.jcenter()

        // Add nebula-plugins, since new plugins won't necessarily be in jcenter()
        // TODO Remove this when plugins are nicely flowing to jcenter()
        project.repositories.maven {
            name: 'Bintray Nebula Plugins repo'
            url 'http://dl.bintray.com/nebula/gradle-plugins'
        }

        //addNebulaTest(project)
        //addNebulaCore(project)

        configureRelease(project)
        configureSnapshot(project)
    }

    private void addNebulaTest(AbstractProject project) {
        if (project.name == 'nebula-test') {
            return
        }

        // TODO We need a special case for when we're building ourselves, without releasing twice.
        // TODO Maybe just a property to trigger this behavior
        // TODO We're only using this for nebula-core, which will be versioned separately, we want this configurable from a property file
        // TODO a plugin which lets arbitrary dependencies be overriden, would let us override this as needed
        // Fallback is just while we bootstrap ourselves, since we want to build ourselves with our current version

        Properties properties = new Properties();
        InputStream is = this.getClass().getResourceAsStream('/nebula.properties');
        if (is) {
            // The Gradle daemon can cache the class, and it'll prevent nebula.properties from being available.
            logger.info("Able to load properties from nebula.properties")
            properties.load(is);
        }

        def key = 'com.netflix.nebula.nebula-test.rev'
        def nebulaTestVersion = project.rootProject.hasProperty(key) ? project.rootProject.property(key) : (properties.get(key) ?: "${project.gradle.gradleVersion}.+")
        logger.info("Nebula Test Version: ${nebulaTestVersion}")

        // Need testing support

        project.dependencies.add('testCompile', "com.netflix.nebula:nebula-test:${nebulaTestVersion}")
    }

    private void addNebulaCore(AbstractProject project) {
        if (project.name == 'nebula-core' || project.name == 'nebula-test') {
            return
        }

        Properties properties = new Properties();
        InputStream is = this.getClass().getResourceAsStream('/nebula.properties');
        if (is) {
            // The Gradle daemon can cache the class, and it'll prevent nebula.properties from being available.
            logger.info("Able to load properties from nebula.properties")
            properties.load(is);
        }

        def key = 'com.netflix.nebula.nebula-core.rev'
        def nebulaCoreVersion = project.rootProject.hasProperty(key) ? project.rootProject.property(key) : (properties.get(key) ?: "${project.gradle.gradleVersion}.+")
        logger.info("Nebula Core Version: ${nebulaCoreVersion} (${properties.get(key)}/${project.gradle.gradleVersion}.+)")

        // Need testing support

        project.dependencies.add('compile', "com.netflix.nebula:nebula-core:${nebulaCoreVersion}")
    }

    def configureSnapshot(AbstractProject project) {
        project.tasks.matching { it.name == 'artifactoryPublish' }.all {
            project.tasks.create('snapshot').dependsOn(it)
        }
    }

    def configureRelease(AbstractProject project) {
        // Release, to be replaced by our own in the future.
        project.plugins.apply(ReleasePlugin)
        project.ext.'gradle.release.useAutomaticVersion' = "true"

        ReleasePluginConvention releaseConvention = project.convention.getPlugin(ReleasePluginConvention)
        releaseConvention.failOnUnversionedFiles = false
        releaseConvention.failOnCommitNeeded = false

        // We routinely release from different branches, which aren't master
        def gitReleaseConvention = (GitReleasePluginConvention) releaseConvention.git
        gitReleaseConvention.requireBranch = null
        gitReleaseConvention.pushToCurrentBranch = true

        def spawnBintrayUpload = project.task('spawnBintrayUpload', description: 'Create GradleBuild to run BintrayUpload to use a new version', group: 'Release', type: GradleBuild) {
            startParameter = project.getGradle().startParameter.newInstance()
            tasks = ['bintrayUpload']
        }
        project.tasks.createReleaseTag.dependsOn spawnBintrayUpload
    }

    /**
     * Sprinkle Manifest fields to use for compatibility detection
     * @param project
     * @return
     */
    def addManifestAttributes(Project project) {
        project.plugins.withType(InfoBrokerPlugin) { InfoBrokerPlugin broker ->
            // Gradle-Version is part of the BasicInfoPlugin
            // TODO Incorporate via an extension what version of Gradle this plugin works with
            // TODO This is more part of responsible plugin
            // TODO Incorporate some Gradle-version compatibility feature

        }
    }

    /**
     * Add Gradle Integration tests, via a conf, sourceSet and task
     * TODO Implement in Java-esque syntax
     * @param project
     */
    private void addIntegrationTests(Project project) {
        project.plugins.withType(JavaBasePlugin) { // Conditionalize usage of convention
            JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention) // Always exists, since we're applying java-base

            project.configurations {
                integrationTestCompile.extendsFrom testCompile
                integrationTestRuntime.extendsFrom testRuntime
            }

            def sourceSets = javaConvention.sourceSets
            SourceSet parentSourceSet = sourceSets.getByName('test') // TBD Should this be main or test
            def integrationTestSourceSet = sourceSets.create('integrationTest') {
                compileClasspath += parentSourceSet.output
                runtimeClasspath += parentSourceSet.output
            }

            configureIdeaPlugin(project, integrationTestSourceSet)

            def intTestTask = project.task(type: Test, 'integrationTest', group: 'verification') {
                description 'Test classes which run the GradleLauncher'
                testClassesDir = integrationTestSourceSet.output.classesDir
                classpath = integrationTestSourceSet.runtimeClasspath
                testSrcDirs = integrationTestSourceSet.allJava.srcDirs as List
            }

            // Establish some ordering
            intTestTask.mustRunAfter(project.tasks.getByName('test'))
            project.tasks.getByName('check').dependsOn(intTestTask)
        }
    }

    /**
     * Configures IDEA plugin to add given SourceSet to test source directories and implicit configurations to the TEST
     * scope.
     *
     * @param project Project
     * @param testSourceSet Test SourceSet
     */
    private void configureIdeaPlugin(Project project, SourceSet testSourceSet) {
        project.plugins.withType(IdeaPlugin) {
            project.idea {
                module {
                    testSourceSet.allSource.srcDirs.each { srcDir ->
                        testSourceDirs += srcDir
                    }

                    scopes.TEST.plus += project.configurations.getByName("${testSourceSet.name}Compile")
                    scopes.TEST.plus += project.configurations.getByName("${testSourceSet.name}Runtime")
                }
            }
        }
    }

    def addLocalTests(Project project) {

        project.plugins.withType(JavaBasePlugin) { // Conditionalize usage of convention
            JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention) // Always exists, since we're applying java-base

            project.tasks.getByName('test') {
                exclude '**/*Local*'
            }

            SourceSet testSourceSet = javaConvention.sourceSets.getByName('test')
            project.tasks.create(name: 'testLocal', type: Test, group: 'verification') {
                description 'Test to be run locally'
                testClassesDir = testSourceSet.output.classesDir
                classpath = testSourceSet.runtimeClasspath
                include '**/*Local*'
            }
        }
    }

    /**
     * Create a wrapper tasks which is aligned with this plugin, which may be tied to a specific version of Gradle
     * @param project
     */
    def addWrapper(Project project) {
        if(!project.rootProject.tasks.findByName('createWrapper')) {
            // TODO Remove default when it's reliably there
            def gradleVersion = ClassHelper.findManifestValue(NebulaPluginPlugin.class, 'Gradle-Version', '1.12')

            logger.info("Adding createWrapper task to ${gradleVersion}")

            def wrapperTask = (Wrapper) project.rootProject.tasks.create(name: 'createWrapper', type: Wrapper)
            wrapperTask.gradleVersion = gradleVersion
            wrapperTask.distributionUrl = "http://dl.bintray.com/nebula/gradle-distributions/${gradleVersion}/gradle-${gradleVersion}-bin.zip"

        }
    }

    /**
     * Handle adding SCM, url and licenses to pom. It's purely constructive, so if someone else try to add these fields the build will break
     * TODO Make this conditionally additive, so that projects can override
     * @return
     */
    def refreshPom() {
        def repoName = project.name
        def pomConfig = {
            // TODO Call scmprovider plugin for values
            url "https://github.com/nebula-plugins/${repoName}"

            scm {
                url "scm:git://github.com/nebula-plugins/${repoName}.git"
                connection "scm:git://github.com/nebula-plugins/${repoName}.git"
            }

            licenses {
                license {
                    name 'The Apache Software License, Version 2.0'
                    url 'http://www.apache.org/licenses/LICENSE-2.0.txt'
                    distribution 'repo'
                }
            }
        }

        project.plugins.withType(NebulaBaseMavenPublishingPlugin) { basePlugin ->
            basePlugin.withMavenPublication { MavenPublication t ->
                t.pom.withXml(new Action<XmlProvider>() {
                    @Override
                    void execute(XmlProvider x) {
                        def root = x.asNode()
                        root.children().last() + pomConfig
                    }
                })
            }
        }
    }

    def resourcesCopyQualifiedPluginNames() {
        project.plugins.withType(JavaBasePlugin) {
            def qualifiedPlugins = "${project.buildDir}/qualifed-resources"
            def outputDirName = "${qualifiedPlugins}/META-INF/gradle-plugins"
            def generateTaskName = 'generateQualifiedPlugins'
            def generateTask = project.tasks.create(generateTaskName, CreateQualifiedPluginPropertiesTask)

            def resourceDir = project.sourceSets.main.resources.srcDirs.find { new File(it, 'META-INF/gradle-plugins').exists() }

            if (resourceDir) {
                generateTask.pluginPropertiesDir = new File(resourceDir, 'META-INF/gradle-plugins')
                generateTask.outputDir = new File(outputDirName)

                project.sourceSets.main {
                    output.dir(qualifiedPlugins, builtBy: generateTask)
                }
            }   
        }
    }

}