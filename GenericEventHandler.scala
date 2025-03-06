package org.encalmo.lambda

import ujson.Value

/** Abstract base class of an generic event handler. */
trait GenericEventHandler[ApplicationContext] {

  /** Optional function name for handling events having `function` property. If not defined it will default to the
    * simple class name.
    */
  def functionName: Option[String] = None

  def handleEvent(
      event: Value
  )(using LambdaContext, ApplicationContext): Option[String]
}
