/* RetryDSL.scala
 * 
 * Copyright (c) 2013 bizo.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package atmos.retries

import java.io.{ PrintStream, PrintWriter }
import java.util.logging.{ Logger, Level }
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.language.implicitConversions
import akka.event.{ Logging, LoggingAdapter }
import org.slf4j.{ Logger => Slf4jLogger }

/**
 * Defines a domain specific language for constructing and using retry policies.
 *
 * ==Getting Started==
 *
 * To use this DSL, start by adding the following imports to your Scala file:
 * {{{
 * import atmos.retries.RetryDSL._
 * import scala.concurrent.duration._ // Optional, only needed if you are defining durations.
 * }}}
 *
 * Next, define and configure an implicit retry policy:
 * {{{
 * implicit val retryPolicy = retryFor { 5.minutes } using linearBackoff { 2.seconds } monitorWith System.out onError {
 *   case _: IllegalArgumentException => stopRetrying
 * }
 * }}}
 * The above policy will retry for at most 5 minutes using a linearly-increasing backoff starting at 2 seconds, printing
 * all events to `System.out` and failing-fast if an `IllegalArgumentException` is thrown. See below for more examples
 * of retry policy declarations.
 *
 * Finally, add a call to `retry` or `retryAsync` around the target operation:
 * {{{
 * retry() { /* Some operation that might fail. */ }
 *
 * retryAsync() { Future { /* Some asynchronous operation that might fail. */ } }
 * }}}
 * Note: to use `retryAsync` you must also have an implicit instance of `scala.concurrent.ExecutionContext` in scope.
 *
 * ==Termination Policies==
 *
 * Termination policies determine when a retry operation will make no further attempts and are typically the first thing
 * defined for a retry policy. See [[atmos.retries.TerminationPolicy]] for more information.
 *
 * A retry policy with the default termination policy of limiting an operation to 3 attempts can be created with
 * `retrying`:
 * {{{
 * implicit val retryPolicy = retrying
 * }}}
 *
 * Additionally, custom termination policies can be specified using `retryFor`:
 * {{{
 * // Terminate after 5 failed attempts.
 * implicit val retryPolicy = retryFor { 5.attempts }
 *
 * // Terminate after retrying for at least 5 minutes.
 * implicit val retryPolicy = retryFor { 5.minutes }
 *
 * // Terminate after 5 failed attempts or retrying for at least 5 minutes, whichever comes first.
 * implicit val retryPolicy = retryFor { 5.attempts || 5.minutes }
 *
 * // Terminate after at least 5 failed attempts but not before retrying for at least 5 minutes.
 * implicit val retryPolicy = retryFor { 5.attempts && 5.minutes }
 * }}}
 * Note that the `5.minutes` parameter is an instance of `scala.concurrent.duration.FiniteDuration` and that any
 * instance of this class may be used as a policy in `retryFor`.
 *
 * Finally, a retry policy that immediately terminates can be created with `neverRetry` and a retry policy that never
 * terminates (unless directed to by an error classifier) can be created with `retryForever`:
 * {{{
 * implicit val retryPolicy = neverRetry
 *
 * implicit val retryPolicy = retryForever
 * }}}
 *
 * ==Backoff Policies==
 *
 * Backoff policies specify the delay between subsequent retry attempts and are configured on a retry policy with
 * `using`. See [[atmos.retries.BackoffPolicy]] for more information.
 *
 * This DSL provides support for the six provided backoff policies:
 * {{{
 * implicit val retryPolicy = retryForever using constantBackoff { 5.millis }
 *
 * implicit val retryPolicy = retryForever using linearBackoff { 5.seconds }
 *
 * implicit val retryPolicy = retryForever using exponentialBackoff { 5.minutes }
 *
 * // Uses the default backoff duration (100 milliseconds) when the parameter is omitted.
 * implicit val retryPolicy = retryForever using fibonacciBackoff()
 *
 * // Selecting another backoff policy based on the type of exception thrown.
 * implicit val retryPolicy = retryForever using selectedBackoff {
 *   case e: WaitException => constantBackoff { e.waitDuration }
 *   case _ => linearBackoff()
 * }
 *
 * // Randomizing the result of a backoff duration by adding a random duration.
 * implicit val retryPolicy = retryForever using { linearBackoff { 1.second } randomized 100.millis }
 *
 * // Randomizing the result of a backoff duration by adding a random duration from a range.
 * implicit val retryPolicy = retryForever using { linearBackoff { 1.second } randomized -50.millis -> 50.millis }
 * }}}
 *
 * ==Monitor Configuration==
 *
 * Event monitors are notified when retry attempts fail and are configured on a retry policy using `monitorWith`. See
 * [[atmos.retries.EventMonitor]] for more information.
 *
 * This DSL provides support for monitoring retry attempts with print streams, print writers, standard Java loggers,
 * Akka logging adapters and SLF4J loggers:
 * {{{
 * // Print information about failed attempts to stderr using the default printing strategies.
 * implicit val retryPolicy = retryForever monitorWith System.err
 *
 * // Print information about failed attempts to a file, customizing what events get printed and how.
 * implicit val retryPolicy = retryForever monitorWith {
 *   new PrintWriter("/path") onRetrying printNothing onInterrupted printMessage onAborted printMessageAndStackTrace
 * }
 *
 * // Submit information about failed attempts to the specified instance of `java.util.logging.Logger`.
 * implicit val retryPolicy = retryForever monitorWith Logger.getLogger("MyLoggerName")
 *
 * // Submit information about failed attempts to the specified instance of `java.util.logging.Logger`, customizing
 * // what events get logged and and at what level.
 * implicit val retryPolicy = retryForever monitorWith {
 *   Logger.getLogger("MyLoggerName") onRetrying logNothing onInterrupted logWarning onAborted logError
 * }
 *
 * // Submit information about failed attempts to the specified instance of `akka.event.LoggingAdapter`, customizing
 * // what events get logged and and at what level.
 * import AkkaSupport._
 * implicit val retryPolicy = retryForever monitorWith {
 *   Logging(context.system, this) onRetrying logNothing onInterrupted logWarning onAborted logError
 * }
 *
 * // Submit information about failed attempts to the specified instance of `org.slf4j.Logger`, customizing what
 * // events get logged and and at what level.
 * import Slf4jSupport._
 * implicit val retryPolicy = retryForever monitorWith {
 *   LoggerFactory.getLogger("MyLoggerName") onRetrying logNothing onInterrupted logWarning onAborted logError
 * }
 * }}}
 *
 * ==Error Classification==
 *
 * Errors that occur during a retry attempt can be classified as `Fatal`, `Recoverable` or `SilentlyRecoverable`.
 * Retry policies can be configured with error classification functions using `onError`. See
 * [[atmos.retries.ErrorClassification]] for more information.
 *
 * This DSL provides support for the three provided error classifications:
 * {{{
 * // Stop retrying after any runtime exception.
 * implicit val retryPolicy = retryForever onError { case _: RuntimeException => stopRetrying }
 *
 * // Continue retrying after an illegal argument exception and retrying silently after an illegal state exception.
 * implicit val retryPolicy = retryForever onError {
 *   case _: IllegalArgumentException => keepRetrying
 *   case _: IllegalStateException => keepRetryingSilently
 * }
 * }}}
 */
