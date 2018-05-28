package com.avast.clients.stor

import java.io.InputStream

import better.files.File
import com.avast.scala.hashes.Sha256
import com.typesafe.config.{Config, ConfigFactory}
import mainecoon.FunctorK
import monix.eval.Task
import monix.execution.Scheduler
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.client.blaze.{BlazeClientConfig, Http1Client}
import pureconfig.error.{ConfigReaderException, ConfigReaderFailure, ConfigReaderFailures, ConfigValueLocation}
import pureconfig.modules.http4s.uriReader
import pureconfig.{CamelCase, ConfigFieldMapping, ProductHint}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.language.higherKinds

trait ReadingStorClient[F[_]] {
  def head(sha256: Sha256): F[Either[StorException, HeadResult]]

  def get(sha256: Sha256, dest: File = File.newTemporaryFile(prefix = "stor")): F[Either[StorException, GetResult]]
}

trait StorClient[F[_]] extends ReadingStorClient[F] {
  def post(sha256: Sha256)(is: InputStream): F[Either[StorException, PostResult]]
}

object StorClient {
  private val RootConfigKey = "storClientDefaults"
  private val DefaultConfig = ConfigFactory.defaultReference().getConfig(RootConfigKey)

  // configure pureconfig:
  private implicit val ph: ProductHint[StorClientConfiguration] = ProductHint[StorClientConfiguration](
    fieldMapping = ConfigFieldMapping(CamelCase, CamelCase)
  )

  def fromConfig[F[_]: FromTask](config: Config)(implicit sch: Scheduler): StorClient[F] = {
    val conf = pureconfig.loadConfigOrThrow[StorClientConfiguration](config.withFallback(DefaultConfig))
    val httpClient: Client[Task] = Await.result(Http1Client[Task](conf.toBlazeConfig.copy(executionContext = sch)).runAsync, Duration.Inf)

    val auth = conf.auth.getOrElse {
      throw new ConfigReaderException[StorClientConfiguration](
        ConfigReaderFailures(new ConfigReaderFailure {
          override def description: String = "Key not found: 'auth'"

          override def location: Option[ConfigValueLocation] = ConfigValueLocation(config.root().origin())
        })
      )
    }

    FunctorK[StorClient].mapK {
      new DefaultStorClient(conf.uri, auth, httpClient)
    }(implicitly[FromTask[F]])
  }

  def readingFromConfig[F[_]: FromTask](config: Config)(implicit sch: Scheduler): ReadingStorClient[F] = {
    val conf = pureconfig.loadConfigOrThrow[StorClientConfiguration](config.withFallback(DefaultConfig))
    val httpClient: Client[Task] = Await.result(Http1Client[Task](conf.toBlazeConfig.copy(executionContext = sch)).runAsync, Duration.Inf)

    // will not be used
    val auth = BasicAuth("", "")

    FunctorK[StorClient].mapK {
      new DefaultStorClient(conf.uri, auth, httpClient)
    }(implicitly[FromTask[F]])
  }
}

private case class StorClientConfiguration(uri: Uri,
                                           auth: Option[BasicAuth],
                                           requestTimeout: Duration,
                                           socketTimeout: Duration,
                                           responseHeaderTimeout: Duration,
                                           maxConnections: Int,
                                           userAgent: Option[String]) {
  def toBlazeConfig: BlazeClientConfig = BlazeClientConfig.defaultConfig.copy(
    requestTimeout = requestTimeout,
    maxTotalConnections = maxConnections,
    responseHeaderTimeout = responseHeaderTimeout,
    idleTimeout = socketTimeout,
    userAgent = userAgent.map {
      org.http4s.headers.`User-Agent`.parse(_).getOrElse(throw new IllegalArgumentException("Unsupported format of user-agent provided"))
    }
  )
}
