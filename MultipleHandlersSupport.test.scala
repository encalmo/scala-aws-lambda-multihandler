package org.encalmo.lambda

import org.encalmo.utils.JsonUtils.*

import ujson.Value
import upickle.default.*
import java.time.Instant

class Foo

class MultipleHandlersSupportSpec extends munit.FunSuite {

  override def munitFixtures = List(lambdaService)

  val lambdaService = new LambdaServiceFixture()

  test("Execute generic event handler") {

    assertEquals(
      lambdaRuntime.test("""{"functionName":"TestMe","foo":"bar"}"""),
      "bar"
    )

    assertEquals(
      lambdaRuntime.test("""{"function":"TestMe","foo":"bar"}"""),
      "bar"
    )

    assertEquals(
      lambdaRuntime.test("""{"handler":"TestMe","foo":"bar"}"""),
      "bar"
    )

    assertEquals(
      lambdaRuntime.test("""{"handlerName":"TestMe","foo":"bar"}"""),
      "bar"
    )

    assertEquals(
      lambdaRuntime.test("""{"foo":"bar"}"""),
      "bar"
    )

    assertEquals(
      lambdaRuntime.test("""{"bar":"foo"}"""),
      """{"success":false,"timestamp":"""
        + Instant.now().getEpochSecond()
        + ""","error":"UnsupportedEventError","errorMessage":"{\"bar\":\"foo\"}"}""".stripMargin
    )
  }

  test("Execute generic event handler using function name") {
    assertEquals(
      lambdaRuntime.test("""{"function":"TestMe", "foo":"bar"}"""),
      "bar"
    )
    assertEquals(
      lambdaRuntime.test("""{"functionName":"TestMe", "functionInput": {"foo":"bar"}}"""),
      "bar"
    )
    assertEquals(
      lambdaRuntime.test("""{"handler":"TestMe", "functionInputParts": {"foo":"bar"}}"""),
      "bar"
    )
    assertEquals(
      lambdaRuntime.test("""{"handlerName":"TestMe", "handlerInputParts": [{"foo":"bar"}]}"""),
      "bar"
    )
    assertEquals(
      lambdaRuntime.test("""{"function":"TestMe", "functionInputParts": [{"foo":"bar"},{"bar":"foo"}]}"""),
      "bar"
    )
    assertEquals(
      lambdaRuntime.test("""{"function":"TestMe", "functionInputParts": [{"bar":"foo"},{"foo":"bar"}]}"""),
      "bar"
    )
    assertEquals(
      lambdaRuntime.test("""{"function":"TestMe","bar":"foo"}"""),
      """{"success":false,"timestamp":"""
        + Instant.now().getEpochSecond()
        + ""","error":"UnsupportedEventError","errorMessage":"{\"function\":\"TestMe\",\"bar\":\"foo\"}"}""".stripMargin
    )
    assertEquals(
      lambdaRuntime.test("""{"function":"TestMe","functionInput":{"bar":"foo"}}"""),
      """{"success":false,"timestamp":"""
        + Instant.now().getEpochSecond()
        + ""","error":"UnsupportedEventError","errorMessage":"{\"function\":\"TestMe\",\"functionInput\":{\"bar\":\"foo\"}}"}""".stripMargin
    )
  }

  test("Execute sqs event handler") {

    val event = SqsEvent(
      Seq(
        SqsEvent.Record(
          messageId = "foo",
          body = """{"foo":"bar"}""",
          attributes = Map.empty,
          eventSource = "bar",
          eventSourceARN = "zoo"
        )
      )
    ).writeAsString

    assertEquals(
      lambdaRuntime.test(event),
      ""
    )
  }

  test("Execute sqs event handler using function name") {

    val event = SqsEvent(
      Seq(
        SqsEvent.Record(
          messageId = "foo",
          body = """{"function":"TestMe","foo":"bar"}""",
          attributes = Map.empty,
          eventSource = "bar",
          eventSourceARN = "zoo"
        )
      )
    ).writeAsString

    assertEquals(
      lambdaRuntime.test(event),
      ""
    )
  }

  test("Execute sqs event handler using functionName and functionInput") {

    val event = SqsEvent(
      Seq(
        SqsEvent.Record(
          messageId = "foo",
          body = """{"functionName":"TestMe", "functionInput":{"foo":"bar"}}""",
          attributes = Map.empty,
          eventSource = "bar",
          eventSourceARN = "zoo"
        )
      )
    ).writeAsString

    assertEquals(
      lambdaRuntime.test(event),
      ""
    )
  }

  test("getEventHandlerTag") {
    assertEquals(lambdaRuntime.getEventHandlerTag("""{"function":"TestMe","foo":"bar"}"""), Some("TestMe"))
    assertEquals(lambdaRuntime.getEventHandlerTag("""{"functionName":"TestMe","foo":"bar"}"""), Some("TestMe"))
    assertEquals(lambdaRuntime.getEventHandlerTag("""{"handler":"TestMe","foo":"bar"}"""), Some("TestMe"))
    assertEquals(lambdaRuntime.getEventHandlerTag("""{"handlerName":"TestMe","foo":"bar"}"""), None)
  }

  override def afterAll(): Unit =
    lambdaService.close()

  def lambdaRuntime =
    new LambdaRuntime with MultipleHandlersSupport {

      override type ApplicationContext = Foo

      override def initialize(using environment: LambdaEnvironment) = {
        environment.info(
          s"Initializing lambda ${environment.getFunctionName()} ..."
        )
        new Foo
      }

      override def apiGatewayRequestHandlers = Seq(new TestApiGatewayRequestHandler)

      override def sqsEventHandlers = Seq(new TestSqsEventHandler)

      override def genericEventHandlers = Seq(new TestGenericEventHandler)
    }

}

class TestGenericEventHandler extends GenericEventHandler[Foo] {

  override def functionName: Option[String] = Some("TestMe")

  override def handleEvent(event: Value)(using
      LambdaContext,
      Foo
  ): Option[String] = event.maybeString("foo")

}

class TestSqsEventHandler extends SqsEventHandler[Foo] {

  override def functionName: Option[String] = Some("TestMe")

  override def handleRecord(record: SqsEvent.Record)(using
      LambdaContext,
      Foo
  ): Option[String] =
    record.maybeParseBodyAsJson.flatMap(_.maybeString("foo"))
}

class TestApiGatewayRequestHandler extends ApiGatewayRequestHandler[Foo] {

  override def handleRequest(request: ApiGatewayRequest)(using LambdaContext, Foo): Option[ApiGatewayResponse] =
    request.body.maybeReadAsJson
      .flatMap(_.maybeString("foo"))
      .map(foo =>
        ApiGatewayResponse(
          body = foo,
          statusCode = 200,
          headers = Map.empty,
          isBase64Encoded = false
        )
      )

}