object RetryDSL {

  //
  // Retry policy factories and extensions.
  //

  /** Creates a new retry policy that immediately terminates. */
  def neverRetry: RetryPolicy = RetryPolicy(TerminationPolicy.ImmediatelyTerminate)

  /** Creates a new default retry policy. */
  def retrying: RetryPolicy = RetryPolicy()

  /**
   * Creates a new retry policy based on the specified termination policy.
   *
   * @param termination The termination policy that will be used by the new retry policy.
   */
  def retryFor(termination: TerminationPolicy): RetryPolicy = RetryPolicy(termination)

  /** Creates a new retry policy that never terminates. */
  def retryForever: RetryPolicy = RetryPolicy(TerminationPolicy.NeverTerminate)

  /**
   * Adds DSL extension methods to the retry policy interface.
   *
   * @param self The retry policy to add the extension methods to.
   */
  implicit final class RetryPolicyExtensions(val self: RetryPolicy) extends AnyVal {

    /**
     * Creates a new retry policy by replacing the underlying policy's termination policy.
     *
     * @param termination The termination policy to use.
     */
    def retryFor(termination: TerminationPolicy): RetryPolicy = self.copy(termination = termination)

    /**
     * Creates a new retry policy by replacing the underlying policy's backoff policy.
     *
     * @param backoff The backoff policy to use.
     */
    def using(backoff: BackoffPolicy): RetryPolicy = self.copy(backoff = backoff)

    /**
     * Creates a new retry policy by replacing the underlying policy's monitor.
     *
     * @param monitor The monitor to use.
     */
    def monitorWith(monitor: EventMonitor): RetryPolicy = self.copy(monitor = monitor)

    /**
     * Creates a new retry policy by replacing the underlying policy's error classifier.
     *
     * @param classifier The error classifier policy to use.
     */
    def onError(classifier: ErrorClassifier): RetryPolicy = self.copy(classifier = classifier)

  }

