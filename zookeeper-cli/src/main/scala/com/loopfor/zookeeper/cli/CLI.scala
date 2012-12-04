package com.loopfor.zookeeper.cli

import scala.language._
import jline.console.ConsoleReader
import jline.console.completer.StringsCompleter
import scala.collection.JavaConverters._
import scala.annotation.tailrec
import java.net.InetSocketAddress
import com.loopfor.zookeeper.Configuration
import scala.concurrent.duration._
import com.loopfor.zookeeper.Zookeeper
import com.loopfor.zookeeper.Node
import com.loopfor.zookeeper.Path
import com.loopfor.zookeeper._
import java.text.DateFormat
import java.util.Date

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

  private val USAGE = """
usage: zk [OPTIONS] SERVER...
       zk [-? | --help]

  An interactive client capable of connecting to a ZooKeeper cluster. At least
  one SERVER in the cluster must be specified, which is defined as `host:port`.

options:
  --path, -p                 : root path (/=default)
  --timeout, -t SECONDS      : session timeout (0=default)
  --readonly, -r             : allow readonly connection
"""

  private val COMMANDS = Seq("ls", "quit")

  private def run(args: Array[String]): Int = {
    import Console.err
    if (args.size == 0) {
      println(USAGE)
      return 1
    }
    val opts = try {
      parseOptions(args)
    } catch {
      case e: OptionException =>
        err.println(e.getMessage)
        return 1
      case e: Exception =>
        err.println("internal error: " + e.getMessage)
        err.println(">> stack trace")
        e.printStackTrace(err)
        return 1
    }
    if (opts contains 'help) {
      println(USAGE)
      return 0
    }
    val servers = opts get 'params match {
      case Some(params) =>
        params.asInstanceOf[Seq[String]] map { p =>
          val i = p indexOf ':'
          if (i == -1) {
            println(p + ": missing port; expecting `host:port`")
            return 1
          } else if (i == 0) {
            println(p + ": missing host; expecting `host:port`")
            return 1
          } else {
            new InetSocketAddress(p take i, try {
              (p drop i + 1).toInt
            } catch {
              case _: NumberFormatException =>
                println(p + ": port invalid; expecting `host:port`")
                return 1
            })
          }
        }
      case _ =>
        println("no servers specified")
        return 1
    }
    val path = opts get 'path match {
      case Some(p) => p.asInstanceOf[String]
      case _ => ""
    }
    val timeout = opts get 'timeout match {
      case Some(p) => p.asInstanceOf[Int] seconds
      case _ => 60 seconds
    }
    val readonly = opts get 'readonly match {
      case Some(p) => p.asInstanceOf[Boolean]
      case _ => false
    }

    // todo: add watcher to config so we can change cursor when client state changes
    val config = Configuration(servers) withPath(path) withTimeout(timeout) withAllowReadOnly(readonly)
    val zk = Zookeeper(config)

    val commands = Map[String, Command](
          "cd" -> CdCommand(zk),
          "pwd" -> PwdCommand(zk),
          "ls" -> LsCommand(zk),
          "stat" -> StatCommand(zk),
          "get" -> GetCommand(zk),
          "quit" -> QuitCommand(zk)) withDefaultValue UnrecognizedCommand(zk)

    val reader = new ConsoleReader
    reader.setBellEnabled(false)
    reader.addCompleter(new StringsCompleter(commands.keySet.asJava))

    @tailrec def process(context: Path) {
      if (context != null) {
        val args = readCommand(reader)
        val _context = if (args.size > 0) commands(args.head)(args.head, args.tail, context) else context
        process(_context)
      }
    }

    process(Path("/"))
    0
  }

  private def readCommand(reader: ConsoleReader): Array[String] = {
    val line = reader.readLine("zk> ")
    val args = line split ' '
    args.headOption match {
      case Some(a) => if (a == "") args.tail else args
      case _ => args
    }
  }

  private class OptionException(message: String, cause: Throwable) extends Exception(message, cause) {
    def this(message: String) = this(message, null)
  }

  private def parseOptions(args: Array[String]): Map[Symbol, Any] = {
    @tailrec def parse(args: Stream[String], opts: Map[Symbol, Any]): Map[Symbol, Any] = {
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
              val _seconds = try {
                seconds.toInt
              } catch {
                case e: NumberFormatException => throw new OptionException(seconds + ": SECONDS must be an integer")
              }
              parse(rest.tail, opts + ('timeout -> _seconds))
            case _ => throw new OptionException(arg + ": missing argument")
          }
          case LongOption("readonly") | ShortOption("r") => parse(rest, opts + ('readonly -> true))
          case LongOption(_) | ShortOption(_) => throw new OptionException(arg + ": unrecognized option")
          case _ => opts + ('params -> args.toList)
        }
      }
    }

    object LongOption {
      def unapply(arg: String): Option[String] = if (arg startsWith "--") Some(arg drop 2) else None
    }

    object ShortOption {
      def unapply(arg: String): Option[String] = if (arg startsWith "-") Some(arg drop 1) else None
    }

    parse(args.toStream, Map())
  }
}

trait Command extends ((String, Seq[String], Path) => Path)

private object LsCommand {
  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val paths = if (args.isEmpty) args :+ "" else args
      val count = paths.size
      (1 /: paths) { case (i, path) =>
        val node = Node(context resolve path)
        try {
          val children = node.children()
          if (count > 1)
            println(node.path + ":")
          children foreach { child => println(child.path.parts.last) }
          if (count > 1 && i < count)
            println()
        } catch {
          case _: NoNodeException => println(node.path + ": no such node")
        }
        i + 1
      }
      context
    }
  }
}

