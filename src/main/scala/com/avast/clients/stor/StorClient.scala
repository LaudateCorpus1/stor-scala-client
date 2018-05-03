package com.avast.clients.stor

import java.io.InputStream

import better.files.File

trait StorClient[F[_]] {
  def head(sha256: String): F[Either[StorException, HeadResult]]

  def get(sha256: String, dest: File = File.newTemporaryFile(prefix = "stor")): F[Either[StorException, GetResult]]

  def post(sha256: String)(is: InputStream): F[Either[StorException, PostResult]]
}
