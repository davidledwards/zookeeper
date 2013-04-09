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

object DeleteCommand {
  val Usage = """usage: rm|del [OPTIONS] PATH

  Deletes the node specified by PATH.

  The version of the node must be provided and match the version in ZooKeeper,
  otherwise the operation will fail. Alternatively, --force can be used to
  ensure deletion of the node without specifying a version.

options:
  --recursive, -r            : recursively deletes nodes under PATH
                               (implies --force on child nodes)
  --version, -v VERSION      : version required to match in ZooKeeper
  --force, -f                : forcefully delete node regardless of version
"""

  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    private lazy val parser =
      ("recursive", 'r') ~> enable ~~ false ++
      ("force", 'f') ~> enable ~~ false ++
      ("version", 'v') ~> asSomeInt ~~ None

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = parser parse args
      val recurse = opts[Boolean]("recursive")
      val version = {
        val force = opts[Boolean]("force")
        if (force || recurse) None
        else opts[Option[Int]]("version") match {
          case None => error("version must be specified; otherwise use --force")
          case v => v
        }
      }
      val path = if (opts.args.isEmpty) error("path must be specified") else opts.args.head
      val node = Node(context resolve path)
      try {
        node.delete(version)
      } catch {
        case _: NoNodeException => error(s"${node.path}: no such node")
        case _: BadVersionException => error(s"${version.get}: version does not match")
        case _: NotEmptyException => error(s"${node.path}: node has children")
      }
      context
    }
  }
}
