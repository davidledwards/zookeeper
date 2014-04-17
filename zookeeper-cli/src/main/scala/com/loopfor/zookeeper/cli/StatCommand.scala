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
import java.text.SimpleDateFormat
import java.util.Date

object StatCommand {
  val Usage = """usage stat|info [PATH...]

  Gets the status for the node specified by each PATH. PATH may be omitted, in
  which case the current working path is assumed.
"""

  private val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    private lazy val parser = OptParser(Seq())

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = parser parse args
      val paths = if (opts.args.size > 0) opts.args else Seq("")
      val count = paths.size
      (1 /: paths) { case (i, path) =>
        val node = Node(context resolve path)
        node.exists() match {
          case Some(status) =>
            if (count > 1) println(s"${node.path}:")
            println(format(status))
            if (count > 1 && i < count) println()
          case _ => println(s"${node.path}: no such node")
        }
        i + 1
      }
      context
    }
  }

  private def format(status: Status): String = {
    "czxid: " + status.czxid + "\n" +
    "mzxid: " + status.mzxid + "\n" +
    "pzxid: " + status.pzxid + "\n" +
    "ctime: " + status.ctime + " (" + dateFormat.format(new Date(status.ctime)) + ")\n" +
    "mtime: " + status.mtime + " (" + dateFormat.format(new Date(status.mtime)) + ")\n" +
    "version: " + status.version + "\n" +
    "cversion: " + status.cversion + "\n" +
    "aversion: " + status.aversion + "\n" +
    "owner: " + status.ephemeralOwner + "\n" +
    "datalen: " + status.dataLength + "\n" +
    "children: " + status.numChildren
  }
}
