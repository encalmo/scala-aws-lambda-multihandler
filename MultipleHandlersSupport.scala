package org.encalmo.lambda

import org.encalmo.utils.JsonUtils.*
import ujson.Arr
import ujson.Obj
import upickle.default.*

import scala.util.control.NonFatal

final case class UnsupportedRequestError(message: String) extends Exception(message) with ApiGatewayBadRequestException

final case class UnsupportedEventError(message: String) extends Exception(message)

trait MultipleHandlersSupport extends EventHandler, EventHandlerTag {

  private val functionNameRegex = "\"(?:function|functionName|handler)\"(?:\\s*):(?:\\s*)\"(.+?)\"".r
  private val requestPathRegex = "\"path\"(?:\\s*):(?:\\s*)\"(.+?)\"".r

  final override inline def getEventHandlerTag(event: String): Option[String] =
    try {
      functionNameRegex
        .findFirstMatchIn(event)
        .orElse(requestPathRegex.findFirstMatchIn(event))
        .flatMap(m => Option(m.group(1)))
    } catch { case NonFatal(_) => None }

  def apiGatewayRequestHandlers: Iterable[ApiGatewayRequestHandler[ApplicationContext]]

  def sqsEventHandlers: Iterable[SqsEventHandler[ApplicationContext]]

  def genericEventHandlers: Iterable[GenericEventHandler[ApplicationContext]]

  lazy val sqsEventHandlersMap: Map[String, SqsEventHandler[ApplicationContext]] =
    sqsEventHandlers
      .map(handler =>
        (
          handler.functionName
            .getOrElse {
              val className = handler.getClass.getSimpleName()
              if (className.endsWith("Handler")) then className.dropRight(7)
              else className
            },
          handler
        )
      )
      .toMap

  lazy val genericEventHandlersMap: Map[String, GenericEventHandler[ApplicationContext]] = genericEventHandlers
    .map(handler =>
      (
        handler.functionName
          .getOrElse {
            val className = handler.getClass.getSimpleName()
            if (className.endsWith("Handler")) then className.dropRight(7)
            else className
          },
        handler
      )
    )
    .toMap

  final override def handleRequest(input: String)(using LambdaContext, ApplicationContext): String =
    parseInput(input).match {
      case request: ApiGatewayRequest =>
        try {
          apiGatewayRequestHandlers
            .foldLeft[Option[ApiGatewayResponse]](None)((result, stub) => result.orElse(stub.handleRequest(request)))
            .map(_.writeAsString)
            .getOrElse(
              throw UnsupportedRequestError(
                s"${request.httpMethod} ${request.path}"
              )
            )
        } catch handleApiGatewayHandlerException(input)

      case event: SqsEvent =>
        println(s"Processing ${event.Records.size} record(s)")
        event.Records.zipWithIndex.foreach { (record, index) =>
          try {
            val recordJson = record.maybeParseBodyAsJson
            recordJson.flatMap(maybeFunctionName).match {
              case Some(functionName) =>
                sqsEventHandlersMap
                  .get(functionName)
                  .flatMap { handler =>
                    handler.handleRecord(getFunctionInput(record))
                  }
                  .getOrElse(
                    throw UnsupportedEventError(write(event))
                  )
              case None =>
                if (sqsEventHandlersMap.size == 1)
                then
                  sqsEventHandlersMap.head._2
                    .handleRecord(record)
                    .getOrElse(
                      throw UnsupportedEventError(write(event))
                    )
                else
                  throw UnsupportedEventError(
                    "Ambiguous SQS event cannot be processed because 'handler' parameter is missing. Add \"handler\":\"{sqsHandlerName}\" field to your record body."
                  )
            }
          } catch handleSqsEventHandlerException(input)
        }
        "" // returning empty string always

      case event: ujson.Value =>
        try {
          maybeFunctionName(event).match {
            case Some(functionName) =>
              genericEventHandlersMap
                .get(functionName)
                .flatMap(_.handleEvent(getFunctionInput(event)))
                .getOrElse(
                  throw UnsupportedEventError(write(event))
                )
            case None =>
              if (genericEventHandlersMap.size == 1)
              then
                genericEventHandlersMap.head._2
                  .handleEvent(event)
                  .getOrElse(
                    throw UnsupportedEventError(write(event))
                  )
              else
                throw UnsupportedEventError(
                  "Ambiguous generic event cannot be processed because 'handler' parameter is missing. Add \"handler\":\"{genericHandlerName}\" field to your event body."
                )
          }

        } catch handleGenericEventHandlerException(input)

      case unsupported: String =>
        handleUnsupportedInputType(unsupported)
    }

