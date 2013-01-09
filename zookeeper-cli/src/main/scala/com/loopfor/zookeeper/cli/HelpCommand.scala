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

object HelpCommand {
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
  config         show connection information and session state
  help, ?        show available commands
  exit, quit     exit program
"""

  private val Commands = Map(
        "ls" -> ListCommand.Usage,
        "dir" -> ListCommand.Usage,
        "cd" -> CdCommand.Usage,
        "pwd" -> PwdCommand.Usage,
        "get" -> GetCommand.Usage,
        "set" -> SetCommand.Usage,
        "stat" -> StatCommand.Usage,
        "info" -> StatCommand.Usage,
        "mk" -> CreateCommand.Usage,
        "create" -> CreateCommand.Usage,
        "rm" -> DeleteCommand.Usage,
        "del" -> DeleteCommand.Usage,
        "getacl" -> GetACLCommand.Usage,
        "setacl" -> SetACLCommand.Usage,
        "config" -> ConfigCommand.Usage,
        "help" -> HelpCommand.Usage,
        "exit" -> QuitCommand.Usage,
        "quit" -> QuitCommand.Usage
        )

  def apply() = new Command {
    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      if (args.isEmpty)
        println(Usage)
      else {
        val cmd = args.head
        Commands get cmd match {
          case Some(usage) => println(usage)
          case _ => println(cmd + ": no such command")
        }
      }
      context
    }
  }
}
