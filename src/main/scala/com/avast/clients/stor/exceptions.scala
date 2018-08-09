package com.avast.clients.stor

import better.files.File

abstract class StorException(msg: String, cause: Throwable = null) extends Exception(msg, cause)

case class StorUnavailableException(uri: String, cause: Throwable = null) extends StorException(s"Stor @ $uri is unavailable", cause)

case class InvalidResponseException(status: Int, body: String, desc: String, cause: Throwable = null)
    extends StorException(s"Invalid response with status $status: $desc", cause)

case class InvalidDestinationException(file: File, cause: Throwable = null)
    extends StorException(s"Invalid destination for saving the file: $file", cause)