  def handleUnsupportedInputType(input: String)(using
      lambdaContext: LambdaContext
  ): String =
    throw UnsupportedRequestError(input)

  /** Provide custom ApiGateway error handling implementation here. */
  def handleApiGatewayHandlerException(input: String)(
      exception: Throwable
  )(using lambdaContext: LambdaContext): String = exception match {
    case e: ApiGatewayRequestBodyParseException =>
      ApiGatewayResponse(
        statusCode = 400,
        body = "Invalid request body.",
        headers = Map("Content-Type" -> "text/plain"),
        isBase64Encoded = false
      ).writeAsString

    case e =>
      ApiGatewayResponse(
        statusCode = 502,
        body = s"""Request ID: ${lambdaContext.requestId}
              |
              |Exception
              |------------------
              |${e.getClass().getName()}
              |${e.getMessage()}${e
                   .getStackTrace()
                   .take(10)
                   .mkString("\n")}
              |
              |Input
              |------------------
              |$input
              |""".stripMargin,
        headers = Map("Content-Type" -> "text/plain"),
        isBase64Encoded = false
      ).writeAsString
  }

  def handleSqsEventHandlerException(input: String)(
      e: Throwable
  )(using lambdaContext: LambdaContext): Unit =
    println(s"""Request ID: ${lambdaContext.requestId}
               |
               |Exception
               |------------------
               |${e.getClass().getName()}
               |${e.getMessage()}${e
                .getStackTrace()
                .take(10)
                .mkString("\n")}
               |
               |Input
               |------------------
               |$input
               |""".stripMargin)

  def handleGenericEventHandlerException(input: String)(
      exception: Throwable
  )(using lambdaContext: LambdaContext): String =
    new Error(
      errorCode = Error.getErrorCode(exception),
      errorMessage = exception.getMessage()
    ).writeAsString

  /** Simple and effective method to decide on the type of the event. */
  private inline def parseInput(input: String) =
    if (input.contains("\"Records\""))
    then input.readAs[SqsEvent]
    else if (input.contains("\"httpMethod\""))
    then input.readAs[ApiGatewayRequest]
    else
      try (ujson.read(input))
      catch {
        case e => input
      }

  private inline def maybeFunctionName(json: ujson.Value): Option[String] =
    json
      .maybeString("functionName")
      .orElse(json.maybeString("handlerName"))
      .orElse(json.maybeString("handler"))
      .orElse(json.maybeString("function"))

  private inline def getFunctionInput(event: ujson.Value): ujson.Value =
    event
      .get("functionInputParts")
      .orElse(event.get("handlerInputParts"))
      .match {
        case Some(ujson.Arr(values)) =>
          values.foldLeft(ujson.Obj()) { (a, v) =>
            v match {
              case Obj(newFields) => Obj.from(a.value ++ newFields)
              case other =>
                throw new Exception(
                  s"Expected functionInputParts array to contain only objects, but got ${other.getClass().getSimpleName()} $other"
                )
            }
          }
        case Some(value) => value
        case None =>
          event
            .get("functionInput")
            .orElse(event.get("handlerInput"))
            .getOrElse(event)
      }

  private inline def getFunctionInput(record: SqsEvent.Record): SqsEvent.Record =
    record.copy(body =
      record.body.maybeReadAsJson
        .map(e => getFunctionInput(e).writeAsString)
        .getOrElse(record.body)
    )

}
