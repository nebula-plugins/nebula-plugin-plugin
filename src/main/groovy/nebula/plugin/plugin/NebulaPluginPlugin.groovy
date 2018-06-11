/*
 * Copyright 2014-2017 Netflix, Inc.
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

import org.apache.maven.model.Dependency
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.tasks.testing.Test
/**
 * Provide an environment for a Gradle plugin
 */
class NebulaPluginPlugin implements Plugin<Project> {
    static final GRADLE_PLUGIN_IDS = ['com.gradle.build-scan',
                                      'groovy',
                                      'idea',
                                      'jacoco',
                                      'com.gradle.plugin-publish']

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
                                      'nebula.nebula-bintray', // nebula-bintray needs to happened after nebula-release since version isn't lazy in the bintray extension
                                      'nebula.optional-base',
                                      'nebula.source-jar',
                                      'nebula.integtest']

    static final PLUGIN_IDS = GRADLE_PLUGIN_IDS + THIRDPARTY_PLUGIN_IDS + NEBULA_PLUGIN_IDS

    @Override
    void apply(Project project) {
        project.with {
            PLUGIN_IDS.each { plugins.apply(it) }

            if (!group) {
                group = 'com.netflix.nebula'
            }

            sourceCompatibility = 1.7
            targetCompatibility = 1.7

            repositories {
                maven { url 'https://plugins.gradle.org/m2/' }
            }

            dependencies {
                compile gradleApi()
                testCompile 'com.netflix.nebula:nebula-test:6.+'
            }

            buildScan {
                licenseAgreementUrl = 'https://gradle.com/terms-of-service'
                licenseAgree = 'yes'
                publishAlways()
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
                jvmArgs "-XX:MaxPermSize=512m"
                doFirst {
                    // Add the execution data only if the task runs
                    jacocoTestReport.executionData += files("$buildDir/jacoco/${task.name}.exec")
                }
            }

            tasks.bintrayUpload.dependsOn tasks.check
            tasks.artifactoryPublish.dependsOn tasks.check

            gradle.taskGraph.whenReady { graph ->
                tasks.bintrayUpload.onlyIf {
                    graph.hasTask(':final') || graph.hasTask(':candidate')
                }
                tasks.artifactoryPublish.onlyIf {
                    graph.hasTask(':snapshot') || graph.hasTask(':devSnapshot')
                }
            }

            if (project == project.rootProject) {
                tasks.artifactoryDeploy.dependsOn tasks.check
                gradle.taskGraph.whenReady { graph ->
                    tasks.bintrayPublish.onlyIf {
                        graph.hasTask(':final') || graph.hasTask(':candidate')
                    }
                    tasks.artifactoryDeploy.onlyIf {
                        graph.hasTask(':snapshot') || graph.hasTask(':devSnapshot')
                    }
                }
            }

            plugins.withId('com.gradle.plugin-publish') {
                pluginBundle {
                    website = "https://github.com/nebula-plugins/${name}"
                    vcsUrl = "https://github.com/nebula-plugins/${name}.git"
                    description = project.description

                    mavenCoordinates {
                        groupId = project.group
                        artifactId = project.name
                    }
                }
                tasks.publishPlugins.dependsOn tasks.check
                tasks.bintrayUpload.dependsOn tasks.publishPlugins

                enableResolvedVersionInPluginPortalPom(project)

                gradle.taskGraph.whenReady { graph ->
                    tasks.publishPlugins.onlyIf {
                        graph.hasTask(':final')
                    }
                }
            }
        }
    }

    def enableResolvedVersionInPluginPortalPom(Project project) {
        project.pluginBundle {
            withDependencies { List<Dependency> deps ->
                def resolvedDeps = project.configurations.runtimeClasspath.incoming.resolutionResult.allDependencies
                deps.each { Dependency dep ->
                    String group = dep.groupId
                    String artifact = dep.artifactId
                    ResolvedDependencyResult found = resolvedDeps.find { r ->
                        (r.requested instanceof ModuleComponentSelector) &&
                                (r.requested.group == group) &&
                                (r.requested.module == artifact)
                    }

                    dep.version = found.selected.moduleVersion.version
                }
            }
        }
    }
}
