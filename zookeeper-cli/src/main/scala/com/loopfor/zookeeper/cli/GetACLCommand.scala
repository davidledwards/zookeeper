package com.loopfor.zookeeper.cli

import com.loopfor.zookeeper._

object GetACLCommand {
  import Command._

  val Usage = """usage: getacl [OPTIONS] [PATH...]

  Gets the ACL for the node specified by each PATH. PATH may be omitted, in
  which case the current working path is assumed.

options:
"""

  def apply(zk: Zookeeper) = new Command {
    implicit private val _zk = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = parse(args)
      val paths = opts('params).asInstanceOf[Seq[String]]
      val count = paths.size
      (1 /: paths) { case (i, path) =>
        val node = Node(context resolve path)
        try {
          val (acl, status) = node.getACL()
          if (count > 1) println(node.path + ":")
          acl foreach { println _ }
          if (count > 1 && i < count) println()
        } catch {
          case _: NoNodeException => println(node.path + ": no such node")
        }
        i + 1
      }
      context
    }
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
