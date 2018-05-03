package com.avast.clients.stor

import better.files.File

sealed trait HeadResult

object HeadResult {

  case object Exists extends HeadResult

  case object NotFound extends HeadResult

}

sealed trait GetResult

object GetResult {

  case class Exists(file: File, fileSize: Long) extends GetResult

  case object NotFound extends GetResult

}

sealed trait PostResult

object PostResult {

  case class AlreadyExists(locations: Map[String, Seq[String]]) extends PostResult

  case class Created(locations: Map[String, Seq[String]]) extends PostResult

  case object Unauthorized extends PostResult

  case object ShaMismatch extends PostResult

  case object InsufficientStorage extends PostResult

}

sealed trait StatusResult {

  case object Ok extends StatusResult

  case object SomeUnavailable extends StatusResult

}
