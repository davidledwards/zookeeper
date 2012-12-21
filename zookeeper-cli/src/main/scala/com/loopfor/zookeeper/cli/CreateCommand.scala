package com.loopfor.zookeeper.cli

import com.loopfor.zookeeper._
import java.io.{FileInputStream, FileNotFoundException, IOException}
import java.nio.charset.Charset
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuilder

object CreateCommand {
  import Command._

  val Usage = """usage: mk|create [OPTIONS] PATH [DATA]

  Creates the node specified by PATH with optional DATA.

  DATA is optional, and if omitted, creates the node without any attached data.
  If DATA does not begin with `@`, it is assumed to be a Unicode string, which
  by default, is encoded as UTF-8 at time of storage. The --encoding option is
  used to provide an alternative CHARSET, which may be any of the possible
  character sets installed on the underlying JRE.

  If DATA is prefixed with `@`, this indicates that the remainder of the
  argument is a filename and whose contents will be attached to the node when
  created.

  The parent node of PATH must exist and must not be ephemeral. The --recursive
  option can be used to create intermediate nodes, though the first existing
  node in PATH must not be ephemeral.

  One or more optional ACL entries may be specified with --acl, which must
  conform to the following syntax: <scheme>:<id>=[rwcda*], where both <scheme>
  and <id> are optional and any of [rwcda*] characters may be given as
  permissions. The permission values are (r)ead, (w)rite, (c)reate, (d)elete,
  (a)dmin and all(*).

options:
  --recursive, -r            : recursively create intermediate nodes
  --encoding, -e CHARSET     : charset used for encoding DATA (default=UTF-8)
  --sequential, -S           : appends sequence to node name
  --ephemeral, -E            : node automatically deleted when CLI exits
  --acl, -A                  : ACL assigned to node (default=world:anyone=*)
"""

  private val UTF_8 = Charset forName "UTF-8"

  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = parse(args)
      val recurse = opts('recursive).asInstanceOf[Boolean]
      val disp = disposition(opts)
      val acl = opts('acl).asInstanceOf[Seq[ACL]] match {
        case Seq() => ACL.AnyoneAll
        case a => a
      }
      val params = opts('params).asInstanceOf[Seq[String]]
      val path = if (params.isEmpty) error("path must be specified") else params.head
      val data = params.tail.headOption match {
        case Some(d) => d.headOption match {
          case Some('@') =>
            val name = d drop 1
            val file = try new FileInputStream(name) catch {
              case _: FileNotFoundException => error(name + ": file not found")
              case _: SecurityException => error(name + ": access denied")
            }
            try read(file) catch {
              case e: IOException => error(name + ": I/O error: " + e.getMessage)
            } finally
              file.close()
          case _ => d getBytes opts('encoding).asInstanceOf[Charset]
        }
        case _ => Array[Byte]()
      }
      val node = Node(context resolve path)
      try {
        if (recurse) {
          (Path("/") /: node.path.parts.tail.dropRight(1)) { case (parent, part) =>
            val node = Node(parent resolve part)
            try node.create(Array(), ACL.AnyoneAll, Persistent) catch {
              case _: NodeExistsException =>
            }
            node.path
          }
        }
        node.create(data, acl, disp)
      } catch {
        case e: NodeExistsException => error(Path(e.getPath).normalize + ": node already exists")
        case _: NoNodeException => error(node.parent.path + ": no such parent node")
        case e: NoChildrenForEphemeralsException => error(Path(e.getPath).normalize + ": parent node is ephemeral")
        case _: InvalidACLException => error(acl.mkString(",") + ": invalid ACL")
      }
      context
    }
  }

  private def read(file: FileInputStream): Array[Byte] = {
    @tailrec def read(buffer: ArrayBuilder[Byte]): Array[Byte] = {
      val c = file.read()
      if (c == -1) buffer.result else read(buffer += c.toByte)
    }
    read(ArrayBuilder.make[Byte])
  }

  private def disposition(opts: Map[Symbol, Any]): Disposition = {
    val sequential = opts('sequential).asInstanceOf[Boolean]
    val ephemeral = opts('ephemeral).asInstanceOf[Boolean]
    if (sequential && ephemeral) EphemeralSequential
    else if (sequential) PersistentSequential
    else if (ephemeral) Ephemeral
    else Persistent
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
          case LongOption("encoding") | ShortOption("e") => rest.headOption match {
            case Some(charset) =>
              val cs = try Charset forName charset catch {
                case _: IllegalArgumentException => error(charset + ": no such charset")
              }
              parse(rest.tail, opts + ('encoding -> cs))
            case _ => error(arg + ": missing argument")
          }
          case LongOption("sequential") | ShortOption("S") => parse(rest, opts + ('sequential -> true))
          case LongOption("ephemeral") | ShortOption("E") => parse(rest, opts + ('ephemeral -> true))
          case LongOption("acl") | ShortOption("A") => rest.headOption match {
            case Some(acl) =>
              val _acl = ACL parse acl match {
                case Some(a) => a
                case _ => error(acl + ": invalid ACL syntax")
              }
              val acls = opts('acl).asInstanceOf[Seq[ACL]] :+ _acl
              parse(rest.tail, opts + ('acl -> acls))
            case _ => error(arg + ": missing argument")
          }
          case LongOption(_) | ShortOption(_) => error(arg + ": no such option")
          case _ => opts + ('params -> args)
        }
      }
    }
    parse(args, Map(
          'recursive -> false,
          'encoding -> UTF_8,
          'sequential -> false,
          'ephemeral -> false,
          'acl -> Seq[ACL](),
          'params -> Seq[String]()))
  }
}
