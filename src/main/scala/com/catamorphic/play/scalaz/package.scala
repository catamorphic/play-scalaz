package com.catamorphic.play

import scala.concurrent._

import _root_.scalaz._
import _root_.scalaz.Scalaz._

// Play Json imports
import play.api._
import play.api.mvc._

import play.api.libs.ws.Response
import play.api.libs.json._
import play.api.data.validation._
import java.net.URI

package object scalaz {
  @inline def liftK[F[+_]: Monad, C, A](fa: F[A]): ReaderT[F, C, A] = fa.liftM[({type l[f[+_], a]=ReaderT[f, C, a]})#l]
  @inline def pointK[F[+_]: Monad, C, A](a: => A): ReaderT[F, C, A] = Monad[({type λ[α] = ReaderT[F, C, α]})#λ].point(a)

  def log[T](msg: String, t : T): Unit = {
    if ( Logger.isDebugEnabled ) {
      Logger.debug("%s : %s".format(msg, t.toString))
    }    
  }

  def tokenReads[T](v: => T, token : String) : Reads[T] = new Reads[T] {    
    def reads(json: JsValue) = {
      lazy val error = JsError(Seq(JsPath() -> Seq(ValidationError("validate.error.expected.token." + token, json)))) 
      json match {
        case JsString(s) => if (s === token) JsSuccess(v) 
                            else error
        case _ => error
      }    
    }
  }

  def tokenWrites[T](tokenFn: T => String): Writes[T] = new Writes[T] {
    def writes(t: T) = JsString(tokenFn(t))
  }

  implicit val uriReads: Reads[URI] = new Reads[URI] {
    import scala.util.control.Exception._
    def reads(json: JsValue): JsResult[URI] = {
      val err = JsError(Seq(JsPath() -> Seq(ValidationError("validate.error.expected.uri"))))
      json match {
        case JsString(s) => (catching(classOf[Throwable]) opt (URI.create(s))).cata(
          some = JsSuccess(_),
          none = err
        )
        case _ => err
      }
    }
  }    

  implicit val uriWrites: Writes[URI] = tokenWrites(u => u.toASCIIString)

  // Copied from blueeyes master
  class FutureMonad(implicit val context: ExecutionContext) extends Applicative[Future] with Monad[Future] {
    def point[A](a: => A) = Future.successful(a)
    def bind[A, B](fut: Future[A])(f: A => Future[B]) = fut.flatMap(f)
    override def ap[A,B](fa: => Future[A])(ff: => Future[A => B]) = fa.flatMap{ a => ff.map{ f => f(a) } }
  }

  implicit def futureApplicative(implicit ec: ExecutionContext) = new FutureMonad
  type JsErrors = Seq[(JsPath, Seq[ValidationError])] 
  type JsResultZ[+T] = JsErrors \/ T
  type AsyncWebResult[+T] = EitherT[Future, Result, T]
  type AsyncJsResult[+T] = EitherT[Future, JsErrors, T]
  type OptionAsyncJsResult[+A] = OptionT[AsyncJsResult, A]
  type OptionAsyncWebResult[+A] = OptionT[AsyncWebResult, A]

  
  def writeJson[T: Writes](t: T): JsValue = Json.toJson(t)

  val jsResultToEither = new (JsResult ~> JsResultZ) {
    def apply[A](a: JsResult[A]): JsResultZ[A] = \/.fromEither(a.asEither)
  }

  implicit def EitherReads[T, U](implicit fmtT: Reads[T], fmtU: Reads[U]): Reads[T \/ U] = new Reads[T \/ U] {
    import scala.util.control.Exception._

    def reads(json: JsValue) = fmtT.reads(json).fold( e => fmtU.reads(json).map(\/.right[T, U](_)), t => JsSuccess(\/.left[T, U](t)))
  }

  def respToJson[T: Reads](resp: Future[Response])(implicit ex: ExecutionContext): AsyncJsResult[T] = 
    validateJsValue[T](resp.map(_.json))

  def eitherRespToJson[T: Reads, U: Reads](resp: Future[Response])(implicit ex: ExecutionContext): AsyncJsResult[T \/ U] =
    validateJsValue[T \/ U](resp.map(_.json))

  def validateJsValue[T: Reads](jv: Future[JsValue])(implicit ex: ExecutionContext): AsyncJsResult[T] = 
      EitherT.eitherT(jv.map(json => jsResultToEither(json.validate[T])))
}

trait JsResultInstances {
  implicit val jsresultinstance = new MonadPlus[JsResult] {
    def bind[A, B](fa: JsResult[A])(f: A => JsResult[B]): JsResult[B] = fa.flatMap(f)

    def plus[A](a: JsResult[A], b: => JsResult[A]): JsResult[A] = a orElse b

    def point[A](a: => A): JsResult[A] = JsSuccess(a)

    def empty[A]: JsResult[A] = JsError(Seq())
  }
}