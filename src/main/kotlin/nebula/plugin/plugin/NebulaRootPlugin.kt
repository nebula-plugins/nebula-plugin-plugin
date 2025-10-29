/*
 * Copyright 2025 Netflix, Inc.
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
package nebula.plugin.plugin;

import nebula.plugin.publishing.NebulaOssPublishingExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType

/**
 * Provide an environment for the root project of a multi-project Nebula repo
 */
class NebulaRootPlugin : Plugin<Project> {

    @Override
    override fun apply(project: Project) {
        project.plugins.apply("com.netflix.nebula.contacts")
        project.plugins.apply("com.netflix.nebula.dependency-lock")
        project.plugins.apply("com.netflix.nebula.info")
        project.plugins.apply("com.netflix.nebula.release")
        project.plugins.apply("com.netflix.nebula.oss-publishing")
        project.extensions.findByType<NebulaOssPublishingExtension>()?.apply {
            packageGroup.set("com.netflix")
            netflixOssRepository.set("gradle-plugins")
        }
        if (project.group.toString().isBlank()) {
            project.group = "com.netflix.nebula"
        }
    }
}
