package com.loopfor.zookeeper.cli

import com.loopfor.zookeeper._
import java.nio.charset.Charset
import scala.annotation.tailrec
import scala.language._

object GetCommand {
  import Command._

  val Usage = """usage: get [OPTIONS] [PATH...]

  Gets the data for the node specified by each PATH. PATH may be omitted, in
  which case the current working path is assumed.

  By default, data is displayed in a hex/ASCII table with offsets, though the
  output format can be changed using --string or --binary. If --string is
  chosen, it may be necessary to also specify the character encoding if the
  default of `UTF-8` is incorrect. The CHARSET in --encoding refers to any of
  the possible character sets installed on the underlying JRE.

options:
  --hex, -h                  : display data as hex/ASCII (default)
  --string, -s               : display data as string (see --encoding)
  --binary, -b               : display data as binary
  --encoding, -e CHARSET     : charset for use with --string (default=UTF-8)
"""

  private val UTF_8 = Charset forName "UTF-8"

  private type DisplayFunction = (Array[Byte], Map[Symbol, Any]) => Unit

  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = parse(args)
      val display = opts('display).asInstanceOf[DisplayFunction]
      val paths = opts('params).asInstanceOf[Seq[String]]
      val count = paths.size
      (1 /: paths) { case (i, path) =>
        val node = Node(context resolve path)
        try {
          val (data, status) = node.get()
          if (count > 1) println(node.path + ":")
          display(data, opts)
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
    @tailrec def parse(args: Seq[String], opts: Map[Symbol, Any]): Map[Symbol, Any] = {
      if (args.isEmpty)
        opts
      else {
        val arg = args.head
        val rest = args.tail
        arg match {
          case "--" => opts + ('params -> rest)
          case LongOption("hex") | ShortOption("h") => parse(rest, opts + ('display -> displayHex _))
          case LongOption("string") | ShortOption("s") => parse(rest, opts + ('display -> displayString _))
          case LongOption("binary") | ShortOption("b") => parse(rest, opts + ('display -> displayBinary _))
          case LongOption("encoding") | ShortOption("e") => rest.headOption match {
            case Some(charset) =>
              val cs = try Charset forName charset catch {
                case _: IllegalArgumentException => error(charset + ": no such charset")
              }
              parse(rest.tail, opts + ('encoding -> cs))
            case _ => error(arg + ": missing argument")
          }
          case LongOption(_) | ShortOption(_) => error(arg + ": no such option")
          case _ => opts + ('params -> args)
        }
      }
    }
    parse(args, Map(
          'display -> displayHex _,
          'encoding -> UTF_8,
          'params -> Seq("")))
  }

  private def displayHex(data: Array[Byte], opts: Map[Symbol, Any]) {
    @tailrec def display(n: Int) {
      def charOf(b: Byte) = if (b >= 32 && b < 127) b.asInstanceOf[Char] else '.'

      def pad(n: Int) = Array.fill(n)(' ') mkString

      if (n < data.length) {
        val l = Math.min(n + 16, data.length) - n
        print("%08x  " format n)
        print((for (i <- n until (n + l)) yield "%02x " format data(i)).mkString)
        print(pad((16 - l) * 3))
        print(" |")
        print((for (i <- n until (n + l)) yield charOf(data(i))).mkString)
        print(pad(16 - l))
        println("|")
        display(n + l)
      }
    }
    display(0)
  }

  private def displayString(data: Array[Byte], opts: Map[Symbol, Any]) = {
    val cs = opts('encoding).asInstanceOf[Charset]
    println(new String(data, cs))
  }

  private def displayBinary(data: Array[Byte], opts: Map[Symbol, Any]) =
    Console.out.write(data, 0, data.length)
}
