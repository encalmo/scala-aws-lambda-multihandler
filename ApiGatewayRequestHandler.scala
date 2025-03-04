package org.encalmo.lambda

import scala.util.matching.Regex

/** Abstract base class of an http request handler. */
trait ApiGatewayRequestHandler[OtherContext] {

  type Resource = (String, Regex)

  def handleRequest(
      request: ApiGatewayRequest
  )(using LambdaContext, OtherContext): Option[ApiGatewayResponse]

  extension (r: Resource)
    inline def httpMethod: String = r._1
    inline def pathRegex: Regex = r._2
    inline def unapplySeq(request: ApiGatewayRequest) =
      if (request.httpMethod == r._1 || r._1 == "ANY")
      then r._2.unapplySeq(request.getResourceOrPathIfProxy)
      else None

  final def createApiGatewaySuccessResponse(body: String): ApiGatewayResponse =
    ApiGatewayResponse(
      statusCode = 200,
      headers = Map("Content-Type" -> "application/json"),
      isBase64Encoded = false,
      body = body
    )

  final def createApiGatewaySuccessResponse(
      json: ujson.Value
  ): ApiGatewayResponse =
    ApiGatewayResponse(
      statusCode = 200,
      headers = Map("Content-Type" -> "application/json"),
      isBase64Encoded = false,
      body = ujson.write(json)
    )

  final def returnErrorResponseWhenException(e: Throwable): ApiGatewayResponse =
    ApiGatewayResponse(
      statusCode = 501,
      headers = Map("Content-Type" -> "plain/text"),
      isBase64Encoded = false,
      body = s"${e.getClass.getName()} ${e
          .getMessage()}\n${e.getStackTrace().take(10).mkString("\n")}"
    )
}
