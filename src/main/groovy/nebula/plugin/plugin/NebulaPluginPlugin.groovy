/*
 * Copyright 2014-2015 Netflix, Inc.
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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.tasks.testing.Test

/**
 * Provide an environment for a Gradle plugin
 */
class NebulaPluginPlugin implements Plugin<Project> {
    static
    final DEFAULT_PLUGINS = ['groovy',
                             'idea',
                             'jacoco',
                             'nebula.info',
                             'nebula.contacts',
                             'nebula.maven-publish',
                             'nebula.nebula-release',
                             'nebula.nebula-bintray',
                             'com.gradle.plugin-publish',
                             'nebula.javadoc-jar',
                             'nebula.source-jar',
                             'nebula.maven-apache-license',
                             'com.github.kt3k.coveralls']

    @Override
    void apply(Project project) {
        assertHasPlugin(project, 'com.gradle.plugin-publish')
        project.with {
            DEFAULT_PLUGINS.each { plugins.apply(it) }

            if (!group) {
                group = 'com.netflix.nebula'
            }

            dependencies {
                compile localGroovy()
                compile gradleApi()
                testCompile('com.netflix.nebula:nebula-test:4.0.0') {
                    exclude group: 'org.codehaus.groovy'
                }
            }

            sourceCompatibility = 1.7
            targetCompatibility = 1.7

            repositories {
                jcenter()
            }

            test {
                jvmArgs "-XX:MaxPermSize=256m"
            }

            jacocoTestReport {
                reports {
                    xml.enabled = true // coveralls plugin depends on xml format report
                    html.enabled = true
                }
            }

            tasks.withType(Test) { task ->
                jacocoTestReport.executionData += files("$buildDir/jacoco/${task.name}.exec")
            }

            pluginBundle {
                website = "https://github.com/nebula-plugins/${project.name}"
                vcsUrl = "https://github.com/nebula-plugins/${project.name}.git"
                description = project.description

                mavenCoordinates {
                    groupId = project.group
                    artifactId = project.name
                }
            }

            project.gradle.taskGraph.whenReady { TaskExecutionGraph graph ->
                project.tasks.bintrayUpload.onlyIf {
                    graph.hasTask(':final') || graph.hasTask(':candidate')
                }
                project.tasks.artifactoryPublish.onlyIf {
                    graph.hasTask(':snapshot') || graph.hasTask(':devSnapshot')
                }
            }
        }
    }

    static def assertHasPlugin(Project project, String id) {
        assert project.plugins.findPlugin(id) : "The ${id} plugin must be applied before this plugin"
    }
}
