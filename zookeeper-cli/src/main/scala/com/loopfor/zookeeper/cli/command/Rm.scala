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
package com.loopfor.zookeeper.cli.command

import com.loopfor.scalop._
import com.loopfor.zookeeper._
import com.loopfor.zookeeper.cli._

object Rm {
  val Usage = """usage: rm|del [OPTIONS] PATH

  Deletes the node specified by PATH.

  The version of the node must be provided and match the version in ZooKeeper,
  otherwise the operation will fail. Alternatively, --force can be used to
  ensure deletion of the node without specifying a version.

  If --recursive is specified, then --force is automatically implied and,
  consequently, --version is ignored. In this scenario, the candidate set of
  nodes marked for deletion is constructed by performing a traversal of child
  nodes beginning at PATH. Then, an attempt is made to delete those nodes in a
  bottom-up manner. Since this operation is not atomically performed, there
  exists the possibility of concurrent modifications to the subtree of PATH
  from another process, thus resulting in spurious failures, most notably the
  case where nodes are created after expansion of the subtree.

  Use --recursive with caution since this option can be quite destructive.
  This command does not allow recursive deletion when the effective PATH
  is `/`.

options:
  --recursive, -r            : recursively deletes nodes under PATH
                               (implies --force on child nodes)
  --version, -v VERSION      : version required to match in ZooKeeper
  --force, -f                : forcefully delete node regardless of version
"""

  def command(zk: Zookeeper) = new CommandProcessor {
    implicit val _zk = zk

    lazy val parser =
      ("recursive", 'r') ~> enable ~~ false ++
      ("force", 'f') ~> enable ~~ false ++
      ("version", 'v') ~> asSomeInt ~~ None

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      implicit val opts = parser parse args
      val recurse = recursiveOpt
      val version = versionOpt
      val path = pathArg
      val node = Node(context resolve path)
      delete(node, recurse, version)
      context
    }
  }

  def find(zk: Zookeeper, args: Seq[String]) = new FindProcessor {
    implicit val _zk = zk
    val parser =
      ("recursive", 'r') ~> enable ~~ false
    implicit val opts = parser parse args
    val recurse = recursiveOpt

    def apply(node: Node): Unit = {
      delete(node, recurse, None)
    }
  }

  private def delete(node: Node, recurse: Boolean, version: Option[Int])(implicit zk: Zookeeper): Unit = {
    val deletions = if (recurse) {
      if (node.path.path == "/") complain("/: recursive deletion of root path not allowed")
      def traverse(node: Node, deletions: Seq[Node]): Seq[Node] = {
        try {
          ((node +: deletions) /: node.children()) { case (d, child) => traverse(child, d) }
        } catch {
          case _: NoNodeException => deletions
        }
      }
      traverse(node, Seq.empty) map { (_, Option.empty[Int]) }
    } else
      Seq((node, version))
    deletions foreach { case (node, version) =>
      try {
        node.delete(version)
      } catch {
        case _: NoNodeException => complain(s"${node.path}: no such node")
        case _: BadVersionException => complain(s"${version.get}: version does not match")
        case _: NotEmptyException => complain(s"${node.path}: node has children")
      }
    }
  }

  private def recursiveOpt(implicit opts: OptResult): Boolean = opts("recursive")

  private def versionOpt(implicit opts: OptResult): Option[Int] = {
    val force = opts[Boolean]("force")
    val recurse = opts[Boolean]("recursive")
    if (force || recurse) None
    else opts[Option[Int]]("version") match {
      case None => complain("version must be specified; otherwise use --force")
      case v => v
    }
  }

  private def pathArg(implicit opts: OptResult): Path = opts.args match {
    case Seq(path, _*) => Path(path)
    case Seq() => complain("path must be specified")
  }
}

