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

import nebula.plugin.publishing.NebulaOssPublishingExtension
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.plugins.signing.Sign

/**
 * Provide an environment for a Gradle plugin
 */
class NebulaPluginPlugin implements Plugin<Project> {
    static final GRADLE_PLUGIN_IDS = ['groovy',
                                      'idea',
                                      'jacoco',
                                      'com.gradle.plugin-publish',
                                      'java-gradle-plugin']

    static final THIRDPARTY_PLUGIN_IDS = ['com.github.kt3k.coveralls']

    static final NEBULA_PLUGIN_IDS = ['nebula.contacts',
                                      'nebula.dependency-lock',
                                      'nebula.facet',
                                      'nebula.info',
                                      'nebula.java-cross-compile',
                                      'nebula.javadoc-jar',
                                      'nebula.maven-apache-license',
                                      'nebula.maven-publish',
                                      'nebula.publish-verification',
                                      'nebula.nebula-release',
                                      'nebula.oss-publishing', 
                                      'nebula.optional-base',
                                      'nebula.source-jar',
                                      'nebula.integtest']

    static final PLUGIN_IDS = GRADLE_PLUGIN_IDS + THIRDPARTY_PLUGIN_IDS + NEBULA_PLUGIN_IDS

    @Override
    void apply(Project project) {
        project.with {
            def nebulaOssPublishingExtension = project.rootProject.extensions.findByType(NebulaOssPublishingExtension) ?: project.rootProject.extensions.create("nebulaOssPublishing", NebulaOssPublishingExtension)
            nebulaOssPublishingExtension.packageGroup.set("com.netflix")

            PLUGIN_IDS.each { plugins.apply(it) }

            if (!group) {
                group = 'com.netflix.nebula'
            }

            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8

            repositories {
                maven { url 'https://plugins.gradle.org/m2/' }
            }

            dependencies {
                implementation gradleApi()
                testImplementation 'com.netflix.nebula:nebula-test:7.+'
            }


            jacocoTestReport {
                reports {
                    xml.enabled = true // coveralls plugin depends on xml format report
                    html.enabled = true
                }
            }

            tasks.withType(Test) { task ->
                minHeapSize = '32m'
                maxHeapSize = '256m'
                doFirst {
                    // Add the execution data only if the task runs
                    jacocoTestReport.executionData.from = files("$buildDir/jacoco/${task.name}.exec")
                }
                testLogging {
                    events "PASSED", "FAILED", "SKIPPED"
                }
            }

            if(tasks.findByName('artifactoryPublish')) {
                tasks.artifactoryPublish.dependsOn tasks.check
                gradle.taskGraph.whenReady { graph ->
                    tasks.artifactoryPublish.onlyIf {
                        graph.hasTask(':snapshot') || graph.hasTask(':devSnapshot')
                    }
                }

            }


            if (project == project.rootProject) {
                if(tasks.findByName('artifactoryDeploy')) {
                    tasks.artifactoryDeploy.dependsOn tasks.check
                    gradle.taskGraph.whenReady { graph ->
                        tasks.artifactoryDeploy.onlyIf {
                            graph.hasTask(':snapshot') || graph.hasTask(':devSnapshot')
                        }
                    }
                }
            }


            plugins.withId('com.gradle.plugin-publish') {
                pluginBundle {
                    website = "https://github.com/nebula-plugins/${name}"
                    vcsUrl = "https://github.com/nebula-plugins/${name}.git"
                    description = project.description


                }

                tasks.publishPlugins.dependsOn tasks.check

                gradle.taskGraph.whenReady { graph ->
                    tasks.publishPlugins.onlyIf {
                        graph.hasTask(':final')
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
            project.tasks.withType(PublishToMavenRepository).configureEach {
                it.mustRunAfter(project.rootProject.tasks.named('release'))
                it.dependsOn(validatePluginsTask)
                it.dependsOn(publishPluginsTask)
            }

            def postReleaseTask = project.rootProject.tasks.findByName('postRelease')
            if(postReleaseTask) {
                postReleaseTask.dependsOn( project.tasks.withType(PublishToMavenRepository))
            }


            project.tasks.withType(Sign).configureEach {
                it.mustRunAfter(validatePluginsTask, project.tasks.named('check'), publishPluginsTask)
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
