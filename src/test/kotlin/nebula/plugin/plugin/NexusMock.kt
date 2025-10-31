package nebula.plugin.publishing

import org.mockserver.integration.ClientAndServer
import org.mockserver.mock.action.ExpectationResponseCallback
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse
import org.mockserver.model.HttpResponse.response

class NexusCallback : ExpectationResponseCallback {
    var status: String = "OPEN"
    override fun handle(httpRequest: HttpRequest): HttpResponse {
        return if (httpRequest.method.equals("GET") && httpRequest.path.matches("/staging/repository/1")) {
            response()
                .withBody(
                    //language=json
                    """{"repositoryId":"1", "transitioning": false, "type": "$status"}"""
                )
                .withStatusCode(200)
        } else if (httpRequest.method.equals("POST") && httpRequest.path.equals("/staging/bulk/close")) {
            status = "CLOSED"
            response()
                .withBody(
                    //language=json
                    """{"data": {"stagedRepositoryIds":["1"], "description": "$status"}}"""
                )
                .withStatusCode(200)
        } else if (httpRequest.method.equals("POST") && httpRequest.path.equals("/staging/bulk/promote")) {
            status = "RELEASED"
            response()
                .withBody(
                    //language=json
                    """{"data": {"stagedRepositoryIds":["1"], "description": "$status"}}"""
                )
                .withStatusCode(200)
        } else {
            response().withStatusCode(404)
        }
    }
}

val callback = NexusCallback()
fun ClientAndServer.mockNexus() {
    `when`(
        request()
            .withMethod("GET")
            .withPath("/staging/profiles/")
    )
        .respond {
            response()
                .withBody(
                    //language=json
                    """{"data": [{"id":"1", "name": "test"}]}"""
                )
                .withStatusCode(200)
        }
    `when`(
        request()
            .withMethod("POST")
            .withPath("/staging/profiles/.*/start")
    )
        .respond {
            response()
                .withBody(
                    //language=json
                    """{"data":{"stagedRepositoryId":"1"}}"""
                )
                .withStatusCode(200)
        }
    `when`(
        request()
            .withMethod("POST")
            .withPath("/staging/bulk/.*")
    ).respond(callback)
    `when`(
        request()
            .withMethod("GET")
            .withPath("/staging/repository/.*")
    ).respond(callback)
    `when`(
        request()
            .withMethod("GET")
            .withPath("/staging/profile_repositories/.*")
    ).respond(callback)
}

