package com.loopfor.zookeeper.cli

import com.loopfor.zookeeper._
import java.io.{BufferedReader, FileInputStream, FileNotFoundException, IOException, InputStream, InputStreamReader}
import java.net.InetSocketAddress
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.language._
import scala.collection.mutable.ArrayBuffer

object CLI {
  def main(args: Array[String]) {
    try {
      val status = run(args)
      sys.exit(status)
    } catch {
      case e: Exception =>
        import Console.err
        err.println("internal error: " + e.getMessage)
        err.println(">> stack trace")
        e.printStackTrace(err)
        sys.exit(1)
    }
  }

  private val Usage = """usage: zk [OPTIONS] SERVER[...]
       zk [-? | --help]

  An interactive client capable of connecting to a ZooKeeper cluster. At least
  one SERVER in the cluster must be specified, which is defined as `host:port`.

configuration options:
  --path, -p                 : root path (default=/)
  --timeout, -t SECONDS      : session timeout (default=60)
  --readonly, -r             : allow readonly connection

command options:
  --command, -c COMMAND      : execute COMMAND
  --file, -f FILE            : execute commands in FILE
  --encoding, -e CHARSET     : charset applicable to FILE (default=UTF-8)
"""

  private val UTF_8 = Charset forName "UTF-8"

