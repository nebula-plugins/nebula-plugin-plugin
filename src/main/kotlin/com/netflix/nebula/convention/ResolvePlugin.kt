package com.netflix.nebula.convention

import org.gradle.api.Plugin
import org.gradle.api.Project

class ResolvePlugin: Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("resolve"){
            dependsOn("dependencies")
        }
    }
}