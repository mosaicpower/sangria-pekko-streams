package sangria.streaming

import scala.language.higherKinds
import akka.NotUsed
import akka.event.Logging
import akka.stream.ActorAttributes.SupervisionStrategy
import akka.stream._
import akka.stream.scaladsl.{Merge, Sink, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}

import scala.concurrent.Future

object akkaStreams {
  type AkkaSource[+T] = Source[T, NotUsed]

  abstract class SimpleLinearGraphStage[T] extends GraphStage[FlowShape[T, T]] {
    val in = Inlet[T](Logging.simpleName(this) + ".in")
    val out = Outlet[T](Logging.simpleName(this) + ".out")
    override val shape = FlowShape(in, out)
  }

  class AkkaStreamsSubscriptionStream(implicit materializer: Materializer) extends SubscriptionStream[AkkaSource] {
    def supported[T[_]](other: SubscriptionStream[T]) = other.isInstanceOf[AkkaStreamsSubscriptionStream]

    def map[A, B](source: AkkaSource[A])(fn: A ⇒ B) = source.map(fn)

    def singleFuture[T](value: Future[T]) = Source.fromFuture(value)

    def single[T](value: T) = Source.single(value)

    def mapFuture[A, B](source: AkkaSource[A])(fn: A ⇒ Future[B]) =
      source.mapAsync(1)(fn)

    def first[T](s: AkkaSource[T]) = s.runWith(Sink.head)

    def failed[T](e: Throwable) = Source.failed(e).asInstanceOf[AkkaSource[T]]

    def onComplete[Ctx, Res](result: AkkaSource[Res])(op: ⇒ Unit) =
      result
        .via(OnComplete(() ⇒ op))
        .recover {case e ⇒ op; throw e}
        .asInstanceOf[AkkaSource[Res]]

    def flatMapFuture[Ctx, Res, T](future: Future[T])(resultFn: T ⇒ AkkaSource[Res]) =
      Source.fromFuture(future).flatMapMerge(1, resultFn)

    def merge[T](streams: Vector[AkkaSource[T]]) = {
      if (streams.size > 1)
        Source.combine(streams(0), streams(1), streams.drop(2): _*)(Merge(_))
      else if (streams.nonEmpty)
        streams.head
      else
        throw new IllegalStateException("No streams produced!")
    }

    def recover[T](stream: AkkaSource[T])(fn: Throwable ⇒ T) =
      stream recover {case e ⇒ fn(e)}
  }

  implicit def akkaSubscriptionStream(implicit materializer: Materializer): SubscriptionStream[AkkaSource] = new AkkaStreamsSubscriptionStream

  implicit def akkaStreamIsValidSubscriptionStream[A[_, _], Ctx, Res, Out](implicit materializer: Materializer, ev1: ValidOutStreamType[Res, Out]): SubscriptionStreamLike[Source[A[Ctx, Res], NotUsed], A, Ctx, Res, Out] =
    new SubscriptionStreamLike[Source[A[Ctx, Res], NotUsed], A, Ctx, Res, Out] {
      type StreamSource[X] = AkkaSource[X]
      val subscriptionStream = new AkkaStreamsSubscriptionStream
    }

  private final case class OnComplete[T](op: () ⇒ Unit) extends SimpleLinearGraphStage[T] {
    override def toString: String = "OnComplete"

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with OutHandler with InHandler {
        def decider = inheritedAttributes.get[SupervisionStrategy].map(_.decider).getOrElse(Supervision.stoppingDecider)

        override def onPush(): Unit = {
          push(out, grab(in))
        }

        override def onPull(): Unit = pull(in)

        override def onDownstreamFinish() = {
          op()
          super.onDownstreamFinish()
        }

        override def onUpstreamFinish() = {
          op()
          super.onUpstreamFinish()
        }

        setHandlers(in, out, this)
      }
  }
}
