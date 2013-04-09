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
import java.io.{FileInputStream, FileNotFoundException, IOException}
import java.nio.charset.Charset
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuilder

object SetCommand {
  val Usage = """usage: set [OPTIONS] PATH [DATA]

  Sets the DATA for the node specified by PATH.

  DATA is optional, and if omitted, associates an empty byte array with the
  node. If DATA does not begin with `@`, it is assumed to be a Unicode string,
  which by default, is encoded as UTF-8 at time of storage. The --encoding
  option is used to provide an alternative CHARSET, which may be any of the
  possible character sets installed on the underlying JRE.

  If DATA is prefixed with `@`, this indicates that the remainder of the
  argument is a filename and whose contents will be attached to the node.

options:
  --encoding, -e CHARSET     : charset used for encoding DATA (default=UTF-8)
  --version, -v VERSION      : version required to match in ZooKeeper
  --force, -f                : forcefully set DATA regardless of version
"""

  private val UTF_8 = Charset forName "UTF-8"

  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    private lazy val parser =
      ("encoding", 'e') ~> asCharset ~~ UTF_8 ++
      ("version", 'v') ~> asSomeInt ~~ None ++
      ("force", 'f') ~> enable ~~ false

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = parser parse args
      val version = {
        val force = opts[Boolean]("force")
        if (force) None
        else opts[Option[Int]]("version") match {
          case None => error("version must be specified; otherwise use --force")
          case v => v
        }
      }
      val path = if (opts.args.isEmpty) error("path must be specified") else opts.args.head
      val data = opts.args.tail.headOption match {
        case Some(d) => d.headOption match {
          case Some('@') =>
            val name = d drop 1
            val file = try new FileInputStream(name) catch {
              case _: FileNotFoundException => error(s"$name: file not found")
              case _: SecurityException => error(s"$name: access denied")
            }
            try read(file) catch {
              case e: IOException => error(s"$name: I/O error: ${e.getMessage}")
            } finally
              file.close()
          case _ => d getBytes opts[Charset]("encoding")
        }
        case _ => Array[Byte]()
      }
      val node = Node(context resolve path)
      try node.set(data, version) catch {
        case _: NoNodeException => error(s"${node.path}: no such node")
        case _: BadVersionException => error(s"${version.get}: version does not match")
      }
      context
    }
  }

  private def read(file: FileInputStream): Array[Byte] = {
    @tailrec def read(buffer: ArrayBuilder[Byte]): Array[Byte] = {
      val c = file.read()
      if (c == -1) buffer.result else read(buffer += c.toByte)
    }
    read(ArrayBuilder.make[Byte])
  }
}
