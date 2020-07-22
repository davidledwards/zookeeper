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

import com.loopfor.zookeeper._
import com.loopfor.zookeeper.cli._

object Help {
  val Usage = """Type `help COMMAND` for more information.

  The TAB key can be used to auto-complete commands and node paths. Pressing
  TAB on the first argument will try to find commands matching the prefix,
  whereas, all subsequent TAB depressions will attempt to match existing nodes
  relative to the current working path.

  In all cases requiring paths, both absolute and relative forms may be given.
  Absolute paths start with `/`, which means that the current working path is
  ignored. Relative paths, on the other hand, are resolved in the context of
  the current working path. In addition, both `.` and `..` may be used in paths
  indicating the current and parent node, respectively.

commands:
  ls, dir        list nodes
  cd             change working path
  pwd            show working path
  get            get node data
  set            set node data
  stat, info     get node status
  mk, create     create new node
  rm, del        delete existing node
  getacl         get node ACL
  setacl         set node ACL
  find           find nodes matching expression
  config         show connection information and session state
  help, ?        show available commands
  exit, quit     exit program
"""

  private val Commands = Map(
        "ls" -> Ls.Usage,
        "dir" -> Ls.Usage,
        "cd" -> Cd.Usage,
        "pwd" -> Pwd.Usage,
        "get" -> Get.Usage,
        "set" -> Set.Usage,
        "stat" -> Stat.Usage,
        "info" -> Stat.Usage,
        "mk" -> Mk.Usage,
        "create" -> Mk.Usage,
        "rm" -> Rm.Usage,
        "del" -> Rm.Usage,
        "getacl" -> GetACL.Usage,
        "setacl" -> SetACL.Usage,
        "find" -> Find.Usage,
        "config" -> Config.Usage,
        "help" -> Help.Usage,
        "exit" -> Quit.Usage,
        "quit" -> Quit.Usage
        )

  def command() = new CommandProcessor {
    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      args match {
        case Seq(c, _*) => println(usageOf(c))
        case _ => println(Usage)
      }
      context
    }
  }

  def usageOf(cmd: String): String = Commands.getOrElse(cmd, s"$cmd: no such command")
}
