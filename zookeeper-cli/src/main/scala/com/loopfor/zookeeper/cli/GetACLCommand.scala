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

object GetACLCommand {
  import Command._

  val Usage = """usage: getacl [OPTIONS] [PATH...]

  Gets the ACL for the node specified by each PATH. PATH may be omitted, in
  which case the current working path is assumed.

options:
"""

  def apply(zk: Zookeeper) = new Command {
    implicit private val _zk = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = parse(args)
      val paths = opts('params).asInstanceOf[Seq[String]]
      val count = paths.size
      (1 /: paths) { case (i, path) =>
        val node = Node(context resolve path)
        try {
          val (acl, status) = node.getACL()
          if (count > 1) println(node.path + ":")
          acl foreach { println _ }
          if (count > 1 && i < count) println()
        } catch {
          case _: NoNodeException => println(node.path + ": no such node")
        }
        i + 1
      }
      context
    }
  }

  private def parse(args: Seq[String]): Map[Symbol, Any] = {
    def parse(args: Seq[String], opts: Map[Symbol, Any]): Map[Symbol, Any] = {
      if (args.isEmpty)
        opts
      else {
        val arg = args.head
        val rest = args.tail
        arg match {
          case "--" => opts + ('params -> rest)
          case LongOption(_) | ShortOption(_) => error(arg + ": no such option")
          case _ => opts + ('params -> args)
        }
      }
    }
    parse(args, Map(
          'params -> Seq("")))
  }
}
