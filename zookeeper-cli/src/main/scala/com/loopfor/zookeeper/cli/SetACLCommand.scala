package com.loopfor.zookeeper.cli

import com.loopfor.zookeeper._
import scala.annotation.tailrec

object SetACLCommand {
  import Command._

  val Usage = """usage: setacl [OPTIONS] PATH ACL[...]

  Sets the ACL for the node specified by PATH.

  At least one ACL entry must be provided, which must conform to the following
  syntax: <scheme>:<id>=[rwcda*], where both <scheme> and <id> are optional and
  any of [rwcda*] characters may be given as permissions. The permission values
  are (r)ead, (w)rite, (c)reate, (d)elete, (a)dmin and all(*).

  Unless otherwise specified, --set is assumed, which means that the given ACL
  replaces the current ACL associated with the node at PATH. Both --add
  and --remove options first query the current ACL before applying the
  respective operation. Therefore, the entire operation is not atomic, though
  specifying --version ensures that no intervening operations have changed the
  state.

options:
  --add, -a                  : adds ACL to existing list
  --remove, -r               : removes ACL from existing list
  --set, -s                  : replaces existing list with ACL (default)
  --version, -v VERSION      : version required to match in ZooKeeper
  --force, -f                : forcefully set ACL regardless of version
"""

  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = parse(args)
      val action = opts('action).asInstanceOf[Symbol]
      val version = {
        val force = opts('force).asInstanceOf[Boolean]
        if (force) None
        else opts('version).asInstanceOf[Option[Int]] match {
          case None => error("version must be specified; otherwise use --force")
          case v => v
        }
      }
      val params = opts('params).asInstanceOf[Seq[String]]
      val path = if (params.isEmpty) error("path must be specified") else params.head
      val acl = params.tail match {
        case Seq() => error("ACL must be specified")
        case acls => acls map { acl =>
          ACL parse acl match {
            case Some(a) => a
            case _ => error(acl + ": invalid ACL syntax")
          }
        }
      }
      val node = Node(context resolve path)
      val (curACL, _) = try node.getACL() catch {
        case _: NoNodeException => error(node.path + ": no such node")
      }
      val newACL = action match {
        case 'add => (toMap(curACL) /: acl) { case (c, a) => c + (a.id -> a) }.values.toSeq
        case 'remove => (toMap(curACL) /: acl) { case (c, a) => c - a.id }.values.toSeq
        case 'set => acl
      }
      if (newACL.isEmpty) error("new ACL would be empty")
      try node.setACL(newACL, version) catch {
        case _: NoNodeException => error(node.path + ": no such node")
        case _: BadVersionException => error(version.get + ": version does not match")
        case _: InvalidACLException => error(newACL.mkString(",") + ": invalid ACL")
      }
      context
    }

    private def toMap(acl: Seq[ACL]): Map[Id, ACL] =
      (Map[Id, ACL]() /: acl) { case (m, a) => m + (a.id -> a) }
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
          case LongOption("add") | ShortOption("a") => parse(rest, opts + ('action -> 'add))
          case LongOption("remove") | ShortOption("r") => parse(rest, opts + ('action -> 'remove))
          case LongOption("set") | ShortOption("s") => parse(rest, opts + ('action -> 'set))
          case LongOption("force") | ShortOption("f") => parse(rest, opts + ('force -> true))
          case LongOption("version") | ShortOption("v") => rest.headOption match {
            case Some(version) =>
              val _version = try version.toInt catch {
                case _: NumberFormatException => error(version + ": version must be an integer")
              }
              parse(rest.tail, opts + ('version -> Some(_version)))
            case _ => error(arg + ": missing argument")
          }
          case LongOption(_) | ShortOption(_) => error(arg + ": no such option")
          case _ => opts + ('params -> args)
        }
      }
    }
    parse(args, Map(
          'action -> 'set,
          'force -> false,
          'version -> None,
          'params -> Seq[String]()))
  }
}
