package com.loopfor.zookeeper.cli

import com.loopfor.zookeeper._
import scala.annotation.tailrec

object PwdCommand {
  import Command._

  val Usage = """usage: pwd [OPTIONS]

  Shows the current working path.

options:
  --check, -c                : check existence of node at working path
"""

  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = parse(args)
      val check = opts('check).asInstanceOf[Boolean]
      print(context)
      if (check && Node(context).exists().isEmpty) print(": does not exist")
      println()
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
          case LongOption("check") | ShortOption("c") => parse(rest, opts + ('check -> true))
          case LongOption(_) | ShortOption(_) => error(arg + ": no such option")
          case _ => opts + ('params -> args)
        }
      }
    }
    parse(args, Map(
          'check -> false))
  }
}
