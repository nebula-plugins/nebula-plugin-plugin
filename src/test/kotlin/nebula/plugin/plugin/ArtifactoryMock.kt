package nebula.plugin.publishing

import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response

fun expectPublicationWithChecksums(mockServer: ClientAndServer, path: String): List<(ClientAndServer) -> Unit> {
    val verifications = mutableListOf<(ClientAndServer) -> Unit>()
    val paths = listOf(
        path,
        "$path.md5",
        "$path.sha1",
        "$path.sha256",
        "$path.sha512"
    )
    paths.forEach {
        val request = request(it).withMethod("PUT")
        mockServer
            .`when`(request)
            .respond { response().withStatusCode(200) }
        verifications.add { it.verify(request) }
    }
    return verifications
}

fun expectSignedPublicationWithChecksums(mockServer: ClientAndServer, path: String): List<(ClientAndServer) -> Unit> {
    return expectPublicationWithChecksums(mockServer, path) +
            expectPublicationWithChecksums(mockServer, "$path.asc")
}

class Publication(
    val mockServer: ClientAndServer,
    val repo: String,
    val groupName: String,
    val moduleName: String,
    val version: String,
) {
    private val groupPath = groupName.replace(".", "/")
    private val modulePath = "$repo/$groupPath/${moduleName}"
    private val fullPath = "$modulePath/${version}"
    private val verifications = mutableListOf<(ClientAndServer) -> Unit>()
    fun withArtifact(classifier: String, extension: String) {
        verifications.addAll(
            expectSignedPublicationWithChecksums(
                mockServer,
                "/$fullPath/$moduleName-${version}-$classifier.$extension"
            )
        )
    }

    fun withGradleModuleMetadata() {
        verifications.addAll(
            expectSignedPublicationWithChecksums(mockServer, "/$fullPath/$moduleName-${version}.module")
        )
    }

    fun withArtifact(extension: String) {
        verifications.addAll(
            expectSignedPublicationWithChecksums(mockServer, "/$fullPath/$moduleName-${version}.$extension")
        )
    }

    fun verifications(): List<(ClientAndServer) -> Unit> {
        return verifications
    }
}

fun ClientAndServer.expectPublication(
    repo: String,
    groupName: String,
    moduleName: String,
    version: String,
    additional: Publication.() -> Unit = {}
): VerificationsContainer {
    val groupPath = groupName.replace(".", "/")
    val modulePath = "$repo/$groupPath/${moduleName}"
    val fullPath = "$modulePath/${version}"
    val allVersionsXml = listOf("0.0.0").joinToString("/n") { "      <version>$it</version>" }
    `when`(request("/$modulePath/maven-metadata.xml"))
        .respond {
            response().withBody(
                """
<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>${groupName}</groupId>
  <artifactId>${moduleName}</artifactId>
  <versioning>
    <latest>0.0.0</latest>
    <release>0.0.0</release>
    <versions>
$allVersionsXml
    </versions>
    <lastUpdated>20210816163607</lastUpdated>
  </versioning>
</metadata>
"""
            ).withStatusCode(200)
        }
    val verifications = mutableListOf<(ClientAndServer) -> Unit>()
    verifications.addAll(
        expectSignedPublicationWithChecksums(this, "/$fullPath/${moduleName}-${version}.pom")
    )
    verifications.addAll(
        expectPublicationWithChecksums(this, "/$modulePath/maven-metadata.xml")
    )

    verifications.addAll(
        Publication(this, repo, groupName, moduleName, version).apply { additional() }.verifications()
    )
    return VerificationsContainer(verifications)
}

class VerificationsContainer(val verifications: List<(ClientAndServer) -> Unit>) {
    fun verify(mockServer: ClientAndServer) {
        verifications.forEach { verification ->
            verification(mockServer)
        }
    }
}