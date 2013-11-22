/*
 * Logger.scala
 * 
 * Copyright (c) 2013 Lonnie Pryor III
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
package atmos.utils

import language.experimental.macros
import language.implicitConversions
import reflect.macros.Context

import java.util.logging.{
  Level => JLevel,
  Logger => JLogger
}

/**
 * A minimal interface around a `java.util.logging.Logger` that is entirely erased at runtime.
 */
final class Logger(val jLogger: JLogger) extends AnyVal {

  /** Returns true if the `Error` level is enabled. */
  def isErrorEnabled: Boolean = macro LoggerMacros.isErrorEnabled

  /** Returns true if the `Warn` level is enabled. */
  def isWarnEnabled: Boolean = macro LoggerMacros.isWarnEnabled

  /** Returns true if the `Info` level is enabled. */
  def isInfoEnabled: Boolean = macro LoggerMacros.isInfoEnabled

  /** Returns true if the `Debug` level is enabled. */
  def isDebugEnabled: Boolean = macro LoggerMacros.isDebugEnabled

  /** Returns true if the specified level is enabled. */
  def isEnabled(level: Logger.Level): Boolean = macro LoggerMacros.isLevelEnabled

  /** Logs a message at the `Error` level if it is enabled. */
  def error(message: String): Unit = macro LoggerMacros.logError

  /** Logs a message and throwable at the `Error` level if it is enabled. */
  def error(message: String, thrown: Throwable): Unit = macro LoggerMacros.logErrorWith

  /** Logs a message at the `Warn` level if it is enabled. */
  def warn(message: String): Unit = macro LoggerMacros.logWarn

  /** Logs a message and throwable at the `Warn` level if it is enabled. */
  def warn(message: String, thrown: Throwable): Unit = macro LoggerMacros.logWarnWith

  /** Logs a message at the `Info` level if it is enabled. */
  def info(message: String): Unit = macro LoggerMacros.logInfo

  /** Logs a message and throwable at the `Info` level if it is enabled. */
  def info(message: String, thrown: Throwable): Unit = macro LoggerMacros.logInfoWith

  /** Logs a message at the `Debug` level if it is enabled. */
  def debug(message: String): Unit = macro LoggerMacros.logDebug

  /** Logs a message and throwable at the `Debug` level if it is enabled. */
  def debug(message: String, thrown: Throwable): Unit = macro LoggerMacros.logDebugWith

  /** Logs a message at the specified level if it is enabled. */
  def apply(level: Logger.Level, message: String): Unit = macro LoggerMacros.log

  /** Logs a message and throwable at the specified level if it is enabled. */
  def apply(level: Logger.Level, message: String, thrown: Throwable): Unit = macro LoggerMacros.logWith

}

/**
 * Factory for `Logger` instances.
 */
object Logger {

  /** Ensures any logger can be used as a `java.util.logging.Logger`. */
  implicit def loggerToJavaLogger(logger: Logger): JLogger = macro LoggerMacros.convertLogger

  /** Creates a `Logger` using the name of the calling type. */
  def apply(): Logger = macro LoggerMacros.create

  /** Creates a `Logger` using the specified name. */
  def apply(name: String): Logger = macro LoggerMacros.createWithName

  /**
   * A minimal interface around a `java.util.logging.Level` that is entirely erased at runtime.
   */
  final class Level(val jLevel: JLevel) extends AnyVal

  /**
   * Definitions of the available logging levels.
   */
  object Level {

    /** Ensures any level can be used as a `java.util.logging.Level`. */
    implicit def levelToJavaLevel(level: Level): JLevel = macro LoggerMacros.convertLevel

    /** The error level. */
    def Error: Level = macro LoggerMacros.errorLevel

    /** The warn level. */
    def Warn: Level = macro LoggerMacros.warnLevel

    /** The info level. */
    def Info: Level = macro LoggerMacros.infoLevel

    /** The debug level. */
    def Debug: Level = macro LoggerMacros.debugLevel

  }

}

/**
 * Definitions of the logging macro expansions.
 */
object LoggerMacros {

  /** Macro that expands to the underlying representation of a logger object. */
  def convertLogger(c: Context)(logger: c.Expr[Logger]): c.Expr[JLogger] =
    c.universe.reify(logger.splice.jLogger)

  /** Macro that expands to the underlying representation of a level object. */
  def convertLevel(c: Context)(level: c.Expr[Logger.Level]): c.Expr[JLevel] =
    c.universe.reify(level.splice.jLevel)

  /** Macro that expands to the creation of the logger object. */
  def create(c: Context)(): c.Expr[Logger] =
    createWithName(c)(c.literal(c.enclosingClass.symbol.fullName))

  /** Macro that expands to the creation of the logger object. */
  def createWithName(c: Context)(name: c.Expr[String]): c.Expr[Logger] =
    c.universe.reify(new Logger(JLogger.getLogger(name.splice)))

  /** Macro that expands to the creation of the error level. */
  def errorLevel(c: Context): c.Expr[Logger.Level] =
    c.universe.reify(new Logger.Level(JLevel.SEVERE))

  /** Macro that expands to the creation of the warn level. */
  def warnLevel(c: Context): c.Expr[Logger.Level] =
    c.universe.reify(new Logger.Level(JLevel.WARNING))

  /** Macro that expands to the creation of the info level. */
  def infoLevel(c: Context): c.Expr[Logger.Level] =
    c.universe.reify(new Logger.Level(JLevel.INFO))

