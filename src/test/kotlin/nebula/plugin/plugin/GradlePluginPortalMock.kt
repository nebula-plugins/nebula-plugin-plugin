package nebula.plugin.publishing

import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response

fun ClientAndServer.mockGradlePluginPortal(pluginId: String) {
    `when`(
        request()
            .withMethod("POST")
            .withPath("/api/v1/publish/versions/validate/$pluginId")
    )
        .respond {
            response().withBody(
                //language=json
                """{"failed": false}""")
        }
}