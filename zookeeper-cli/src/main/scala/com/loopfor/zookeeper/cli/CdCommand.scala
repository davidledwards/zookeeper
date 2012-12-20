package com.loopfor.zookeeper.cli

import com.loopfor.zookeeper._
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

object CdCommand {
  import Command._

  val Usage = """usage: cd [OPTIONS] [PATH|-]

  Changes the current working path to PATH if specified. If PATH is omitted,
  then `/` is assumed. In addition, if PATH is `-`, then the previous working
  path is chosen.

options:
  --check, -c                : check existence of node at working path
                               (does not fail command if nonexistent)
"""

  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    val last = new AtomicReference(Path("/"))

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = parse(args)
      val check = opts('check).asInstanceOf[Boolean]
      val params = opts('params).asInstanceOf[Seq[String]]
      val path = params.head match {
        case "-" => last.get
        case "" => Path("/")
        case p => context.resolve(p).normalize
      }
      if (check) {
        Node(path).exists() match {
          case Some(status) => println(path)
          case _ => println(path + ": does not exist")
        }
      }
      last.set(context)
      path
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
          case "-" => opts + ('params -> args)
          case LongOption("check") | ShortOption("c") => parse(rest, opts + ('check -> true))
          case LongOption(_) | ShortOption(_) => error(arg + ": no such option")
          case _ => opts + ('params -> args)
        }
      }
    }
    parse(args, Map(
          'check -> false,
          'params -> Seq("")))
  }
}