  /** Macro that expands to the creation of the debug level. */
  def debugLevel(c: Context): c.Expr[Logger.Level] =
    c.universe.reify(new Logger.Level(JLevel.CONFIG))

  /** Macro that expands to a test of the log's level. */
  def isErrorEnabled(c: Context { type PrefixType = Logger }): c.Expr[Boolean] =
    isLevelEnabled(c)(c.universe.reify(new Logger.Level(JLevel.SEVERE)))

  /** Macro that expands to a test of the log's level. */
  def isWarnEnabled(c: Context { type PrefixType = Logger }): c.Expr[Boolean] =
    isLevelEnabled(c)(c.universe.reify(new Logger.Level(JLevel.WARNING)))

  /** Macro that expands to a test of the log's level. */
  def isInfoEnabled(c: Context { type PrefixType = Logger }): c.Expr[Boolean] =
    isLevelEnabled(c)(c.universe.reify(new Logger.Level(JLevel.INFO)))

  /** Macro that expands to a test of the log's level. */
  def isDebugEnabled(c: Context { type PrefixType = Logger }): c.Expr[Boolean] =
    isLevelEnabled(c)(c.universe.reify(new Logger.Level(JLevel.CONFIG)))

  /** Macro that expands to a test of the log's level. */
  def isLevelEnabled(c: Context { type PrefixType = Logger })(level: c.Expr[Logger.Level]): c.Expr[Boolean] =
    c.universe.reify(c.prefix.splice.jLogger.isLoggable(level.splice.jLevel))

  /** Macro that expands to the conditional submission of an error log entry. */
  def logError(c: Context { type PrefixType = Logger })(message: c.Expr[String]): c.Expr[Unit] =
    log(c)(c.universe.reify(new Logger.Level(JLevel.SEVERE)), message)

  /** Macro that expands to the conditional submission of an error log entry. */
  def logErrorWith(c: Context { type PrefixType = Logger }) //
  (message: c.Expr[String], thrown: c.Expr[Throwable]): c.Expr[Unit] =
    logWith(c)(c.universe.reify(new Logger.Level(JLevel.SEVERE)), message, thrown)

  /** Macro that expands to the conditional submission of a warn log entry. */
  def logWarn(c: Context { type PrefixType = Logger })(message: c.Expr[String]): c.Expr[Unit] =
    log(c)(c.universe.reify(new Logger.Level(JLevel.WARNING)), message)

  /** Macro that expands to the conditional submission of a warn log entry. */
  def logWarnWith(c: Context { type PrefixType = Logger }) //
  (message: c.Expr[String], thrown: c.Expr[Throwable]): c.Expr[Unit] =
    logWith(c)(c.universe.reify(new Logger.Level(JLevel.WARNING)), message, thrown)

  /** Macro that expands to the conditional submission of an info log entry. */
  def logInfo(c: Context { type PrefixType = Logger })(message: c.Expr[String]): c.Expr[Unit] =
    log(c)(c.universe.reify(new Logger.Level(JLevel.INFO)), message)

  /** Macro that expands to the conditional submission of an info log entry. */
  def logInfoWith(c: Context { type PrefixType = Logger }) //
  (message: c.Expr[String], thrown: c.Expr[Throwable]): c.Expr[Unit] =
    logWith(c)(c.universe.reify(new Logger.Level(JLevel.INFO)), message, thrown)

  /** Macro that expands to the conditional submission of a debug log entry. */
  def logDebug(c: Context { type PrefixType = Logger })(message: c.Expr[String]): c.Expr[Unit] =
    log(c)(c.universe.reify(new Logger.Level(JLevel.CONFIG)), message)

  /** Macro that expands to the conditional submission of a debug log entry. */
  def logDebugWith(c: Context { type PrefixType = Logger }) //
  (message: c.Expr[String], thrown: c.Expr[Throwable]): c.Expr[Unit] =
    logWith(c)(c.universe.reify(new Logger.Level(JLevel.CONFIG)), message, thrown)

  /** Macro that expands to the conditional submission of a log entry. */
  def log(c: Context { type PrefixType = Logger })(level: c.Expr[Logger.Level], message: c.Expr[String]): c.Expr[Unit] =
    c.universe.reify {
      val jLogger = c.prefix.splice.jLogger
      val jLevel = level.splice.jLevel
      if (jLogger.isLoggable(jLevel))
        jLogger.logp(jLevel, enclosingClass(c).splice, enclosingMethod(c).splice, message.splice)
    }

  /** Macro that expands to the conditional submission of a log entry with a throwable. */
  def logWith(c: Context { type PrefixType = Logger }) //
  (level: c.Expr[Logger.Level], message: c.Expr[String], thrown: c.Expr[Throwable]): c.Expr[Unit] =
    c.universe.reify {
      val jLogger = c.prefix.splice.jLogger
      val jLevel = level.splice.jLevel
      if (jLogger.isLoggable(jLevel))
        jLogger.logp(jLevel, enclosingClass(c).splice, enclosingMethod(c).splice, message.splice, thrown.splice)
    }

  /** Captures the name of the enclosing class of the call. */
  def enclosingClass(c: Context) =
    c.literal(c.enclosingClass.symbol.fullName)

  /** Captures the name of the enclosing method of the call. */
  def enclosingMethod(c: Context) =
    c.literal(Option(c.enclosingMethod) map (_.symbol.name.decoded) getOrElse "<init>")

}