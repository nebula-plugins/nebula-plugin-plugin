package nebula.plugin.plugin

import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.BintrayPlugin
import com.jfrog.bintray.gradle.BintrayUploadTask
import groovyx.net.http.EncoderRegistry
import groovyx.net.http.HTTPBuilder
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
 * Instructions for publishing the nebula-plugins on bintray
 */
class NebulaBintrayPublishingPlugin implements Plugin<Project> {
    private static Logger logger = Logging.getLogger(NebulaBintrayPublishingPlugin);

    protected Project project

    @Override
    void apply(Project project) {
        this.project = project

        def bintrayUpload = addBintray(project)

        project.plugins.withType(NebulaBaseMavenPublishingPlugin) { NebulaBaseMavenPublishingPlugin mavenPublishingPlugin ->
            mavenPublishingPlugin.withMavenPublication { MavenPublicationInternal mavenJava ->
                // Ensure everything is built before uploading
                bintrayUpload.dependsOn(mavenJava.publishableFiles)
            }
        }

        // Ensure our versions look like the project status before publishing
        def verifyStatus = project.tasks.create('verifyReleaseStatus')
        verifyStatus.doFirst {
            if(project.status != 'release') {
                throw new GradleException("Project should have a status of release when uploading to bintray")
            }

            def hasSnapshot = project.version.contains('-SNAPSHOT')
            if (hasSnapshot) {
                throw new GradleException("Version (${project.version}) can not have -SNAPSHOT if publishing release")
            }
        }
        bintrayUpload.dependsOn(verifyStatus)

    }

    BintrayUploadTask addBintray(Project project) {
        // Bintray Side
        project.plugins.apply(BintrayPlugin)

        BintrayExtension bintray = project.extensions.getByType(BintrayExtension)
        if (project.hasProperty('bintrayUser')) {
            bintray.user = project.property('bintrayUser')
            bintray.key = project.property('bintrayKey')
        }
        bintray.publications = ['mavenJava'] // TODO Assuming this from the other plugin
        bintray.pkg.repo = 'gradle-plugins'
        bintray.pkg.desc = project.description
        bintray.pkg.userOrg = 'nebula'
        bintray.pkg.name = project.name
        bintray.pkg.licenses = ['Apache-2.0']
        bintray.pkg.labels = ['gradle', 'nebula']
        //dryRun = project.hasProperty('dry')?project.dry:true // whether to run this as dry-run, without deploying

        // Modify bintrayUpload task
        BintrayUploadTask bintrayUpload = (BintrayUploadTask) project.tasks.find { it instanceof BintrayUploadTask }
        bintrayUpload.group = 'publishing'
        bintrayUpload.doLast {
            // Bintray uploads are not marked published, that has to be manually done. I don't for-see a scenario where
            // we wouldn't want these published.
            bintrayUpload.with {
                def http = createHttpClient(bintrayUpload)
                def repoPath = "${userOrg ?: user}/$repoName"
                
                // https://bintray.com/docs/api.html#_publish_discard_uploaded_content
                // POST /content/:subject/:repo/:package/:version/publish
                def uploadUri = "/content/$repoPath/$packageName/${project.version}/publish"
                println "Package path: $uploadUri"
                http.request(POST, JSON) {
                    uri.path = uploadUri
                    body = [discard: 'false']

                    response.success = { resp ->
                        logger.info("Published package $packageName.")
                    }
                    response.failure = { resp ->
                        throw new GradleException("Could not publish package $packageName")
                    }
                }
                
                def attributesUri = "/packages/$repoPath/$packageName/versions/${project.version}/attributes"
                addAttributes(http, attributesUri, packageName)
            }
        }

        bintrayUpload
    }

    void addAttributes(HTTPBuilder http, String attributesUri, String packageName) {
        def resourceDir = project.sourceSets.main.resources.srcDirs.find { new File(it, 'META-INF/gradle-plugins').exists() }
        if (!resourceDir) {
            return
        }

        def files = new File(resourceDir, 'META-INF/gradle-plugins').list() as List
        def plugins = files.findAll { it.endsWith('.properties') }
        def attributes = plugins.collect { "nebula.${it[0..-12]}:${project.group}:${project.name}".toString() }

        // https://bintray.com/docs/api.html#_set_attributes
        // POST /packages/:subject/:repo/:package/versions/:version/attributes
        def successful = false
        def retries = 0
        while (!successful && retries < 3) {
            http.request(POST, JSON) {
                uri.path = attributesUri
                body = [[name: 'gradle-plugin', values: attributes, type: 'string']]
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
            throw new GradleException("Could not add attribute to package $packageName")
        }
    }

    /**
     * Copied from https://github.com/bintray/gradle-bintray-plugin/blob/0.3/src/main/groovy/com/jfrog/bintray/gradle/BintrayUploadTask.groovy
     *
     * It's private on the original class, so until it's public this works.
     */
    HTTPBuilder createHttpClient(BintrayUploadTask bintrayUpload) {
        def http = new HTTPBuilder(bintrayUpload.apiUrl)

        // Must use preemptive auth for non-repeatable upload requests
        http.headers.Authorization = "Basic ${"$bintrayUpload.user:$bintrayUpload.apiKey".toString().bytes.encodeBase64()}"

        //Set an entity with a length for a stream that has the totalBytes method on it
        def er = new EncoderRegistry() {
            @Override
            InputStreamEntity encodeStream(Object data, Object contentType) throws UnsupportedEncodingException {
                if (data.metaClass.getMetaMethod("totalBytes")) {
                    InputStreamEntity entity = new InputStreamEntity((InputStream) data, data.totalBytes())
                    entity.setContentType(contentType.toString())
                    entity
                } else {
                    super.encodeStream(data, contentType)
                }
            }
        }
        http.encoders = er

        //No point in retrying non-repeatable upload requests
        http.client.httpRequestRetryHandler = new DefaultHttpRequestRetryHandler(0, false)

        //Follow permanent redirects for PUTs
        http.client.setRedirectStrategy(new DefaultRedirectStrategy() {
            @Override
            boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) {
                def redirected = super.isRedirected(request, response, context)
                return redirected || response.getStatusLine().getStatusCode() == 301
            }

            @Override
            HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context) throws org.apache.http.ProtocolException {
                URI uri = getLocationURI(request, response, context)
                String method = request.requestLine.method
                if (method.equalsIgnoreCase(HttpHead.METHOD_NAME)) {
                    return new HttpHead(uri)
                } else if (method.equalsIgnoreCase(HttpPut.METHOD_NAME)) {
                    return new HttpPut(uri)
                } else {
                    return new HttpGet(uri)
                }
            }
        })
        http
    }

}