  //
  // Termination policy factories and extensions.
  //

  /**
   * Creates a termination policy that limits a retry operation to the specified time frame.
   *
   * @param duration The maximum duration that the resulting termination policy will specify.
   */
  implicit def finiteDurationToTerminationPolicy(duration: FiniteDuration): TerminationPolicy =
    TerminationPolicy.LimitAmountOfTimeSpent(duration)

  /**
   * Adds logical and and or operators to durations for use in expressions like `retryFor(5.minutes || 5.attempts)`.
   *
   * @param duration The maximum duration that the resulting termination policy will specify.
   */
  implicit def finiteDurationToTerminationPolicyExtensions(duration: FiniteDuration): TerminationPolicyExtensions =
    new TerminationPolicyExtensions(duration)

  /**
   * Adds a termination policy factory named `attempts` to `Int` for use in expressions like `retryFor(5.attempts)`.
   *
   * @param self The maximum number of attempts that the resulting termination policy will specify.
   */
  implicit final class LimitAttemptsTerminationPolicyFactory(val self: Int) extends AnyVal {

    /** Creates a termination policy that limits a retry operation to `self` attempts. */
    def attempts: TerminationPolicy = TerminationPolicy.LimitNumberOfAttempts(self)

  }

  /**
   * Adds logical and and or operators to termination policies for use in expressions like
   * `retryFor(5.attempts || 5.minutes)`.
   *
   * @param self The termination policy to add the extension methods to.
   */
  implicit final class TerminationPolicyExtensions(val self: TerminationPolicy) extends AnyVal {

    /**
     * Creates a termination policy that signals for termination only after both `self` and `that` terminate.
     *
     * @param that The other termination policy to combine with.
     */
    def &&(that: TerminationPolicy): TerminationPolicy = TerminationPolicy.TerminateAfterBoth(self, that)

    /**
     * Creates a termination policy that signals for termination after either `self` or `that` terminate.
     *
     * @param that The other termination policy to combine with.
     */
    def ||(that: TerminationPolicy): TerminationPolicy = TerminationPolicy.TerminateAfterEither(self, that)

  }

  //
  // Backoff factories and extensions.
  //

  /**
   * Creates a backoff policy that uses the same backoff after every attempt.
   *
   * @param backoff The backoff to use after every attempt.
   */
  def constantBackoff(backoff: FiniteDuration = BackoffPolicy.defaultBackoff): BackoffPolicy =
    BackoffPolicy.Constant(backoff)