  private def run(args: Array[String]): Int = {
    import Console.err
    if (args.size == 0) {
      println(Usage)
      return 1
    }
    val opts = try parse(args) catch {
      case e: OptionException =>
        err.println(e.getMessage)
        return 1
      case e: Exception =>
        err.println("internal error: " + e.getMessage)
        err.println(">> stack trace")
        e.printStackTrace(err)
        return 1
    }
    if (opts('help).asInstanceOf[Boolean]) {
      println(Usage)
      return 0
    }
    val params = opts('params).asInstanceOf[Seq[String]]
    if (params.isEmpty) {
      println("no servers specified")
      return 1
    }
    val servers = params map { server =>
      val i = server indexOf ':'
      if (i == -1) {
        println(server + ": missing port; expecting `host:port`")
        return 1
      } else if (i == 0) {
        println(server + ": missing host; expecting `host:port`")
        return 1
      } else {
        new InetSocketAddress(server take i, try {
          (server drop i + 1).toInt
        } catch {
          case _: NumberFormatException =>
            println(server + ": port invalid; expecting `host:port`")
            return 1
        })
      }
    }
    val path = opts('path).asInstanceOf[String]
    val timeout = opts('timeout).asInstanceOf[Int] seconds
    val readonly = opts('readonly).asInstanceOf[Boolean]

    def read(file: InputStream, cs: Charset): Seq[String] = {
      val f = new BufferedReader(new InputStreamReader(file, cs))
      @tailrec def read(cmds: ArrayBuffer[String]): Seq[String] = {
        val line = f.readLine()
        if (line == null) cmds.toSeq else read(cmds += line)
      }
      read(ArrayBuffer.empty)
    }

    val cmds = {
      opts('file).asInstanceOf[Option[String]] match {
        case Some(name) =>
          val file = try new FileInputStream(name) catch {
            case _: FileNotFoundException =>
              println(name + ": file not found")
              return 1
            case _: SecurityException =>
              println(name + ": access denied")
              return 1
          }
          try read(file, opts('encoding).asInstanceOf[Charset]) catch {
            case e: IOException =>
              println(name + ": I/O error: " + e.getMessage)
              return 1
          } finally file.close()
        case _ => opts('command).asInstanceOf[Option[String]] match {
          case Some(cmd) => Seq(cmd)
          case _ => null
        }
      }
    }

    val state = new AtomicReference[StateEvent](Disconnected)
    val config = Configuration(servers) withPath(path) withTimeout(timeout) withAllowReadOnly(readonly) withWatcher {
      (event, session) => state set event
    }
    val zk = try Zookeeper(config) catch {
      case e: IOException =>
        println("I/O error: " + e.getMessage)
        return 1
    }

    val commands = Map[String, Command](
          "config" -> ConfigCommand(config, state),
          "cd" -> CdCommand(zk),
          "pwd" -> PwdCommand(zk),
          "ls" -> ListCommand(zk),
          "dir" -> ListCommand(zk),
          "stat" -> StatCommand(zk),
          "info" -> StatCommand(zk),
          "get" -> GetCommand(zk),
          "set" -> SetCommand(zk),
          "getacl" -> GetACLCommand(zk),
          "setacl" -> SetACLCommand(zk),
          "mk" -> CreateCommand(zk),
          "create" -> CreateCommand(zk),
          "rm" -> DeleteCommand(zk),
          "del" -> DeleteCommand(zk),
          "quit" -> QuitCommand(zk),
          "exit" -> QuitCommand(zk),
          "help" -> HelpCommand(),
          "?" -> HelpCommand()) withDefaultValue new Command {
      def apply(cmd: String, args: Seq[String], context: Path): Path = {
        println(cmd + ": no such command")
        context
      }
    }

    cmds match {
      case null =>
        val reader = Reader(commands.keySet, zk)

        @tailrec def process(context: Path) {
          if (context != null) {
            val args = reader(context)
            val _context = try {
              if (args.size > 0) commands(args.head)(args.head, args.tail, context) else context
            } catch {
              case e: CommandException =>
                println(e.getMessage)
                context
              case _: ConnectionLossException =>
                println("connection lost")
                context
              case _: SessionExpiredException =>
                println("session has expired; `exit` and restart CLI")
                context
              case _: NoAuthException =>
                println("not authorized")
                context
              case e: KeeperException =>
                println("internal zookeeper error: " + e.getMessage)
                context
            }
            process(_context)
          }
        }
        process(Path("/"))
        0
      case cmds =>
        (Path("/") /: cmds) { case (context, cmd) =>
          val args = Splitter split cmd
          try {
            if (args.size > 0) commands(args.head)(args.head, args.tail, context) else context
          } catch {
            case e: CommandException =>
              println(e.getMessage)
              return 1
            case _: ConnectionLossException =>
              println("connection lost")
              return 1
            case _: SessionExpiredException =>
              println("session has expired")
              return 1
            case e: KeeperException =>
              println("internal zookeeper error: " + e.getMessage)
              return 1
          }
        }
        0
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
          case "--" => opts + ('params -> rest.toList)
          case LongOption("help") | ShortOption("?") => parse(rest, opts + ('help -> true))
          case LongOption("path") | ShortOption("p") => rest.headOption match {
            case Some(path) => parse(rest.tail, opts + ('path -> path))
            case _ => throw new OptionException(arg + ": missing argument")
          }
          case LongOption("timeout") | ShortOption("t") => rest.headOption match {
            case Some(seconds) =>
              val _seconds = try seconds.toInt catch {
                case _: NumberFormatException => throw new OptionException(seconds + ": timeout must be an integer")
              }
              parse(rest.tail, opts + ('timeout -> _seconds))
            case _ => throw new OptionException(arg + ": missing argument")
          }
          case LongOption("readonly") | ShortOption("r") => parse(rest, opts + ('readonly -> true))
          case LongOption("command") | ShortOption("c") => rest.headOption match {
            case Some(command) => parse(rest.tail, opts + ('command -> Some(command)))
            case _ => throw new OptionException(arg + ": missing argument")
          }
          case LongOption("file") | ShortOption("f") => rest.headOption match {
            case Some(file) => parse(rest.tail, opts + ('file -> Some(file)))
            case _ => throw new OptionException(arg + ": missing argument")
          }
          case LongOption("encoding") | ShortOption("e") => rest.headOption match {
            case Some(charset) =>
              val cs = try Charset forName charset catch {
                case _: IllegalArgumentException => throw new OptionException(charset + ": no such charset")
              }
              parse(rest.tail, opts + ('encoding -> cs))
            case _ => throw new OptionException(arg + ": missing argument")
          }
          case LongOption(_) | ShortOption(_) => throw new OptionException(arg + ": no such option")
          case _ => opts + ('params -> args.toList)
        }
      }
    }
    parse(args, Map(
          'help -> false,
          'path -> "",
          'timeout -> 60,
          'readonly -> false,
          'command -> None,
          'file -> None,
          'encoding -> UTF_8,
          'params -> Seq[String]()))
  }
}
