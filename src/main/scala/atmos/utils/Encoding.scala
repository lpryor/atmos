/* Encoding.scala
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
package atmos.utils

/**
 * A strategy for encoding and decoding objects of type `T`.
 */
trait Encoding[T] {

  /** Encodes and returns the specified value. */
  def apply(value: T): String

  /** Decodes and returns the specified string. */
  def unapply(encoded: String): T

}

/**
 * Common encoding implementations.
 */
object Encoding {

  /**
   * An encoding for boolean values.
   */
  implicit object Booleans extends Encoding[Boolean] {
    override def apply(value: Boolean) = value.toString
    override def unapply(encoded: String) = encoded.toBoolean
  }

  /**
   * An encoding for byte values.
   */
  implicit object Bytes extends Encoding[Byte] {
    override def apply(value: Byte) = value.toString
    override def unapply(encoded: String) = encoded.toByte
  }

  /**
   * An encoding for short values.
   */
  implicit object Shorts extends Encoding[Short] {
    override def apply(value: Short) = value.toString
    override def unapply(encoded: String) = encoded.toShort
  }

  /**
   * An encoding for integer values.
   */
  implicit object Ints extends Encoding[Int] {
    override def apply(value: Int) = value.toString
    override def unapply(encoded: String) = encoded.toInt
  }

  /**
   * An encoding for float values.
   */
  implicit object Floats extends Encoding[Float] {
    override def apply(value: Float) = value.toString
    override def unapply(encoded: String) = encoded.toFloat
  }

  /**
   * An encoding for long values.
   */
  implicit object Longs extends Encoding[Long] {
    override def apply(value: Long) = value.toString
    override def unapply(encoded: String) = encoded.toLong
  }

  /**
   * An encoding for double values.
   */
  implicit object Doubles extends Encoding[Double] {
    override def apply(value: Double) = value.toString
    override def unapply(encoded: String) = encoded.toDouble
  }

  /**
   * An encoding for string values.
   */
  implicit object Strings extends Encoding[String] {
    override def apply(value: String) = value
    override def unapply(encoded: String) = encoded
  }

}