private object CdCommand {
  def apply(zk: Zookeeper) = new Command {
    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      if (args.isEmpty) Path("/") else context.resolve(args.head).normalize
    }
  }
}

private object PwdCommand {
  def apply(zk: Zookeeper) = new Command {
    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      println(context.path)
      context
    }
  }
}

private object StatCommand {
  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk
    private val formatter = DateFormat.getDateTimeInstance

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val paths = if (args.isEmpty) args :+ "" else args
      val count = paths.size
      (1 /: paths) { case (i, path) =>
        val node = Node(context resolve path)
        node.exists() match {
          case Some(status) =>
            if (count > 1)
              println(node.path + ":")
            println("czxid: " + status.czxid)
            println("mzxid: " + status.mzxid)
            println("pzxid: " + status.pzxid)
            println("ctime: " + status.ctime + " (" + formatter.format(new Date(status.ctime)) + ")")
            println("mtime: " + status.mtime + " (" + formatter.format(new Date(status.mtime)) + ")")
            println("version: " + status.version)
            println("cversion: " + status.cversion)
            println("aversion: " + status.aversion)
            println("owner: " + status.ephemeralOwner)
            println("datalen: " + status.dataLength)
            println("children: " + status.numChildren)
            if (count > 1 && i < count)
              println()
          case _ => println(node.path + ": no such node")
        }
        i + 1
      }
      context
    }
  }
}

private object GetCommand {
  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val paths = if (args.isEmpty) args :+ "" else args
      val count = paths.size
      (1 /: paths) { case (i, path) =>
        val node = Node(context resolve path)
        try {
          val (data, status) = node.get()
          display(data)
        } catch {
          case _: NoNodeException => println(node.path + ": no such node")
        }
        i + 1
      }
      context
    }

    private def display(data: Array[Byte]) {
      @tailrec def display(n: Int) {
        import Math.min
        if (n < data.length) {
          val l = min(n + 16, data.length) - n
          print("%08x  " format n)
          print((for (i <- n until (n + l)) yield "%02x " format data(i)).mkString)
          print((for (i <- l until 16) yield "   ").mkString)
          print(" |")
          print((for (i <- n until (n + l)) yield ".").mkString)
          print((for (i <- l until 16) yield " ").mkString)
          println("|")
          display(n + l)
        }
      }
      display(0)
    }
  }
}

private object QuitCommand {
  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      zk.close()
      null
    }
  }
}

private object UnrecognizedCommand {
  def apply(zk: Zookeeper) = new Command {
    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      println(cmd + ": unrecognized command")
      context
    }
  }
}