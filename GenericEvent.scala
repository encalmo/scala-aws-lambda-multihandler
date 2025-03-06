package org.encalmo.lambda

import upickle.default.ReadWriter
import org.encalmo.lambda.OptionPickler.*

final case class GenericEvent(
    funtionName: String,
    funtionInput: ujson.Value
) derives ReadWriter
