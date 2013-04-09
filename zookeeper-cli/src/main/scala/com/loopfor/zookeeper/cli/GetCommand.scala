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

import com.loopfor.scalop._
import com.loopfor.zookeeper._
import java.nio.charset.Charset
import scala.annotation.tailrec
import scala.language._

object GetCommand {
  val Usage = """usage: get [OPTIONS] [PATH...]

  Gets the data for the node specified by each PATH. PATH may be omitted, in
  which case the current working path is assumed.

  By default, data is displayed in a hex/ASCII table with offsets, though the
  output format can be changed using --string or --binary. If --string is
  chosen, it may be necessary to also specify the character encoding if the
  default of `UTF-8` is incorrect. The CHARSET in --encoding refers to any of
  the possible character sets installed on the underlying JRE.

options:
  --hex, -h                  : display data as hex/ASCII (default)
  --string, -s               : display data as string (see --encoding)
  --binary, -b               : display data as binary
  --encoding, -e CHARSET     : charset for use with --string (default=UTF-8)
"""

  private val UTF_8 = Charset forName "UTF-8"

  private type DisplayFunction = (Array[Byte], OptResult) => Unit

  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    private lazy val parser =
      ("hex", 'h') ~> enable ~~ false ++
      ("string", 's') ~> enable ~~ false ++
      ("binary", 'b') ~> enable ~~ false ++
      ("encoding", 'e') ~> asCharset ~~ UTF_8

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = parser parse args
      val display =
        if (opts[Boolean]("hex")) displayHex _
        else if (opts[Boolean]("binary")) displayBinary _
        else if (opts[Boolean]("string")) displayString _
        else displayHex _
      val paths = if (opts.args.size > 0) opts.args else Seq("")
      val count = paths.size
      (1 /: paths) { case (i, path) =>
        val node = Node(context resolve path)
        try {
          val (data, status) = node.get()
          if (count > 1) println(s"${node.path}:")
          display(data, opts)
          if (count > 1 && i < count) println()
        } catch {
          case _: NoNodeException => println(s"${node.path}: no such node")
        }
        i + 1
      }
      context
    }
  }

  private def displayHex(data: Array[Byte], opts: OptResult) {
    @tailrec def display(n: Int) {
      def charOf(b: Byte) = if (b >= 32 && b < 127) b.asInstanceOf[Char] else '.'

      def pad(n: Int) = Array.fill(n)(' ') mkString

      if (n < data.length) {
        val l = Math.min(n + 16, data.length) - n
        print("%08x  " format n)
        print((for (i <- n until (n + l)) yield "%02x " format data(i)).mkString)
        print(pad((16 - l) * 3))
        print(" |")
        print((for (i <- n until (n + l)) yield charOf(data(i))).mkString)
        print(pad(16 - l))
        println("|")
        display(n + l)
      }
    }
    display(0)
  }

  private def displayString(data: Array[Byte], opts: OptResult) = {
    val cs = opts[Charset]("encoding")
    println(new String(data, cs))
  }

  private def displayBinary(data: Array[Byte], opts: OptResult) =
    Console.out.write(data, 0, data.length)
}
