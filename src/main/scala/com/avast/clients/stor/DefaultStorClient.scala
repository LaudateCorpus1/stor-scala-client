package com.avast.clients.stor

import java.io.{ByteArrayInputStream, InputStream}

import better.files.File
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.blaze.Http1Client
import org.http4s.headers.`Content-Length`

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

class DefaultStorClient(rootUri: String, httpClient: Client[Task])(implicit sch: Scheduler) extends StorClient[Task] with StrictLogging {

  override def head(sha256: Sha256): Task[Either[StorException, HeadResult]] = {
    logger.debug(s"Checking presence of file $sha256 in Stor")

    val request = Request[Task](
      Method.HEAD,
      Uri.fromString(s"$rootUri/$sha256").getOrElse(throw new IllegalStateException("This should not happen"))
    )

    httpClient.fetch(request) { resp =>
      resp.status match {
        case Status.Ok => Task.now(Right(HeadResult.Exists))
        case Status.NotFound =>
          Task.now(Right(HeadResult.NotFound))

        case _ =>
          resp.bodyAsText.compile.last.map { body =>
            Left(InvalidResponseException(resp.status.code, body.toString, "Unexpected status"))
          }
      }
    }
  }

  override def get(sha256: Sha256, dest: File): Task[Either[StorException, GetResult]] = {
    logger.debug(s"Getting file $sha256 from Stor")

    try {
      val request = Request[Task](
        Method.GET,
        Uri.fromString(s"$rootUri/$sha256").getOrElse(throw new IllegalStateException("This should not happen"))
      )

      httpClient.fetch(request) { resp =>
        resp.status match {
          case Status.Ok => receiveStreamedFile(sha256, dest, resp)
          case Status.NotFound => Task.now(Right(GetResult.NotFound))

          case _ =>
            resp.bodyAsText.compile.last.map { body =>
              Left(InvalidResponseException(resp.status.code, body.toString, "Unexpected status"))
            }
        }

      }
    } catch {
      case NonFatal(e) => Task.raiseError(e)
    }
  }

  private def receiveStreamedFile(sha256: Sha256, dest: File, resp: Response[Task]): Task[Either[InvalidResponseException, GetResult]] = {
    `Content-Length`.from(resp.headers) match {
      case Some(clh) =>
        val fileCopier = new FileCopier
        val fileOs = dest.newOutputStream

        resp.body.chunks
          .map(bytes => new ByteArrayInputStream(bytes.toArray))
          .map(fileCopier.copy(_, fileOs))
          .compile
          .toVector
          .map { chunksSizes =>
            val transferred = chunksSizes.sum

            if (clh.length != transferred) {
              Left(InvalidResponseException(resp.status.code, "-stream-", s"Expected ${clh.length} B but got $transferred B"))
            } else {
              val transferredSha = fileCopier.finalSha256

              if (transferredSha != sha256.toString) {
                Left(InvalidResponseException(resp.status.code, "-stream-", s"Expected SHA256 $sha256 but got $transferredSha"))
              } else {
                Right(GetResult.Downloaded(dest, transferred))
              }
            }
          }

      case None => Task.now(Left(InvalidResponseException(resp.status.code, "-stream-", "Missing Content-Length header")))
    }
  }

  override def post(sha256: Sha256)(is: InputStream): Task[Either[StorException, PostResult]] = ???

}

object Test extends App {

  import Scheduler.Implicits.global

  try {
    val sha = Sha256("F6125E8A82EF3309BEB31742C6FCBC5A6A2B36D57894AF268ABB4EF09D71C562")

    val client = new DefaultStorClient("http://stor.whale.int.avast.com", Await.result(Http1Client[Task]().runAsync, Duration.Inf))

    val result = Await.result(client.get(sha).runAsync, Duration.Inf)

    println(result)
  } catch {
    case e: Throwable => e.printStackTrace()
  }
}
