package nebula.plugin.plugin

import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.BintrayUploadTask
import org.gradle.api.Project

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * Instructions for publishing the nebula-plugins on bintray
 */
class NebulaBintrayPluginConfiguration {
    private static Logger logger = Logging.getLogger(NebulaBintrayPluginConfiguration)

    void configure(Project project) {
        BintrayExtension bintray = project.extensions.getByType(BintrayExtension)
        BintrayUploadTask bintrayUpload = (BintrayUploadTask) project.tasks.find { it instanceof BintrayUploadTask }
        bintrayUpload.doFirst {
            bintray.pkg.version {
                name = project.version
                vcsTag = project.version
            }

            // this doesn't seem to work currently but it should
            def gradlePluginAttributes = calculateAttributes(project)
            if (gradlePluginAttributes) {
                bintray.pkg.version.attributes = ['gradle-plugin': gradlePluginAttributes]
            }
        }
        bintrayUpload.doLast {
            def gradlePluginAttributes = calculateAttributes(project)
            if (gradlePluginAttributes) {
                bintrayUpload.with {
                    def http = BintrayHttpClientFactory.create(apiUrl, user, apiKey)
                    def repoPath = "${userOrg ?: user}/${repoName}"
                    def attributesUri = "/packages/$repoPath/$packageName/versions/${project.version}/attributes"
                    // https://bintray.com/docs/api.html#_set_attributes
                    // POST /packages/:subject/:repo/:package/versions/:version/attributes
                    def successful = false
                    def retries = 0
                    while (!successful && retries < 3) {
                        http.request(POST, JSON) {
                            uri.path = attributesUri
                            body = [[name: 'gradle-plugin', values: gradlePluginAttributes, type: 'string']]
                            response.success = { resp ->
                                logger.info("Added gradle-plugins attribute to package $packageName.")
                                successful = true
                            }
                            response.failure = { resp ->
                                logger.info("${resp.status} try $retries")
                                retries++
                            }
                        }
                    }
                    if (retries == 3) {
                        throw new RuntimeException("Could not add attribute to package $packageName")
                    }
                }
            }
        }
    }

    List<String> calculateAttributes(Project project) {
        def resourceDir = project.sourceSets.main.resources.srcDirs.find { new File(it, 'META-INF/gradle-plugins').exists() }
        if (resourceDir) {
            def files = new File(resourceDir, 'META-INF/gradle-plugins').list() as List
            def plugins = files.findAll { it.endsWith('.properties') }
            plugins.collect { "nebula.${it[0..-12]}:${project.group}:${project.name}".toString() } 
        } else {
            []
        }   
    }
}