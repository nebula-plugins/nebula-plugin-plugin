package nebula.plugin.plugin

import nebula.test.dsl.ProjectBuilder

//language=kotlin
const val DISABLE_PUBLISH_TASKS: String = """
afterEvaluate {
    tasks.withType<AbstractPublishToMaven>() {
        onlyIf { false }
    }
    project.tasks.findByName("publishPlugins")?.onlyIf { false }
}
"""

//language=kotlin
const val DISABLE_MAVEN_CENTRAL_TASKS: String = """
project.tasks.findByName("initializeSonatypeStagingRepository")?.onlyIf { false }
project.tasks.findByName("closeSonatypeStagingRepository")?.onlyIf { false }
project.tasks.findByName("releaseSonatypeStagingRepository")?.onlyIf { false }
"""

//language=kotlin
fun ProjectBuilder.mockSign() {
    rawBuildScript(
        //language=kotlin
        """
afterEvaluate {
project.extensions.getByType<SigningExtension>().signatories = object:
    org.gradle.plugins.signing.signatory.SignatoryProvider<Signatory> {
    override fun configure(settings: SigningExtension?, closure: groovy.lang.Closure<*>?) {
    }

    override fun getDefaultSignatory(project: Project?): Signatory {
        return object: Signatory {
            override fun getName(): String {
                return "name"
            }

            override fun sign(toSign: java.io.InputStream?, destination: java.io.OutputStream?) {
                destination?.write( sign(toSign))
            }

            override fun sign(toSign: java.io.InputStream?): ByteArray {
                return "signature".toByteArray()
            }

            override fun getKeyId(): String {
                return "id"
            }
        }
    }

    override fun getSignatory(name: String?): Signatory {
        return object: Signatory {
            override fun getName(): String {
                return "name"
            }

            override fun sign(toSign: java.io.InputStream?, destination: java.io.OutputStream?) {
                destination?.write( sign(toSign))
            }

            override fun sign(toSign: java.io.InputStream?): ByteArray {
                return "signature".toByteArray()
            }

            override fun getKeyId(): String {
                return "id"
            }
        }
    }
}
}
"""
    )
}

fun ProjectBuilder.nebulaOssPublishing(netflixOssRepositoryBaseUrl: String) {
    rawBuildScript(
        """
nebulaOssPublishing {
    signingKey = "something"
    signingPassword = "something"
    netflixOssRepositoryBaseUrl = "$netflixOssRepositoryBaseUrl"
}
"""
    )
}
