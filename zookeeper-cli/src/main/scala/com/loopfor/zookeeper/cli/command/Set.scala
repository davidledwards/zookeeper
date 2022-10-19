/*
 * Copyright 2020 David Edwards
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
package com.loopfor.zookeeper.cli.command

import com.loopfor.scalop._
import com.loopfor.zookeeper._
import com.loopfor.zookeeper.cli._
import java.io.{FileInputStream, FileNotFoundException, IOException}
import java.nio.charset.Charset
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuilder

object Set {
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

  def command(zk: Zookeeper) = new CommandProcessor {
    implicit val _zk: Zookeeper = zk

    val opts =
      ("encoding", 'e') ~> as[Charset] ~~ UTF_8 ::
      ("version", 'v') ~> as[Option[Int]] ~~ None ::
      ("force", 'f') ~> just(true) ~~ false ::
      Nil

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val optr = opts <~ args
      val version = versionOpt(optr)
      val (path, afterPath) = pathArg(optr)
      val data = dataArg(optr, afterPath)
      val node = Node(context.resolve(path))
      set(node, version, data)
      context
    }
  }

  def find(zk: Zookeeper, args: Seq[String]) = new FindProcessor {
    val opts =
      ("encoding", 'e') ~> as[Charset] ~~ UTF_8 ::
      Nil
    val optr = opts <~ args
    val data = dataArg(optr, optr.args)

    def apply(node: Node): Unit = {
      set(node, None, data)
    }
  }

  private def set(node: Node, version: Option[Int], data: Array[Byte]): Unit = {
    try node.set(data, version) catch {
      case _: NoNodeException => complain(s"${node.path}: no such node")
      case _: BadVersionException => complain(s"${version.get}: version does not match")
    }
  }

  private def versionOpt(optr: OptResult): Option[Int] = {
    val force = optr[Boolean]("force")
    if (force) None
    else optr[Option[Int]]("version") match {
      case None => complain("version must be specified; otherwise use --force")
      case v => v
    }
  }

  private def pathArg(optr: OptResult): (Path, Seq[String]) = optr.args match {
    case Seq(path, rest @ _*) => (Path(path), rest)
    case Seq() => complain("path must be specified")
  }

  private def dataArg(optr: OptResult, args: Seq[String]): Array[Byte] = args match {
    case Seq(data, _*) => data.headOption match {
      case Some('@') =>
        val name = data.drop(1)
        val file = try new FileInputStream(name) catch {
          case _: FileNotFoundException => complain(s"$name: file not found")
          case _: SecurityException => complain(s"$name: access denied")
        }
        try read(file) catch {
          case e: IOException => complain(s"$name: I/O error: ${e.getMessage}")
        } finally
          file.close()
      case _ => data.getBytes(optr[Charset]("encoding"))
    }
    case Seq() => Array.empty[Byte]
  }

  private def read(file: FileInputStream): Array[Byte] = {
    @tailrec def read(buffer: ArrayBuilder[Byte]): Array[Byte] = {
      val c = file.read()
      if (c == -1) buffer.result() else read(buffer += c.toByte)
    }
    read(ArrayBuilder.make[Byte])
  }
}