  /**
   * Creates a backoff policy that increases the backoff duration linearly after every attempt.
   *
   * @param backoff The duration to add to the backoff after every attempt.
   */
  def linearBackoff(backoff: FiniteDuration = BackoffPolicy.defaultBackoff): BackoffPolicy =
    BackoffPolicy.Linear(backoff)

  /**
   * Creates a backoff policy that increases the backoff duration exponentially after every attempt.
   *
   * @param backoff The backoff used for the first retry and used as the base for all subsequent attempts.
   */
  def exponentialBackoff(backoff: FiniteDuration = BackoffPolicy.defaultBackoff): BackoffPolicy =
    BackoffPolicy.Exponential(backoff)

  /**
   * Creates a backoff policy that increases the backoff duration by repeatedly multiplying by the an approximation of
   * the golden ratio (8 / 5, the sixth and fifth fibonacci numbers).
   *
   * @param backoff The backoff used for the first retry and used as the base for all subsequent attempts.
   */
  def fibonacciBackoff(backoff: FiniteDuration = BackoffPolicy.defaultBackoff): BackoffPolicy =
    BackoffPolicy.Fibonacci(backoff)

  /**
   * Creates a backoff policy selects another policy based on the most recently thrown exception.
   *
   * @param f The function that maps from exceptions to backoff policies.
   */
  def selectedBackoff(f: Throwable => BackoffPolicy): BackoffPolicy = BackoffPolicy.Selected(f)

  /**
   * Adds support for randomization to all backoff policies.
   *
   * @param self The backoff policy to add the extension methods to.
   */
  implicit final class BackoffPolicyExtensions(val self: BackoffPolicy) extends AnyVal {

    /**
     * Creates a backoff policy that randomizes the result of `self`.
     *
     * @param bound The minimum or maximum value in the range that may be used to modify the result of `self`.
     */
    def randomized(bound: FiniteDuration): BackoffPolicy = BackoffPolicy.Randomized(self, Duration.Zero -> bound)

    /**
     * Creates a backoff policy that randomizes the result of `self`.
     *
     * @param range The range of values that may be used to modify the result of `self`.
     */
    def randomized(range: (FiniteDuration, FiniteDuration)): BackoffPolicy = BackoffPolicy.Randomized(self, range)

  }

  //
  // Monitor factories and extensions.
  //

  /**
   * Creates a new event monitor that prints messages to a stream.
   *
   * @param stream The stream to print events to.
   */
  implicit def printStreamToEventMonitor(stream: PrintStream): EventMonitor.PrintEventsWithStream =
    EventMonitor.PrintEventsWithStream(stream)

  /**
   * Creates a new event monitor extension interface for a print stream.
   *
   * @param stream The print stream to create a new event monitor extension interface for.
   */
  implicit def printStreamToEventMonitorExtensions(stream: PrintStream): PrintEventsWithStreamExtensions =
    new PrintEventsWithStreamExtensions(stream)

  /**
   * Exposes extensions on any instance of `EventMonitor.PrintEventsWithStream`.
   */
  implicit final class PrintEventsWithStreamExtensions(val self: EventMonitor.PrintEventsWithStream) extends AnyVal {

    import EventMonitor.PrintEvents.PrintAction

    /** Returns a copy of the underlying monitor that prints events with the specified retrying strategy. */
    def onRetrying(action: PrintAction) = self.copy(retryingAction = action)

    /** Returns a copy of the underlying monitor that prints events with the specified interrupted strategy. */
    def onInterrupted(action: PrintAction) = self.copy(interruptedAction = action)

    /** Returns a copy of the underlying monitor that prints events with the specified aborting strategy. */
    def onAborted(action: PrintAction) = self.copy(abortedAction = action)

  }

  /**
   * Creates a new event monitor that prints messages to a writer.
   *
   * @param writer The writer to print events to.
   */
  implicit def printWriterToEventMonitor(writer: PrintWriter): EventMonitor.PrintEventsWithWriter =
    EventMonitor.PrintEventsWithWriter(writer)

