package com.netflix.nebula.convention

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Highly opinionated lockign setup for nebula projects.
 * Used over the "com.netflix.nebula.dependency-lock" plugin since we don't need its extra options,
 * and that plugin adds significant configuration time
 */
class DependencyLockingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.dependencyLocking {
            lockAllConfigurations()
        }
    }
}