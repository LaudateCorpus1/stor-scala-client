package com.avast.clients.stor

abstract class StorException(msg: String, cause: Throwable = null) extends Exception(msg, cause)

case class InvalidResponseException(status: Int, body: String, desc: String, cause: Throwable = null)
    extends StorException(s"Invalid response with status $status", cause)
