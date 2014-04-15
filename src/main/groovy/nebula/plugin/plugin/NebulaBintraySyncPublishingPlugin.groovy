package nebula.plugin.plugin

import com.jfrog.bintray.gradle.BintrayUploadTask
import groovyx.net.http.HttpResponseDecorator
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.Method.POST

/**
 * If provided with a sonatypeUsername and sonatypePassword, this plugin will enhance the NebulaBintrayPublishingPlugin
 * to sync to Maven Central.
 */
class NebulaBintraySyncPublishingPlugin implements Plugin<Project> {
    private static Logger logger = Logging.getLogger(NebulaBintraySyncPublishingPlugin);

    protected Project project

    @Override
    void apply(Project project) {
        this.project = project

        NebulaBintrayPublishingPlugin bintrayPublishingPlugin = project.plugins.apply(NebulaBintrayPublishingPlugin)

        BintrayUploadTask bintrayUpload = (BintrayUploadTask) project.tasks.find { it instanceof BintrayUploadTask }

        if (project.hasProperty('sonatypeUsername') && project.hasProperty('sonatypePassword')) {
            def sonatypeUsername = project.sonatypeUsername
            def sonatypePassword = project.sonatypePassword
            bintrayUpload.doLast {
                // Bintray uploads are not marked published, that has to be manually done. I don't for-see a scenario where
                // we wouldn't want these published.
                bintrayUpload.with {
                    def http = bintrayPublishingPlugin.createHttpClient(bintrayUpload)
                    def repoPath = "${userOrg ?: user}/$repoName"
                    // /maven_central_sync/:subject/:repo/:package/versions/:version?username=:username&password=:password[&close=0/1]
                    def uploadUri = "/maven_central_sync/$repoPath/$packageName/versions/${project.version}"
                    println "Package Sync: $uploadUri"
                    http.request(POST, JSON) {
                        uri.path = uploadUri
                        uri.query = [username: sonatypeUsername, password: sonatypePassword, close: 1]

                        response.success = { resp ->
                            logger.info("Synced package $packageName.")
                        }
                        response.failure = { HttpResponseDecorator resp ->
                            logger.error("Unable to sync to Maven Central, please do manually at https://bintray.com/nebula/gradle-plugins/${project.name}/${project.version}/view/central")
                            logger.error(resp.data)
                            //throw new GradleException("Could not publish package $packageName")
                        }
                    }
                }
            }
        }
    }
}