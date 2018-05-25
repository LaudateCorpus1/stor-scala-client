package com.avast.clients

import java.io.InputStream

import better.files.File
import cats.arrow.FunctionK
import cats.effect.{Effect, IO, Sync}
import cats.~>
import com.avast.scala.hashes.Sha256
import mainecoon.FunctorK
import monix.eval.Task
import monix.execution.Scheduler

import scala.language.higherKinds
import scala.util.control.NonFatal

package object stor {
  type FromTask[A[_]] = FunctionK[Task, A]

  implicit val fkTask: FromTask[Task] = FunctionK.id

  implicit val producerFunctorK: FunctorK[StorClient] = new FunctorK[StorClient] {
    override def mapK[F[_], G[_]](client: StorClient[F])(fToG: ~>[F, G]): StorClient[G] = new StorClient[G] {
      override def head(sha256: Sha256): G[Either[StorException, HeadResult]] = fToG {
        client.head(sha256)
      }

      override def get(sha256: Sha256, dest: File): G[Either[StorException, GetResult]] = fToG {
        client.get(sha256, dest)
      }

      override def post(sha256: Sha256)(is: InputStream): G[Either[StorException, PostResult]] = fToG {
        client.post(sha256)(is)
      }
    }

  }

  /* The following two implicits are here because current version of monix-cats module depends on old version of Cats. These are the only
   * things that we need from the dependency thus I implemented them by myself (and partially copied the code).
   * */
  private[stor] implicit val sync: Sync[Task] = new TaskSync

  private[stor] implicit def effect(implicit scheduler: Scheduler): Effect[Task] = new TaskSync with Effect[Task] {
    override def runAsync[A](fa: Task[A])(cb: Either[Throwable, A] => IO[Unit]): IO[Unit] = IO.fromFuture(IO(fa.runAsync)).runAsync(cb)

    def async[A](k: (Either[Throwable, A] => Unit) => Unit): Task[A] = {
      Task.unsafeCreate { (ctx, cb) =>
        val sc = ctx.scheduler
        try k {
          case Right(a) => cb.asyncOnSuccess(a)
          case Left(e) => cb.asyncOnError(e)
        } catch {
          case NonFatal(e) =>
            sc.reportFailure(e)
        }
      }
    }
  }
}

class TaskSync extends Sync[Task] {
  override def suspend[A](thunk: => Task[A]): Task[A] = Task.defer(thunk)

  override def pure[A](x: A): Task[A] = Task.now(x)

  override def tailRecM[A, B](a: A)(f: A => Task[Either[A, B]]): Task[B] = Task.tailRecM(a)(f)

  override def flatMap[A, B](fa: Task[A])(f: A => Task[B]): Task[B] = fa.flatMap(f)

  override def raiseError[A](e: Throwable): Task[A] = Task.raiseError(e)

  override def handleErrorWith[A](fa: Task[A])(f: Throwable => Task[A]): Task[A] = fa.onErrorHandleWith(f)
}
