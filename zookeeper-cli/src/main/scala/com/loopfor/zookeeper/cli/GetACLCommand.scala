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

object GetACLCommand {
  val Usage = """usage: getacl [OPTIONS] [PATH...]

  Gets the ACL for the node specified by each PATH. PATH may be omitted, in
  which case the current working path is assumed.
"""

  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    private lazy val parser = OptParser(Seq())

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = parser parse args
      val paths = opts.args
      val count = paths.size
      (1 /: paths) { case (i, path) =>
        val node = Node(context resolve path)
        try {
          val (acl, status) = node.getACL()
          if (count > 1) println(s"${node.path}:")
          acl foreach { println _ }
          if (count > 1 && i < count) println()
        } catch {
          case _: NoNodeException => println(s"${node.path}: no such node")
        }
        i + 1
      }
      context
    }
  }
}
