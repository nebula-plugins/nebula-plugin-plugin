package nebula.plugin.plugin

import nebula.core.ClassHelper
import nebula.core.GradleHelper
import nebula.plugin.publishing.NebulaPublishingPlugin
import nebula.plugin.responsible.NebulaResponsiblePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.wrapper.Wrapper

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

        // These projects need to be Groovy enabled, even if they don't actually write groovy code. This assumption makes it easier for this infrastructure
        project.plugins.apply(GroovyPlugin)
        project.dependencies.add('compile', project.dependencies.gradleApi()) // We are a plugin after all
        project.dependencies.add('compile', project.dependencies.localGroovy())

        // Relevant plugins
        project.plugins.apply(NebulaResponsiblePlugin)
        project.plugins.apply(NebulaPublishingPlugin)

        addIntegrationTests(project)

        addLocalTests(project)

        addWrapper(project)

        addManifestAttributes(project)

        // Add jcenter, they'll have had to already include jcenter into buildscipt, at least we can do this for them.
        project.repositories.jcenter()

        // Capture our version, so that we can add aligned versions of nebula-core
        // TODO We need a special case for when we're building ourselves, without releasing twice.
        // Fallback is just while we bootstrap ourselves, since we want to build ourselves with our current version
        def special = ['nebula-tests', 'nebula-plugin-plugin', 'nebula-publishing', 'nebula-responsible']
        def nebulaCoreVersion = special.contains(project.name) ? project.version : (ClassHelper.findSpecificationVersion(NebulaPluginPlugin.class) ?: 'latest.release')

        // Need testing support
        project.dependencies.add('testCompile', "com.netflix.nebula:nebula-tests:${nebulaCoreVersion}")

        // Release, to be replaced by our own in the future.
//        project.plugins.apply(ReleasePlugin)
//        project.ext.'gradle.release.useAutomaticVersion' = "true"
//
//        ReleasePluginConvention releaseConvention = project.convention.getByType(ReleasePluginConvention)
//        releaseConvention.failOnUnversionedFiles = false
//        releaseConvention.failOnCommitNeeded = false
//
//        // We routinely release from different branches, which aren't master
//        releaseConvention.git.requireBranch = null
//
//        def spawnBintrayUpload = project.task('spawnBintrayUpload', description: 'Create GradleBuild to run BintrayUpload to use a new version', group: 'Release', type: GradleBuild) {
//            startParameter = project.getGradle().startParameter.newInstance()
//            tasks = ['bintrayUpload']
//        }
//        project.tasks.createReleaseTag.dependsOn spawnBintrayUpload
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

            project.logger.lifecycle("Adding createWrapper task to ${gradleVersion}")

            def wrapperTask = (Wrapper) project.rootProject.tasks.create(name: 'createWrapper', type: Wrapper)
            wrapperTask.gradleVersion = gradleVersion
        }
    }

}