  /**
   * Creates a new event monitor extension interface for a print writer.
   *
   * @param writer The print writer to create a new event monitor extension interface for.
   */
  implicit def printWriterToEventMonitorExtensions(writer: PrintWriter): PrintEventsWithWriterExtensions =
    new PrintEventsWithWriterExtensions(writer)

  /**
   * Exposes extensions on any instance of `EventMonitor.PrintEventsWithWriter`.
   */
  implicit final class PrintEventsWithWriterExtensions(val self: EventMonitor.PrintEventsWithWriter) extends AnyVal {

    import EventMonitor.PrintEvents.PrintAction

    /** Returns a copy of the underlying monitor that prints events with the specified retrying strategy. */
    def onRetrying(action: PrintAction) = self.copy(retryingAction = action)

    /** Returns a copy of the underlying monitor that prints events with the specified interrupted strategy. */
    def onInterrupted(action: PrintAction) = self.copy(interruptedAction = action)

    /** Returns a copy of the underlying monitor that prints events with the specified aborting strategy. */
    def onAborted(action: PrintAction) = self.copy(abortedAction = action)

  }

  /** Returns a print action that will print no text. */
  def printNothing = EventMonitor.PrintEvents.PrintAction.PrintNothing

  /** Returns a print action that will print only an event message. */
  def printMessage = EventMonitor.PrintEvents.PrintAction.PrintMessage

  /** Returns a print action that will print an event message and stack trace. */
  def printMessageAndStackTrace = EventMonitor.PrintEvents.PrintAction.PrintMessageAndStackTrace

  /**
   * Creates a new event monitor that submits events to a logger.
   *
   * @param logger The logger to supply with event messages.
   */
  implicit def loggerToEventMonitor(logger: Logger): EventMonitor.LogEventsWithJava =
    EventMonitor.LogEventsWithJava(logger)

  /**
   * Creates a new event monitor extension interface for a logger.
   *
   * @param logger The logger to create a new event monitor extension interface for.
   */
  implicit def loggerToEventMonitorExtensions(logger: Logger): LogEventsWithJavaExtensions =
    new LogEventsWithJavaExtensions(logger)

  /**
   * Exposes extensions on any instance of `EventMonitor.LogEventsWithJava`.
   */
  implicit final class LogEventsWithJavaExtensions(val self: EventMonitor.LogEventsWithJava) extends AnyVal {

    import EventMonitor.LogEvents.LogAction

    /** Returns a copy of the underlying monitor that logs events at the specified retrying level. */
    def onRetrying(action: LogAction[Level]) = self.copy(retryingAction = action)

    /** Returns a copy of the underlying monitor that logs events at the specified interrupted level. */
    def onInterrupted(action: LogAction[Level]) = self.copy(interruptedAction = action)

    /** Returns a copy of the underlying monitor that logs events at the specified aborting level. */
    def onAborted(action: LogAction[Level]) = self.copy(abortedAction = action)

  }

  /** Returns a log action that will not log anything. */
  def logNothing = EventMonitor.LogEvents.LogAction.LogNothing

  /** Returns a log action that will submit a log entry at an error-equivalent level. */
  def logError[T: EventLogLevelType] = implicitly[EventLogLevelType[T]].errorAction

  /** Returns a log action that will submit a log entry at a warning-equivalent level. */
  def logWarning[T: EventLogLevelType] = implicitly[EventLogLevelType[T]].warningAction

  /** Returns a log action that will submit a log entry at an info-equivalent level. */
  def logInfo[T: EventLogLevelType] = implicitly[EventLogLevelType[T]].infoAction

  /** Returns a log action that will submit a log entry at a debug-equivalent level. */
  def logDebug[T: EventLogLevelType] = implicitly[EventLogLevelType[T]].debugAction

  /**
   * A tag for logging system specific level types, used to map generic action names to concrete logging levels.
   *
   * @param T The type of system specific logging level.
   */
  trait EventLogLevelType[T] {

    /** A cached action that submits error log entries. */
    lazy val errorAction = EventMonitor.LogEvents.LogAction.LogAt(errorLevel)

