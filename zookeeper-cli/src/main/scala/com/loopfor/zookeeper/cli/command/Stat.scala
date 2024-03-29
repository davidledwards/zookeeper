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
import java.text.SimpleDateFormat
import java.util.Date

object Stat {
  val Usage = """usage stat|info [PATH...]

  Gets the status for the node specified by each PATH. PATH may be omitted, in
  which case the current working path is assumed.

options:
  --compact, -c              : display in compact format
"""

  private type FormatFunction = Status => String

  private val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

  private lazy val opts =
    ("compact", 'c') ~> just(formatCompact _) ~~ formatLong _ ::
    Nil

  def command(zk: Zookeeper) = new CommandProcessor {
    implicit val _zk: Zookeeper = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val optr = opts <~ args
      val format = optr[FormatFunction]("compact")
      val nodes = pathArgs(optr).map { path => Node(context.resolve(path)) }
      stat(nodes, format)
      context
    }
  }

  def find(zk: Zookeeper, args: Seq[String]) = new FindProcessor {
    val optr = opts <~ args
    val format = optr[FormatFunction]("compact")

    def apply(node: Node): Unit = {
      stat(Seq(node), format)
    }
  }

  private def stat(nodes: Seq[Node], format: FormatFunction): Unit = {
    val count = nodes.size
    nodes.foldLeft(1) { case (i, node) =>
      node.exists() match {
        case Some(status) =>
          if (count > 1) println(s"${node.path}:")
          println(format(status))
          if (count > 1 && i < count) println()
        case _ => println(s"${node.path}: no such node")
      }
      i + 1
    }
  }

  private def pathArgs(optr: OptResult): Seq[Path] = optr.args match {
    case Seq() => Seq(Path(""))
    case paths => paths.map { Path(_) }
  }

  private def formatLong(status: Status): String = {
    s"czxid: ${status.czxid}\n" +
    s"mzxid: ${status.mzxid}\n" +
    s"pzxid: ${status.pzxid}\n" +
    s"ctime: ${status.ctime} (${dateFormat.format(new Date(status.ctime))})\n" +
    s"mtime: ${status.mtime} (${dateFormat.format(new Date(status.mtime))})\n" +
    s"version: ${status.version}\n" +
    s"cversion: ${status.cversion}\n" +
    s"aversion: ${status.aversion}\n" +
    s"owner: ${status.ephemeralOwner}\n" +
    s"datalen: ${status.dataLength}\n" +
    s"children: ${status.numChildren}"
  }

  private def formatCompact(status: Status): String = {
    s"${status.czxid} ${status.mzxid} ${status.pzxid} ${dateFormat.format(new Date(status.ctime))} " +
      s"${dateFormat.format(new Date(status.mtime))} ${status.version} ${status.cversion} " +
      s"${status.aversion} ${status.ephemeralOwner} ${status.dataLength} ${status.numChildren}"
  }
}
