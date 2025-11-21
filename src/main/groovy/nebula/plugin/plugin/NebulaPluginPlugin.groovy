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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.plugin.devel.tasks.ValidatePlugins
import org.gradle.plugins.signing.Sign
import org.jspecify.annotations.NullMarked

import javax.inject.Inject

/**
 * Provide an environment for a Gradle plugin
 */
@NullMarked
class NebulaPluginPlugin implements Plugin<Project> {
    static final GRADLE_PLUGIN_IDS = ['groovy',
                                      'idea',
                                      'com.gradle.plugin-publish',
                                      'java-gradle-plugin']

    static final NEBULA_PLUGIN_IDS = ['com.netflix.nebula.contacts',
                                      'com.netflix.nebula.dependency-lock',
                                      'com.netflix.nebula.info',
                                      'com.netflix.nebula.javadoc-jar',
                                      'com.netflix.nebula.maven-apache-license',
                                      'com.netflix.nebula.maven-publish',
                                      'com.netflix.nebula.publish-verification',
                                      'com.netflix.nebula.release',
                                      'com.netflix.nebula.oss-publishing',
                                      'com.netflix.nebula.source-jar']

    static final OPTIONAL_TESTING_PLUGIN_IDS = ['com.netflix.nebula.facet', 'com.netflix.nebula.integtest']

    static final PLUGIN_IDS = GRADLE_PLUGIN_IDS + NEBULA_PLUGIN_IDS

    private final ProviderFactory providers
    private boolean isPluginPublishingValidation

    @Inject
    NebulaPluginPlugin(ProviderFactory providerFactory) {
        this.providers = providerFactory
    }

    @Override
    void apply(Project project) {
        project.group = 'com.netflix.nebula'

        this.isPluginPublishingValidation = project.gradle.startParameter.taskNames.contains('--validate-only')
        project.plugins.withId("com.netflix.nebula.oss-publishing") {
            NebulaOssPublishingExtension ossPublishingExt = project.rootProject.extensions.findByType(NebulaOssPublishingExtension)
            ossPublishingExt.packageGroup.set("com.netflix")
            ossPublishingExt.netflixOssRepository.set("gradle-plugins")
        }
        ArchRulesUtil.setupArchRules(project)
        project.with {
            PLUGIN_IDS.each { plugins.apply(it) }
            boolean integTest = !project.hasProperty("nebula.integTest") ||
                    Boolean.parseBoolean(project.property("nebula.integTest").toString())
            if (integTest) {
                OPTIONAL_TESTING_PLUGIN_IDS.each { plugins.apply(it) }
            }
            tasks.withType(ValidatePlugins).configureEach {
                it.enableStricterValidation.set(true)
            }

            JavaPluginExtension javaPluginExtension = extensions.getByType(JavaPluginExtension)
            JavaToolchainSpec toolchainSpec = javaPluginExtension.toolchain
            toolchainSpec.languageVersion.convention(JavaLanguageVersion.of(17))

            repositories {
                maven {
                    url = 'https://plugins.gradle.org/m2/'
                }
                mavenCentral()
            }

            tasks.withType(Test).configureEach {
                useJUnitPlatform()
            }

            dependencies {
                //we apply plugin-plugin in nebula-test to and we don't want to create cycles which confuses gradle locks
                if (project.name != 'nebula-test') {
                    testImplementation "com.netflix.nebula:nebula-test:11.+"
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
                if (jdkVersionForTests < 17) {
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
        }

        project.afterEvaluate {
            TaskProvider validatePluginsTask = project.tasks.named('validatePlugins')
            TaskProvider publishPluginsTask = project.tasks.named('publishPlugins')
            project.plugins.withId('com.netflix.nebula.release') {
                project.tasks.withType(PublishToMavenRepository).configureEach {
                    it.dependsOn(validatePluginsTask)
                    it.dependsOn(publishPluginsTask)
                }
            }

            /**
             * Disable signing and publishing when running --validate-only
             */
            if (isPluginPublishingValidation) {
                project.tasks.withType(Sign).configureEach {
                    it.enabled = false
                }
                project.tasks.withType(PublishToMavenRepository).configureEach {
                    it.enabled = false
                }
                project.tasks.withType(AbstractNexusStagingRepositoryTask).configureEach {
                    it.enabled = false
                }
            }
        }

    }
}