    /** A cached action that submits warning log entries. */
    lazy val warningAction = EventMonitor.LogEvents.LogAction.LogAt(warningLevel)

    /** A cached action that submits info log entries. */
    lazy val infoAction = EventMonitor.LogEvents.LogAction.LogAt(infoLevel)

    /** A cached action that submits debug log entries. */
    lazy val debugAction = EventMonitor.LogEvents.LogAction.LogAt(debugLevel)

    /** The concrete error level. */
    def errorLevel: T

    /** The concrete warning level. */
    def warningLevel: T

    /** The concrete info level. */
    def infoLevel: T

    /** The concrete debug level. */
    def debugLevel: T

  }

  /**
   * Declarations of the default logging level tags.
   */
  object EventLogLevelType {

    /**
     * A tag for levels used by `java.util.logging`.
     */
    implicit object JavaLevelType extends EventLogLevelType[Level] {
      override def errorLevel = Level.SEVERE
      override def warningLevel = Level.WARNING
      override def infoLevel = Level.INFO
      override def debugLevel = Level.CONFIG
    }

  }

  /**
   * Separate namespace for optional Akka support.
   */
  object AkkaSupport {

    /**
     * Creates a new event monitor that submits events to an Akka logging adapter.
     *
     * @param adapter The Akka logging adapter to supply with event messages.
     */
    implicit def loggingAdapterToEventMonitor(adapter: LoggingAdapter): EventMonitor.LogEventsWithAkka =
      EventMonitor.LogEventsWithAkka(adapter)

    /**
     * Creates a new event monitor extension interface for an Akka logging adapter.
     *
     * @param adapter The Akka logging adapter to create a new event monitor extension interface for.
     */
    implicit def slf4jLoggerToEventMonitorExtensions(adapter: LoggingAdapter): LogEventsWithAkkaExtensions =
      new LogEventsWithAkkaExtensions(adapter)

    /**
     * Exposes extensions on any instance of `EventMonitor.LogEventsWithAkka`.
     */
    implicit final class LogEventsWithAkkaExtensions(val self: EventMonitor.LogEventsWithAkka) extends AnyVal {

      import EventMonitor.LogEvents.LogAction

      /** Returns a copy of the underlying monitor that logs events at the specified retrying level. */
      def onRetrying(action: LogAction[Logging.LogLevel]) = self.copy(retryingAction = action)

      /** Returns a copy of the underlying monitor that logs events at the specified interrupted level. */
      def onInterrupted(action: LogAction[Logging.LogLevel]) = self.copy(interruptedAction = action)

      /** Returns a copy of the underlying monitor that logs events at the specified aborting level. */
      def onAborted(action: LogAction[Logging.LogLevel]) = self.copy(abortedAction = action)

    }

    /**
     * A tag for levels provided for Akka.
     */
    implicit object AkkaEventLogLevelType extends EventLogLevelType[Logging.LogLevel] {
      override def errorLevel = Logging.ErrorLevel
      override def warningLevel = Logging.WarningLevel
      override def infoLevel = Logging.InfoLevel
      override def debugLevel = Logging.DebugLevel
    }

  }

  /**
   * Separate namespace for optional SLF4J support.
   */
  object Slf4jSupport {

    import EventMonitor.LogEventsWithSlf4j.Slf4jLevel

    /**
     * Creates a new event monitor that submits events to a SLF4J logger.
     *
     * @param logger The SLF4J logger to supply with event messages.
     */
    implicit def slf4jLoggerToEventMonitor(logger: Slf4jLogger): EventMonitor.LogEventsWithSlf4j =
      EventMonitor.LogEventsWithSlf4j(logger)

    /**
     * Creates a new event monitor extension interface for a SLF4J logger.
     *
     * @param logger The logger to create a new event monitor extension interface for.
     */
    implicit def slf4jLoggerToEventMonitorExtensions(logger: Slf4jLogger): LogEventsWithSlf4jExtensions =
      new LogEventsWithSlf4jExtensions(logger)

