package org.encalmo.lambda

import org.encalmo.utils.JsonUtils.*

import ujson.Value
import upickle.default.*
import java.time.Instant

class MultipleHandlersSupportSpec extends munit.FunSuite {

  override def munitFixtures = List(lambdaService)

  val lambdaService = new LambdaServiceFixture()

  class Foo

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
      lambdaRuntime.test("""{"function":"TestMe", "functionInput": {"foo":"bar"}}"""),
      "bar"
    )
    assertEquals(
      lambdaRuntime.test("""{"function":"TestMe", "functionInputParts": {"foo":"bar"}}"""),
      "bar"
    )
    assertEquals(
      lambdaRuntime.test("""{"function":"TestMe", "functionInputParts": [{"foo":"bar"}]}"""),
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

  test("getEventHandlerTag") {
    assertEquals(lambdaRuntime.getEventHandlerTag("""{"function":"TestMe","foo":"bar"}"""), Some("TestMe"))
    assertEquals(lambdaRuntime.getEventHandlerTag("""{"functionName":"TestMe","foo":"bar"}"""), Some("TestMe"))
    assertEquals(lambdaRuntime.getEventHandlerTag("""{"handler":"TestMe","foo":"bar"}"""), Some("TestMe"))
    assertEquals(lambdaRuntime.getEventHandlerTag("""{"handlerName":"TestMe","foo":"bar"}"""), None)
  }

  override def afterAll(): Unit =
    lambdaService.close()

  def lambdaRuntime = new LambdaRuntime with MultipleHandlersSupport[Foo] {

    override given otherContext: Foo = new Foo

    override def apiGatewayRequestHandlers: Iterable[ApiGatewayRequestHandler[Foo]] = Seq.empty

    override def sqsEventHandlers: Iterable[SqsEventHandler[Foo]] = Seq(
      new SqsEventHandler[Foo] {

        override def functionName: Option[String] = Some("TestMe")

        override def handleRecord(record: SqsEvent.Record)(using
            LambdaContext,
            Foo
        ): Option[String] =
          record.maybeParseBodyAsJson.flatMap(_.maybeString("foo"))

      }
    )

    override def genericEventHandlers: Iterable[GenericEventHandler[Foo]] =
      Seq(new GenericEventHandler[Foo] {

        override def functionName: Option[String] = Some("TestMe")

        override def handleEvent(event: Value)(using
            LambdaContext,
            Foo
        ): Option[String] = event.maybeString("foo")

      })

    lazy val config = configure { (environment: LambdaEnvironment) =>
      println(s"Initializing lambda ${environment.getFunctionName()} ...")
    }
  }

}
