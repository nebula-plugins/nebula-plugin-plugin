package nebula.plugin.plugin

import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.BintrayPlugin
import com.jfrog.bintray.gradle.BintrayUploadTask
import groovyx.net.http.EncoderRegistry
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.HttpResponseDecorator
import nebula.plugin.publishing.maven.NebulaBaseMavenPublishingPlugin
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpHead
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.entity.InputStreamEntity
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler
import org.apache.http.impl.client.DefaultRedirectStrategy
import org.apache.http.protocol.HttpContext
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal

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
                            logger.error(resp.toString())
                            throw new GradleException("Could not publish package $packageName")
                        }
                    }
                }
            }
        }
    }
}