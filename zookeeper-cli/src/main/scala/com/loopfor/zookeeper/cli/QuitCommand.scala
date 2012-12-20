package com.loopfor.zookeeper.cli

import com.loopfor.zookeeper._

object QuitCommand {
  val Usage = """usage: exit|quit

  Exits the CLI.
"""

  def apply(zk: Zookeeper) = new Command {
    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      zk.close()
      null
    }
  }
}
