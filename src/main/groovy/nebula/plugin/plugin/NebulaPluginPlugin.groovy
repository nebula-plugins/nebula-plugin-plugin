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

import nebula.plugin.bintray.BintrayPlugin
import nebula.plugin.release.ReleaseExtension
import nebula.plugin.release.ReleasePlugin
import org.ajoberstar.gradle.git.release.base.ReleasePluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph

/**
 * Provide an environment for a Gradle plugin
 */
class NebulaPluginPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply(BintrayPlugin)
        project.plugins.apply(ReleasePlugin)

        project.logger.lifecycle("Enabling Nebula for this project ${project.name}")

        if (!project.group) {
            project.group = 'com.netflix.nebula'
        }

        project.tasks.getByName('verifyReleaseStatus').actions.clear()
        project.tasks.getByName('verifySnapshotStatus').actions.clear()

        project.tasks.matching { it.name == 'bintrayUpload' || it.name == 'artifactoryPublish'}.all { Task task ->
            task.mustRunAfter('build')
            project.tasks.release.dependsOn(task)
        }

        project.tasks.matching { it.name == 'bintrayUpload' }.all { Task task ->
            project.gradle.taskGraph.whenReady { TaskExecutionGraph graph ->
                task.onlyIf {
                    graph.hasTask(':final') || graph.hasTask(':candidate')
                }
            }
        }

        project.tasks.matching { it.name == 'artifactoryPublish'}.all { Task task ->
            project.gradle.taskGraph.whenReady { TaskExecutionGraph graph ->
                task.onlyIf {
                    graph.hasTask(':snapshot')
                }
            }
        }

        ReleasePluginExtension releaseExtension = project.extensions.findByType(ReleasePluginExtension)
        releaseExtension.with {
            defaultVersionStrategy = nebula.plugin.release.NetflixOssStrategies.SNAPSHOT
        }

        if (project.hasProperty('release.travisci') && project.property('release.travisci').toBoolean()) {
            project.tasks.release.deleteAllActions()
            project.tasks.prepare.deleteAllActions()
            ReleaseExtension nebulaRelease = project.extensions.findByType(ReleaseExtension)
            nebulaRelease.with {
                addReleaseBranchPattern(/HEAD/)
                addReleaseBranchPattern(/v?\d+\.\d+\.\d+/)
                addReleaseBranchPattern(/gradle-\d+\.\d+/)
            }
        }
    }
}