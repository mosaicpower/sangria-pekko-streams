package sangria.streaming

import org.apache.pekko.NotUsed
import org.apache.pekko.event.Logging
import org.apache.pekko.stream.*
import org.apache.pekko.stream.ActorAttributes.SupervisionStrategy
import org.apache.pekko.stream.scaladsl.{Merge, Sink, Source}
import org.apache.pekko.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}

import scala.concurrent.Future

object pekkoStreams {
  type PekkoSource[+T] = Source[T, NotUsed]

  abstract class SimpleLinearGraphStage[T] extends GraphStage[FlowShape[T, T]] {
    val in = Inlet[T](Logging.simpleName(this) + ".in")
    val out = Outlet[T](Logging.simpleName(this) + ".out")
    override val shape = FlowShape(in, out)
  }

  class PekkoStreamsSubscriptionStream(implicit materializer: Materializer)
      extends SubscriptionStream[PekkoSource] {
    def supported[T[_]](other: SubscriptionStream[T]) =
      other.isInstanceOf[PekkoStreamsSubscriptionStream]

    def map[A, B](source: PekkoSource[A])(fn: A => B) = source.map(fn)

    def singleFuture[T](value: Future[T]) = Source.future(value)

    def single[T](value: T) = Source.single(value)

    def mapFuture[A, B](source: PekkoSource[A])(fn: A => Future[B]) =
      source.mapAsync(1)(fn)

    def first[T](s: PekkoSource[T]) = s.runWith(Sink.head)

    def failed[T](e: Throwable) = Source.failed(e).asInstanceOf[PekkoSource[T]]

    def onComplete[Ctx, Res](result: PekkoSource[Res])(op: => Unit) =
      result
        .via(OnComplete(() => op))
        .recover { case e => op; throw e }
        .asInstanceOf[PekkoSource[Res]]

    def flatMapFuture[Ctx, Res, T](future: Future[T])(resultFn: T => PekkoSource[Res]) =
      Source.future(future).flatMapMerge(1, resultFn)

    def merge[T](streams: Vector[PekkoSource[T]]) =
      if (streams.size > 1)
        Source.combine(streams(0), streams(1), streams.drop(2): _*)(Merge(_))
      else if (streams.nonEmpty)
        streams.head
      else
        throw new IllegalStateException("No streams produced!")

    def recover[T](stream: PekkoSource[T])(fn: Throwable => T) =
      stream.recover { case e => fn(e) }
  }

  implicit def pekkoSubscriptionStream(implicit
      materializer: Materializer): SubscriptionStream[PekkoSource] =
    new PekkoStreamsSubscriptionStream

  implicit def pekkoStreamIsValidSubscriptionStream[A[_, _], Ctx, Res, Out](implicit
      materializer: Materializer,
      ev1: ValidOutStreamType[Res, Out])
      : SubscriptionStreamLike[Source[A[Ctx, Res], NotUsed], A, Ctx, Res, Out] =
    new SubscriptionStreamLike[Source[A[Ctx, Res], NotUsed], A, Ctx, Res, Out] {
      type StreamSource[X] = PekkoSource[X]
      val subscriptionStream: SubscriptionStream[PekkoSource] = new PekkoStreamsSubscriptionStream
    }

  private final case class OnComplete[T](op: () => Unit) extends SimpleLinearGraphStage[T] {
    override def toString: String = "OnComplete"

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with OutHandler with InHandler {
        def decider = inheritedAttributes
          .get[SupervisionStrategy]
          .map(_.decider)
          .getOrElse(Supervision.stoppingDecider)

        override def onPush(): Unit =
          push(out, grab(in))

        override def onPull(): Unit = pull(in)

        override def onDownstreamFinish(cause: Throwable) = {
          op()
          super.onDownstreamFinish(cause)
        }

        override def onUpstreamFinish() = {
          op()
          super.onUpstreamFinish()
        }

        setHandlers(in, out, this)
      }
  }
}
