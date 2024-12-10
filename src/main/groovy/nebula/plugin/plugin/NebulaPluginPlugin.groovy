/*
 * Copyright 2014-2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nebula.plugin.plugin

import io.github.gradlenexus.publishplugin.AbstractNexusStagingRepositoryTask
import nebula.plugin.publishing.NebulaOssPublishingExtension
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.plugin.devel.tasks.ValidatePlugins
import org.gradle.plugins.signing.Sign
import org.gradle.util.GradleVersion

import javax.inject.Inject

/**
 * Provide an environment for a Gradle plugin
 */
class NebulaPluginPlugin implements Plugin<Project> {
    static final GRADLE_PLUGIN_IDS = ['groovy',
                                      'idea',
                                      'com.gradle.plugin-publish',
                                      'java-gradle-plugin']

    static final NEBULA_PLUGIN_IDS = ['com.netflix.nebula.contacts',
                                      'com.netflix.nebula.dependency-lock',
                                      'com.netflix.nebula.facet',
                                      'com.netflix.nebula.info',
                                      'com.netflix.nebula.java-cross-compile',
                                      'com.netflix.nebula.javadoc-jar',
                                      'com.netflix.nebula.maven-apache-license',
                                      'com.netflix.nebula.maven-publish',
                                      'com.netflix.nebula.publish-verification',
                                      'com.netflix.nebula.release',
                                      'com.netflix.nebula.oss-publishing',
                                      'com.netflix.nebula.source-jar',
                                      'com.netflix.nebula.integtest']

    static final PLUGIN_IDS = GRADLE_PLUGIN_IDS  + NEBULA_PLUGIN_IDS

    private final ProviderFactory providers
    private boolean isPluginPublishingValidation

    @Inject
    NebulaPluginPlugin(ProviderFactory providerFactory) {
        this.providers = providerFactory
    }

