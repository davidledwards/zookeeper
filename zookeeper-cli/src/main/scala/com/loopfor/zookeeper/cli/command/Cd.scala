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

object Cd {
  val Usage = """usage: cd [OPTIONS] [PATH|-]

  Changes the current working path to PATH if specified. If PATH is omitted,
  then `/` is assumed. In addition, if PATH is `-`, then the previous working
  path is chosen.

options:
  --check, -c                : check existence of node at working path
                               (does not fail command if nonexistent)
"""

  def command(zk: Zookeeper) = new CommandProcessor {
    implicit val _zk: Zookeeper = zk
    var last = Path("/")

    val opts =
      ("check", 'c') ~> just(true) ~~ false ::
      Nil

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val optr = opts <~ args
      val check = optr[Boolean]("check")
      val path = context.resolve(pathArg(optr.args, last)).normalize
      if (check) Node(path).exists() match {
        case Some(_) => println(path)
        case _ => println(s"$path: does not exist")
      }
      last = context
      path
    }
  }

  private def checkOpt(implicit opts: OptResult): Boolean = opts("check")

  private def pathArg(args: Seq[String], last: Path): Path = args.headOption match {
    case Some("-") => last
    case Some(path) => Path(path)
    case None => Path("/")
  }
}
