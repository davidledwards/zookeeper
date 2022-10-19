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

object Ls {
  val Usage = """usage: ls|dir [OPTIONS] [PATH...]

  List child nodes for each PATH. PATH may be omitted, in which case the
  current working path is assumed.

  When --long is specified, node names are optionally appended with `/` if the
  node has chidren or `*` if the node is ephemeral. In all cases, the version
  of the node follows.

options:
  --recursive, -r            : recursively list nodes
  --long, -l                 : display in long format
"""

  private type FormatFunction = (Node, Int) => String

  def command(zk: Zookeeper) = new CommandProcessor {
    implicit val _zk: Zookeeper = zk

    lazy val opts =
      ("recursive", 'r') ~> just(true) ~~ false ::
      ("long", 'l') ~> just(formatLong _) ~~ formatShort _ ::
      Nil

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val optr = opts <~ args
      val recurse = optr[Boolean]("recursive")
      val format = optr[FormatFunction]("long")
      val nodes = pathArgs(optr).map { path => Node(context.resolve(path)) }
      list(nodes, recurse, format)
      context
    }
  }

  def find(zk: Zookeeper, args: Seq[String]) = new FindProcessor {
    val opts =
      ("long", 'l') ~> just(formatLong _) ~~ formatShort _ ::
      Nil

    val optr = opts <~ args
    val format = optr[FormatFunction]("long")

    def apply(node: Node): Unit = {
      list(Seq(node), false, format)
    }
  }

  private def list(nodes: Seq[Node], recurse: Boolean, format: FormatFunction): Unit = {
    val count = nodes.size
    nodes.foldLeft(1) { case (i, node) =>
      try {
        val children = node.children().sortBy { _.name }
        if (count > 1) println(s"${node.path}:")
        if (recurse)
          traverse(children, 0, format)
        else
          children.foreach { child => println(format(child, 0)) }
        if (count > 1 && i < count) println()
      } catch {
        case _: NoNodeException => println(s"${node.path}: no such node")
      }
      i + 1
    }
  }

  private def pathArgs(optr: OptResult): Seq[Path] = optr.args match {
    case Seq() => Seq(Path(""))
    case paths => paths.map { Path(_) }
  }

  private def formatShort(node: Node, depth: Int): String = {
    indent(depth) + node.name
  }

  private def formatLong(node: Node, depth: Int): String = {
    indent(depth) + node.name + (node.exists() match {
      case Some(status) =>
        (if (status.numChildren > 0) "/ " else if (status.ephemeralOwner != 0) "* " else " ") +
        status.version
      case _ => " ?"
    })
  }

  private def indent(depth: Int) = {
    def pad(depth: Int) = " ".repeat((depth - 1) * 2)
    if (depth > 0) pad(depth) + "+ " else ""
  }

  private def traverse(children: Seq[Node], depth: Int, format: (Node, Int) => String): Unit = {
    children foreach { child =>
      println(format(child, depth))
      try {
        traverse(child.children().sortBy { _.name }, depth + 1, format)
      } catch {
        case _: NoNodeException =>
      }
    }
  }
}
