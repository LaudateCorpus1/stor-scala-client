package com.avast.clients.stor

import java.io.InputStream

import better.files.File
import com.typesafe.config.Config

import scala.language.higherKinds

trait StorClient[F[_]] {
  def head(sha256: Sha256): F[Either[StorException, HeadResult]]

  def get(sha256: Sha256, dest: File = File.newTemporaryFile(prefix = "stor")): F[Either[StorException, GetResult]]

  def post(sha256: Sha256)(is: InputStream): F[Either[StorException, PostResult]]
}

object StorClient {
  def fromConfig[F[_]: FromTask](config: Config): StorClient[F] = ???
}

case class StorClientConfiguration(host: String, port: Int)
