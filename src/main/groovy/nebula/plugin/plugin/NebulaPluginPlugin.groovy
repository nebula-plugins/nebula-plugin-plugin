package nebula.plugin.plugin

import nebula.core.ClassHelper
import nebula.core.GradleHelper
import nebula.plugin.publishing.maven.NebulaBaseMavenPublishingPlugin
import nebula.plugin.publishing.NebulaPublishingPlugin
import nebula.plugin.publishing.sign.NebulaSignPlugin
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
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.wrapper.Wrapper
import release.ReleasePlugin
import release.ReleasePluginConvention

import java.text.SimpleDateFormat

/**
 * Provide an environment for a Gradle plugin
 * TODO This is already too big, break apart. Most looks like it would be part of other plugins an part of the responsible plugin
 */
class NebulaPluginPlugin implements Plugin<Project> {
    private static Logger logger = Logging.getLogger(NebulaPluginPlugin);

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
        project.status = project.version.toString().endsWith('-SNAPSHOT')?'integration':'release'

        // Relevant plugins
        project.plugins.apply(NebulaResponsiblePlugin)
        project.plugins.apply(NebulaBintrayPublishingPlugin)
        project.plugins.apply(NebulaOJOPublishingPlugin)
        project.plugins.apply(NebulaPublishingPlugin)
        project.plugins.apply(NebulaSignPlugin)

        // These projects need to be Groovy enabled, even if they don't actually write groovy code. This assumption makes it easier for this infrastructure
        project.plugins.apply(GroovyPlugin)
        project.dependencies.add('compile', project.dependencies.gradleApi()) // We are a plugin after all
        project.dependencies.add('compile', project.dependencies.localGroovy())

        refreshPom()

        addIntegrationTests(project)

        addLocalTests(project)

        addWrapper(project)

        addManifestAttributes(project)

        // Add jcenter, they'll have had to already include jcenter into buildscipt, at least we can do this for them.
        project.repositories.jcenter()

        // Add nebula-plugins, since new plugins won't necessarily be in jcenter()
        // TODO Remove this when plugins are nicely flowing to jcenter()
        project.repositories.maven {
            name: 'Bintray Nebula Plugins repo'
            url 'http://dl.bintray.com/nebula/gradle-plugins'
        }

        addNebulaTest(project)
        addNebulaCore(project)

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
        releaseConvention.git.requireBranch = null

        def spawnBintrayUpload = project.task('spawnBintrayUpload', description: 'Create GradleBuild to run BintrayUpload to use a new version', group: 'Release', type: GradleBuild) {
            startParameter = project.getGradle().startParameter.newInstance()
            tasks = ['bintrayUpload']
        }
        project.tasks.createReleaseTag.dependsOn spawnBintrayUpload
    }

    /**
     * TODO Until we have a plugin to sprinkle Manifest fields in automatically, this is an easy work around
     * TODO Incorporate via an extension what version of Gradle this plugin works with
     * TODO This is more part of responsible plugin
     * TODO Incorporate some Gradle-version compatibility feature
     * @param project
     * @return
     */
    def addManifestAttributes(Project project) {
        project.tasks.withType(Jar) { Jar jar ->
            jar.doFirst {
                // Delay, in case these values, like version are changed after this plugin is applied.

                // A arbitrary calculation of a current build version
                // TODO Remove hostname execution
                // TODO Tie in with a SCM plugin
                String impVersion
                if (System.env['BUILD_TAG']) {
                    impVersion = "${System.env['BUILD_TAG']}"
                } else {
                    def formatter = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");
                    def dateStr = formatter.format(new Date());
                    def hostname = "hostname".execute().text.trim()
                    impVersion = "${dateStr}-${hostname}"
                }
                // Sample
                // Manifest-Version: 1.0
                // Ant-Version: Apache Ant 1.7.0
                // Created-By: 1.5.0_13-119 (Apple Inc.)
                // Package: org.apache.commons.lang
                // Extension-Name: commons-lang
                // Specification-Version: 2.4
                // Specification-Vendor: Apache Software Foundation
                // Specification-Title: Commons Lang
                // Implementation-Version: 2.4
                // Implementation-Vendor: Apache Software Foundation
                // Implementation-Title: Commons Lang
                // Implementation-Vendor-Id: org.apache
                // X-Compile-Source-JDK: 1.3
                // X-Compile-Target-JDK: 1.2

                jar.manifest.attributes("Implementation-Title": project.name, "Specification-Version": project.version, "Implementation-Version": "${project.version} (${impVersion})", 'Gradle-Version': project.gradle.gradleVersion)
            }
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
            def gradleVersion = ClassHelper.findManifestValue(NebulaPluginPlugin.class, 'Gradle-Version', '1.9')

            logger.info("Adding createWrapper task to ${gradleVersion}")

            def wrapperTask = (Wrapper) project.rootProject.tasks.create(name: 'createWrapper', type: Wrapper)
            wrapperTask.gradleVersion = gradleVersion
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

}