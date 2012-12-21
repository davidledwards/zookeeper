package com.loopfor.zookeeper.cli

import com.loopfor.zookeeper._
import java.io.{FileInputStream, FileNotFoundException, IOException}
import java.nio.charset.Charset
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuilder

object SetCommand {
  import Command._

  val Usage = """usage: set [OPTIONS] PATH [DATA]

  Sets the DATA for the node specified by PATH.

  DATA is optional, and if omitted, associates an empty byte array with the
  node. If DATA does not begin with `@`, it is assumed to be a Unicode string,
  which by default, is encoded as UTF-8 at time of storage. The --encoding
  option is used to provide an alternative CHARSET, which may be any of the
  possible character sets installed on the underlying JRE.

  If DATA is prefixed with `@`, this indicates that the remainder of the
  argument is a filename and whose contents will be attached to the node.

options:
  --encoding, -e CHARSET     : charset used for encoding DATA (default=UTF-8)
  --version, -v VERSION      : version required to match in ZooKeeper
  --force, -f                : forcefully set DATA regardless of version
"""

  private val UTF_8 = Charset forName "UTF-8"

  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = parse(args)
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
      try node.set(data, version) catch {
        case _: NoNodeException => error(node.path + ": no such node")
        case _: BadVersionException => error(version.get + ": version does not match")
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

  private def parse(args: Seq[String]): Map[Symbol, Any] = {
    @tailrec def parse(args: Seq[String], opts: Map[Symbol, Any]): Map[Symbol, Any] = {
      if (args.isEmpty)
        opts
      else {
        val arg = args.head
        val rest = args.tail
        arg match {
          case "--" => opts + ('params -> rest)
          case LongOption("encoding") | ShortOption("e") => rest.headOption match {
            case Some(charset) =>
              val cs = try Charset forName charset catch {
                case _: IllegalArgumentException => error(charset + ": no such charset")
              }
              parse(rest.tail, opts + ('encoding -> cs))
            case _ => error(arg + ": missing argument")
          }
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
          'encoding -> UTF_8,
          'force -> false,
          'version -> None,
          'params -> Seq[String]()))
  }
}