    /**
     * Exposes extensions on any instance of `EventMonitor.LogEventsWithSlf4j`.
     */
    implicit final class LogEventsWithSlf4jExtensions(val self: EventMonitor.LogEventsWithSlf4j) extends AnyVal {

      import EventMonitor.LogEvents.LogAction

      /** Returns a copy of the underlying monitor that logs events at the specified retrying level. */
      def onRetrying(action: LogAction[Slf4jLevel]) = self.copy(retryingAction = action)

      /** Returns a copy of the underlying monitor that logs events at the specified interrupted level. */
      def onInterrupted(action: LogAction[Slf4jLevel]) = self.copy(interruptedAction = action)

      /** Returns a copy of the underlying monitor that logs events at the specified aborting level. */
      def onAborted(action: LogAction[Slf4jLevel]) = self.copy(abortedAction = action)

    }

    /**
     * A tag for levels provided for Slf4j.
     */
    implicit object Slf4jEventLogLevelType extends EventLogLevelType[Slf4jLevel] {
      override def errorLevel = Slf4jLevel.Error
      override def warningLevel = Slf4jLevel.Warn
      override def infoLevel = Slf4jLevel.Info
      override def debugLevel = Slf4jLevel.Debug
    }

  }

  //
  // Classification factories.
  //

  /** Returns the `Fatal` error classification. */
  def stopRetrying: ErrorClassification = ErrorClassification.Fatal

  /** Returns the `Recoverable` error classification. */
  def keepRetrying: ErrorClassification = ErrorClassification.Recoverable

  /** Returns the `SilentlyRecoverable` error classification. */
  def keepRetryingSilently: ErrorClassification = ErrorClassification.SilentlyRecoverable

  //
  // Retry operations.
  //

  /**
   * Performs the specified operation synchronously, retrying according to the implicit retry policy.
   *
   * @param operation The operation to repeatedly perform.
   */
  def retry[T]()(operation: => T)(implicit policy: RetryPolicy): T =
    policy.retry()(operation)

  /**
   * Performs the specified named operation synchronously, retrying according to the implicit retry policy.
   *
   * @param name The name of the operation.
   * @param operation The operation to repeatedly perform.
   */
  def retry[T](name: String)(operation: => T)(implicit policy: RetryPolicy): T =
    policy.retry(name)(operation)

  /**
   * Performs the specified optionally named operation synchronously, retrying according to the implicit retry policy.
   *
   * @param name The optional name of the operation.
   * @param operation The operation to repeatedly perform.
   */
  def retry[T](name: Option[String])(operation: => T)(implicit policy: RetryPolicy): T =
    policy.retry(name)(operation)

  /**
   * Performs the specified operation asynchronously, retrying according to the implicit retry policy.
   *
   * @param operation The operation to repeatedly perform.
   * @param context The execution context to retry on.
   */
  def retryAsync[T]()(operation: => Future[T])(implicit policy: RetryPolicy, context: ExecutionContext): Future[T] =
    policy.retryAsync()(operation)

  /**
   * Performs the specified optionally named operation asynchronously, retrying according to the implicit retry policy.
   *
   * @param name The name of the operation.
   * @param operation The operation to repeatedly perform.
   * @param context The execution context to retry on.
   */
  def retryAsync[T](name: String)(operation: => Future[T]) //
  (implicit policy: RetryPolicy, context: ExecutionContext): Future[T] =
    policy.retryAsync(name)(operation)

  /**
   * Performs the specified optionally named operation asynchronously, retrying according to the implicit retry policy.
   *
   * @param name The optional name of the operation.
   * @param operation The operation to repeatedly perform.
   * @param context The execution context to retry on.
   */
  def retryAsync[T](name: Option[String])(operation: => Future[T]) //
  (implicit policy: RetryPolicy, context: ExecutionContext): Future[T] =
    policy.retryAsync(name)(operation)

}
