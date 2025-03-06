package org.encalmo.lambda

/** Abstract base class of an SQS event handler. */
trait SqsEventHandler[ApplicationContext] {

  /** Optional function name for handling events having `function` property. If not defined it will default to the
    * simple class name.
    */
  def functionName: Option[String] = None

  def handleRecord(
      record: SqsEvent.Record
  )(using LambdaContext, ApplicationContext): Option[String]
}
