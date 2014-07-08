package nebula.plugin.plugin

import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.BintrayUploadTask
import org.gradle.api.Project

/**
 * Instructions for publishing the nebula-plugins on bintray
 */
class NebulaBintrayPluginConfiguration {
    void configure(Project project) {
        BintrayExtension bintray = project.extensions.getByType(BintrayExtension)
        BintrayUploadTask bintrayUpload = (BintrayUploadTask) project.tasks.find { it instanceof BintrayUploadTask }
        bintrayUpload.doFirst {
            def resourceDir = project.sourceSets.main.resources.srcDirs.find { new File(it, 'META-INF/gradle-plugins').exists() }
            if (resourceDir) {
                def files = new File(resourceDir, 'META-INF/gradle-plugins').list() as List
                def plugins = files.findAll { it.endsWith('.properties') }
                def attributes = plugins.collect { "nebula.${it[0..-12]}:${project.group}:${project.name}".toString() }
        
                bintray.pkg.version {
                    name = project.version
                    vcsTag = project.version
                    attributes = ['gradle-plugin': attributes]
                }
            }
        }
    }

}