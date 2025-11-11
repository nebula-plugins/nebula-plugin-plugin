package nebula.plugin.plugin

import nebula.test.dsl.ProjectBuilder

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

fun ProjectBuilder.disableMavenPublishTasks() {
    rawBuildScript(
        //language=kotlin
        """
tasks.withType<AbstractPublishToMaven>() {
    onlyIf { false }
}
"""
    )
}

fun ProjectBuilder.contacts() {
    rawBuildScript(
        //language=kotlin
        """
contacts {
    addPerson("nebula-plugins-oss@netflix.com") {
        moniker = "Nebula Plugins Maintainers"
        github =  "nebula-plugins"
    }
}
"""
    )
}

fun ProjectBuilder.overrideSonatypeUrlRoot(url: String) {
    rawBuildScript(
        //language=kotlin
        """
afterEvaluate {
    project.extensions.findByType<io.github.gradlenexus.publishplugin.NexusPublishExtension>()?.repositories {
        named("sonatype") {
            nexusUrl.set(`java.net`.URI("$url"))
            allowInsecureProtocol.set(true)
        }
    }
}
"""
    )
}

fun ProjectBuilder.allowInsecure() {
    rawBuildScript(
        //language=kotlin
        """
afterEvaluate {
    publishing {
        repositories {
            named<MavenArtifactRepository>("sonatype"){
                isAllowInsecureProtocol = true
            }
        }
    }
}
"""
    )
}