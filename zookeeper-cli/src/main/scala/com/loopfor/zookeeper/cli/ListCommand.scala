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
import scala.language._

object ListCommand {
  val Usage= """usage: ls|dir [OPTIONS] [PATH...]

  List child nodes for each PATH. PATH may be omitted, in which case the
  current working path is assumed.

  When --long is specified, node names are optionally appended with `/` if the
  node has chidren or `*` if the node is ephemeral. In all cases, the version
  of the node follows.

options:
  --recursive, -r            : recursively list nodes
  --long, -l                 : display in long format
"""

  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    private lazy val parser =
      ("recursive", 'r') ~> enable ~~ false ++
      ("long", 'l') ~> set(formatLong _) ~~ formatShort _

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = parser parse args
      val recurse = opts[Boolean]("recursive")
      val format  = opts[(Node, Int) => String]("long")
      val paths = if (opts.args.size > 0) opts.args else Seq("")
      val count = paths.size
      (1 /: paths) { case (i, path) =>
        val node = Node(context resolve path)
        try {
          val children = node.children() sortBy { _.name }
          if (count > 1) println(s"${node.path}:")
          if (recurse)
            traverse(children, 0, format)
          else
            children foreach { child => println(format(child, 0)) }
          if (count > 1 && i < count) println()
        } catch {
          case _: NoNodeException => println(s"${node.path}: no such node")
        }
        i + 1
      }
      context
    }
  }

  private def formatShort(node: Node, depth: Int): String =
    indent(depth) + node.name

  private def formatLong(node: Node, depth: Int): String = {
    indent(depth) + node.name + (node.exists() match {
      case Some(status) =>
        (if (status.numChildren > 0) "/ " else if (status.ephemeralOwner != 0) "* " else " ") +
        status.version
      case _ => " ?"
    })
  }

  private def indent(depth: Int) = {
    def pad(depth: Int) = { Array.fill((depth - 1) * 2)(' ') mkString }
    if (depth > 0) pad(depth) + "+ " else ""
  }

  private def traverse(children: Seq[Node], depth: Int, format: (Node, Int) => String) {
    children foreach { child =>
      println(format(child, depth))
      try {
        traverse(child.children() sortBy { _.name }, depth + 1, format)
      } catch {
        case _: NoNodeException =>
      }
    }
  }
}