    @Override
    void apply(Project project) {
        this.isPluginPublishingValidation = project.gradle.startParameter.taskNames.contains('--validate-only')
        project.with {
            def nebulaOssPublishingExtension = project.rootProject.extensions.findByType(NebulaOssPublishingExtension) ?: project.rootProject.extensions.create("nebulaOssPublishing", NebulaOssPublishingExtension)
            nebulaOssPublishingExtension.packageGroup.set("com.netflix")

            PLUGIN_IDS.each { plugins.apply(it) }

            tasks.withType(ValidatePlugins).configureEach {
                it.enableStricterValidation.set(true)
            }

            if (!group) {
                group = 'com.netflix.nebula'
            }

            JavaPluginExtension javaPluginExtension = extensions.getByType(JavaPluginExtension)
            JavaToolchainSpec toolchainSpec = javaPluginExtension.toolchain
            toolchainSpec.languageVersion.convention(JavaLanguageVersion.of(8))
            java {
                sourceCompatibility = null
                targetCompatibility = null
            }

            repositories {
                maven {
                    url = 'https://plugins.gradle.org/m2/'
                    metadataSources {
                        mavenPom()
                        artifact()
                        ignoreGradleMetadataRedirection()
                    }
                }
                mavenCentral()
            }

            if (GradleVersion.current().baseVersion >= GradleVersion.version("7.0")) {
                tasks.withType(Test).configureEach {
                    useJUnitPlatform()
                }
            }

            dependencies {
                implementation gradleApi()
                //we apply plugin-plugin in nebula-test to and we don't want to create cycles which confuses gradle locks
                if (project.name != 'nebula-test') {
                    if (GradleVersion.current().baseVersion >= GradleVersion.version("7.0")) {
                        testImplementation "com.netflix.nebula:nebula-test:10.+"
                    } else {
                        testImplementation 'com.netflix.nebula:nebula-test:9.+'
                    }
                }
            }

            Provider<String> jdkVersionForTestsEnvVariable = providers.environmentVariable("JDK_VERSION_FOR_TESTS")
            Integer jdkVersionForTests = jdkVersionForTestsEnvVariable.isPresent() ? jdkVersionForTestsEnvVariable.get().toInteger() : 21
            JavaToolchainService javaToolchainService = project.extensions.getByType(JavaToolchainService)
            tasks.withType(Test).configureEach { task ->
                minHeapSize = '32m'
                maxHeapSize = '256m'
                /*
                Allows to override the JDK used to execute the test process
                 */
                javaLauncher.set(javaToolchainService.launcherFor {
                    it.languageVersion.set(JavaLanguageVersion.of(jdkVersionForTests))
                })
                if(jdkVersionForTests < 17) {
                    systemProperty('ignoreDeprecations', true)
                }
                doFirst {
                    logger.lifecycle("Executing tests with JDK: ${jdkVersionForTests}")
                }
                testLogging {
                    events "PASSED", "FAILED", "SKIPPED"
                }
            }

            if (tasks.findByName('artifactoryPublish')) {
                tasks.artifactoryPublish.dependsOn tasks.check
                gradle.taskGraph.whenReady { graph ->
                    tasks.artifactoryPublish.onlyIf {
                        graph.hasTask(':snapshot') || graph.hasTask(':devSnapshot')
                    }
                }

            }


            if (project == project.rootProject) {
                if (tasks.findByName('artifactoryDeploy')) {
                    tasks.artifactoryDeploy.dependsOn tasks.check
                    gradle.taskGraph.whenReady { graph ->
                        tasks.artifactoryDeploy.onlyIf {
                            graph.hasTask(':snapshot') || graph.hasTask(':devSnapshot')
                        }
                    }
                }
            }


            plugins.withId('com.gradle.plugin-publish') {
                gradlePlugin {
                    website = "https://github.com/nebula-plugins/${name}"
                    vcsUrl = "https://github.com/nebula-plugins/${name}.git"
                }

                tasks.publishPlugins.dependsOn tasks.check

                gradle.taskGraph.whenReady { graph ->
                    tasks.publishPlugins.onlyIf {
                        graph.hasTask(':final') || isPluginPublishingValidation
                    }
                }
            }

            plugins.withId('com.github.johnrengelman.shadow') {
                disableGradleModuleMetadataTask(project)
            }
        }

        project.afterEvaluate {
            //Disable marker tasks
            project.tasks.findAll {
                (it.name.contains("Marker") && it.name.contains('Maven')) ||
                        it.name.contains("PluginMarkerMavenPublicationToNetflixOSSRepository") ||
                        it.name.contains("PluginMarkerMavenPublicationToSonatypeRepository") ||
                        it.name.contains("publishPluginMavenPublicationToNetflixOSSRepository") ||
                        it.name.contains("publishPluginMavenPublicationToSonatypeRepository")
            }.each {
                it.enabled = false
            }

            TaskProvider validatePluginsTask = project.tasks.named('validatePlugins')
            TaskProvider publishPluginsTask = project.tasks.named('publishPlugins')
            project.plugins.withId('com.netflix.nebula.release') {
                project.tasks.withType(PublishToMavenRepository).configureEach {
                    def releasetask = project.rootProject.tasks.findByName('release')
                    if (releasetask) {
                        it.mustRunAfter(releasetask)
                        it.dependsOn(validatePluginsTask)
                        it.dependsOn(publishPluginsTask)
                    }
                }
            }

            def postReleaseTask = project.rootProject.tasks.findByName('postRelease')
            if (postReleaseTask) {
                postReleaseTask.dependsOn(project.tasks.withType(PublishToMavenRepository))
            }


            /**
             * Configure signing unless it is plugin validation
             */
            if (!isPluginPublishingValidation) {
                project.tasks.withType(Sign).configureEach {
                    it.mustRunAfter(validatePluginsTask, project.tasks.named('check'))
                }
            }

            /**
             * Disable publishing to maven repositories when running --validate-only
             */
            if (isPluginPublishingValidation) {
                project.tasks.withType(PublishToMavenRepository).configureEach {
                    it.enabled = false
                }
                project.tasks.withType(AbstractNexusStagingRepositoryTask).configureEach {
                    it.enabled = false
                }
            }
        }

    }

    private void disableGradleModuleMetadataTask(Project project) {
        project.tasks.withType(GenerateModuleMetadata).configureEach(new Action<GenerateModuleMetadata>() {
            @Override
            void execute(GenerateModuleMetadata generateModuleMetadataTask) {
                generateModuleMetadataTask.enabled = false
            }
        })
    }
}
