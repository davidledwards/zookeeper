package com.loopfor.zookeeper.cli

import com.loopfor.zookeeper._

trait Command extends ((String, Seq[String], Path) => Path)

object Command {
  def error(message: String): Nothing = throw new CommandException(message)
}

class CommandException(message: String) extends Exception(message)
