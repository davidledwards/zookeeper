/*
 * Copyright 2013 David Edwards
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.loopfor.zookeeper.cli

import scala.annotation.tailrec
import scala.language._

class Splitter(delims: Set[Char]) {
  def split(s: String): Seq[String] = {
    @tailrec def split(s: Seq[Char], terms: Seq[String]): Seq[String] = {
      val r = skip(s)
      r.headOption match {
        case Some('"') =>
          val (term, rest) = slurpQuote(r.tail)
          split(rest, terms :+ term)
        case Some(c) =>
          val (term, rest) = slurp(r)
          split(rest, terms :+ term)
        case _ => terms
      }
    }
    split(s, Seq())
  }

  @tailrec private def skip(s: Seq[Char]): Seq[Char] = s.headOption match {
    case Some(c) if delims contains c => skip(s.tail)
    case _ => s
  }

  private def slurp(s: Seq[Char]): (String, Seq[Char]) = {
    @tailrec def slurp(s: Seq[Char], buf: StringBuilder): (String, Seq[Char]) = s.headOption match {
      case Some(c) if !(delims contains c) => slurp(s.tail, buf + c)
      case _ => (buf.toString, s)
    }
    slurp(s, new StringBuilder)
  }

  private def slurpQuote(s: Seq[Char]): (String, Seq[Char]) = {
    @tailrec def slurpQuote(s: Seq[Char], buf: StringBuilder): (String, Seq[Char]) = s.headOption match {
      case Some('"') => (buf.toString, s.tail)
      case Some('\\') =>
        val rest = s.tail
        val (c, _rest) = rest.headOption match {
          case Some(c) if c == '"' || c == '\\' => (c, rest.tail)
          case _ => ('\\', rest)
        }
        slurpQuote(_rest, buf + c)
      case Some(c) => slurpQuote(s.tail, buf + c)
      case _ => (buf.toString, s)
    }
    slurpQuote(s, new StringBuilder)
  }
}

object Splitter {
  private val Whitespace = Set(' ', '\t', '\n', '\u000b', '\f', '\r')
  private val Default = Splitter()

  def apply(): Splitter = apply(Whitespace)

  def apply(delims: Set[Char]): Splitter = new Splitter(delims)

  def split(s: String): Seq[String] = Default split s

  def split(s: String, delims: Set[Char]): Seq[String] = Splitter(delims) split s
}
