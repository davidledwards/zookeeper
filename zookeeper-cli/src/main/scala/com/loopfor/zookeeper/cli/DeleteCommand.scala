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

import com.loopfor.zookeeper._
import scala.annotation.tailrec

object DeleteCommand {
  import Command._

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

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = parse(args)
      val recurse = opts('recursive).asInstanceOf[Boolean]
      val version = {
        val force = opts('force).asInstanceOf[Boolean]
        if (force) None
        else opts('version).asInstanceOf[Option[Int]] match {
          case None => error("version must be specified; otherwise use --force")
          case v => v
        }
      }
      val params = opts('params).asInstanceOf[Seq[String]]
      val path = if (params.isEmpty) error("path must be specified") else params.head
      val node = Node(context resolve path)
      try {
        node.delete(version)
      } catch {
        case _: NoNodeException => error(node.path + ": no such node")
        case _: BadVersionException => error(version.get + ": version does not match")
        case _: NotEmptyException => error(node.path + ": node has children")
      }
      context
    }
  }

  private def parse(args: Seq[String]): Map[Symbol, Any] = {
    @tailrec def parse(args: Seq[String], opts: Map[Symbol, Any]): Map[Symbol, Any] = {
      if (args.isEmpty)
        opts
      else {
        val arg = args.head
        val rest = args.tail
        arg match {
          case "--" => opts + ('params -> rest)
          case LongOption("recursive") | ShortOption("r") => parse(rest, opts + ('recursive -> true))
          case LongOption("force") | ShortOption("f") => parse(rest, opts + ('force -> true))
          case LongOption("version") | ShortOption("v") => rest.headOption match {
            case Some(version) =>
              val _version = try version.toInt catch {
                case _: NumberFormatException => error(version + ": version must be an integer")
              }
              parse(rest.tail, opts + ('version -> Some(_version)))
            case _ => error(arg + ": missing argument")
          }
          case LongOption(_) | ShortOption(_) => error(arg + ": no such option")
          case _ => opts + ('params -> args)
        }
      }
    }
    parse(args, Map(
          'recursive -> false,
          'force -> false,
          'version -> None,
          'params -> Seq[String]()))
  }
}
