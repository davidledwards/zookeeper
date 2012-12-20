package com.loopfor.zookeeper.cli

import com.loopfor.zookeeper._
import java.text.SimpleDateFormat
import java.util.Date

object StatCommand {
  import Command._

  val Usage = """usage stat|info [OPTIONS] [PATH...]

  Gets the status for the node specified by each PATH. PATH may be omitted, in
  which case the current working path is assumed.

options:
"""

  private val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = parse(args)
      val paths = opts('params).asInstanceOf[Seq[String]]
      val count = paths.size
      (1 /: paths) { case (i, path) =>
        val node = Node(context resolve path)
        node.exists() match {
          case Some(status) =>
            if (count > 1) println(node.path + ":")
            println(format(status))
            if (count > 1 && i < count) println()
          case _ => println(node.path + ": no such node")
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
