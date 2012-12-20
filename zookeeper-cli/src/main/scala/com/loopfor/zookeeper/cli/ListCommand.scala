package com.loopfor.zookeeper.cli

import com.loopfor.zookeeper._
import scala.annotation.tailrec
import scala.language._

object ListCommand {
  import Command._

  val Usage= """usage: ls|dir [OPTIONS] [PATH...]

  List child nodes for each PATH. PATH may be omitted, in which case the
  current working path is assumed.

  When --long is specified, node names are optionally appended with `/` if the
  node has chidren or `*` if the node is ephemeral. In all cases, the version
  of the node follows.

options:
  --recursive, -r            : recursively list nodes
  --long, -l                 : display in long format
"""

  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = parse(args)
      val recurse = opts('recursive).asInstanceOf[Boolean]
      val format = opts('format).asInstanceOf[(Node, Int) => String]
      val paths = opts('params).asInstanceOf[Seq[String]]
      val count = paths.size
      (1 /: paths) { case (i, path) =>
        val node = Node(context resolve path)
        try {
          val children = node.children() sortBy { _.name }
          if (count > 1) println(node.path + ":")
          if (recurse)
            traverse(children, 0, format)
          else
            children foreach { child => println(format(child, 0)) }
          if (count > 1 && i < count) println()
        } catch {
          case _: NoNodeException => println(node.path + ": no such node")
        }
        i + 1
      }
      context
    }
  }

  private def formatShort(node: Node, depth: Int): String =
    indent(depth) + node.name

  private def formatLong(node: Node, depth: Int): String = {
    indent(depth) + node.name + (node.exists() match {
      case Some(status) =>
        (if (status.numChildren > 0) "/ " else if (status.ephemeralOwner != 0) "* " else " ") +
        status.version
      case _ => " ?"
    })
  }

  private def indent(depth: Int) = {
    def pad(depth: Int) = { Array.fill((depth - 1) * 2)(' ') mkString }
    if (depth > 0) pad(depth) + "+ " else ""
  }

  private def traverse(children: Seq[Node], depth: Int, format: (Node, Int) => String) {
    children foreach { child =>
      println(format(child, depth))
      try {
        traverse(child.children() sortBy { _.name }, depth + 1, format)
      } catch {
        case _: NoNodeException =>
      }
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
          case LongOption("recursive") | ShortOption("r") => parse(rest, opts + ('recursive -> true))
          case LongOption("long") | ShortOption("l") => parse(rest, opts + ('format -> formatLong _))
          case LongOption(_) | ShortOption(_) => error(arg + ": no such option")
          case _ => opts + ('params -> args)
        }
      }
    }
    parse(args, Map(
          'recursive -> false,
          'format -> formatShort _,
          'params -> Seq("")))
  }
}
