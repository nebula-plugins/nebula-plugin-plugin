package nebula.plugin.plugin

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.jfrog.gradle.plugin.artifactory.ArtifactoryPublicationsPlugin

/**
 * Instructions for publishing snapshots oss.jfrog.org
 */
class NebulaOJOPublishingPlugin implements Plugin<Project> {
    private static Logger logger = Logging.getLogger(NebulaBintrayPublishingPlugin);

    protected Project project

    @Override
    void apply(Project project) {
        this.project = project

        applyArtifactory()

        // Ensure our versions look like the project status before publishing
        def verifyStatus = project.tasks.create('verifySnapshotStatus')
        verifyStatus.doFirst {
            if(project.status != 'integration') {
                throw new GradleException("Project should have a status of integration when uploading to OJO")
            }

            def hasSnapshot = project.version.contains('-SNAPSHOT')
            if (!hasSnapshot) {
                throw new GradleException("Version (${project.version}) must have -SNAPSHOT if publishing to OJO")
            }
        }
        project.tasks.matching { it.name == 'artifactoryPublish' }.all {
            it.dependsOn(verifyStatus)
        }

    }

    def applyArtifactory() {
        def artifactoryPublicationsPlugin = project.plugins.apply(ArtifactoryPublicationsPlugin)

        def convention = artifactoryPublicationsPlugin.getArtifactoryPluginConvention(project) // Will peak into rootProject

        convention.contextUrl = 'https://oss.jfrog.org'

        // TODO Conditionalize this, we only want to resolve from oss.jfrog.org when someone explicitly says so
//        convention.resolve {
//            repository {
//                repoKey = 'libs-release'
//            }
//        }
        convention.publish {
            repository {
                repoKey = 'oss-snapshot-local' //The Artifactory repository key to publish to
                //when using oss.jfrog.org the credentials are from Bintray. For local build we expect them to be found in
                //~/.gradle/gradle.properties, otherwise to be set in the build server
                // COnditionalize for the users who don't have bintray credentials setup
                if (project.hasProperty('bintrayUser')) {
                    username = project.property('bintrayUser')
                    password = project.property('bintrayKey')
                }
            }
            defaults {
                publications 'mavenJava'
            }
        }
    }
}