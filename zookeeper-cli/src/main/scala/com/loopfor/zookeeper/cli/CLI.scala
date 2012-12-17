package com.loopfor.zookeeper.cli

import com.loopfor.zookeeper._
import java.io.{FileInputStream, FileNotFoundException, IOException}
import java.net.InetSocketAddress
import java.nio.charset.{Charset, UnsupportedCharsetException}
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicReference
import jline.console.ConsoleReader
import jline.console.completer.{Completer, ArgumentCompleter, StringsCompleter}
import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuilder
import scala.concurrent.duration._
import scala.language._

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

options:
  --path, -p                 : root path (default=/)
  --timeout, -t SECONDS      : session timeout (default=0)
  --readonly, -r             : allow readonly connection
"""

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
          'params -> Seq("")))
  }
}

private object Reader {
  def apply(commands: Set[String], zk: Zookeeper) = new (Path => Seq[String]) {
    private val reader = new ConsoleReader
    reader.setBellEnabled(false)
    reader.setHistoryEnabled(true)
    reader.setPrompt("zk> ")

    private val delimiter = new ArgumentCompleter.WhitespaceArgumentDelimiter()
    private val first = new StringsCompleter(commands.asJava)

    def apply(context: Path): Seq[String] = {
      // Completer is added and removed with each invocation since completion is relative to the path context and the
      // context may change with each subsequent command. Keeping the same reader for the duration of the user session
      // is necessary to retain command history, otherwise ^p/^n operations have no effect.
      val completer = new ArgumentCompleter(delimiter, first, new PathCompleter(zk, context))
      completer.setStrict(false)
      reader.addCompleter(completer)
      try {
        val line = reader.readLine()
        val args = if (line == null) Array("quit") else line split ' '
        args.headOption match {
          case Some(a) => if (a == "") args.tail else args
          case _ => args
        }
      } finally {
        reader.removeCompleter(completer)
      }
    }
  }

  private class PathCompleter(zk: Zookeeper, context: Path) extends Completer {
    private implicit val _zk = zk

    def complete(buffer: String, cursor: Int, candidates: java.util.List[CharSequence]): Int = {
      val (node, prefix) = if (buffer == null)
        (Node(context), "")
      else {
        val path = context resolve buffer
        if (buffer endsWith "/") (Node(path), "")
        else (Node(path.parentOption match {
          case Some(p) => p
          case _ => path
        }), path.name)
      }
      if (prefix == "." || prefix == "..") {
        candidates add "/"
        buffer.size
      } else {
        try {
          val results = node.children() filter { _.name startsWith prefix }
          if (results.size == 1 && results.head.name == prefix)
            candidates add results.head.name + "/"
          else
            results sortBy { _.name } foreach { candidates add _.name }
          if (buffer == null) 0 else (buffer lastIndexOf '/') + 1
        } catch {
          case _: KeeperException => return -1
        }
      }
    }
  }
}

private trait Command extends ((String, Seq[String], Path) => Path)

private class CommandException(message: String) extends Exception(message)

private object Command {
  def error(message: String): Nothing = throw new CommandException(message)
}

private object ConfigCommand {
  val Usage = """usage: config

  Shows connection information and session state.

  Possible session states, which indicate connectedness to the ZooKeeper
  cluster, include:
    * Disconnected
    * Connected
    * ConnectedReadOnly
    * AuthenticationFailed
    * Authenticated
    * Expired

  In general, session state may change between connected and disconnected
  because of temporary loss of connectivity, but once expired, the CLI must
  be stopped before a new session can be established.
"""

  def apply(config: Configuration, state: AtomicReference[StateEvent]) = new Command {
    def apply(cmd: String, args: Seq[String], context: Path) = {
      println("servers: " + (config.servers map { s => s.getHostName + ":" + s.getPort } mkString ","))
      println("path: " + Path("/").resolve(config.path).normalize)
      println("timeout: " + config.timeout)
      println("session: " + state.get)
      context
    }
  }
}

private object ListCommand {
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

private object CdCommand {
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

private object PwdCommand {
  import Command._

  val Usage = """usage: pwd [OPTIONS]

  Shows the current working path.

options:
  --check, -c                : check existence of node at working path
"""

  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = parse(args)
      val check = opts('check).asInstanceOf[Boolean]
      print(context)
      if (check && Node(context).exists().isEmpty) print(": does not exist")
      println()
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
          case LongOption("check") | ShortOption("c") => parse(rest, opts + ('check -> true))
          case LongOption(_) | ShortOption(_) => error(arg + ": no such option")
          case _ => opts + ('params -> args)
        }
      }
    }
    parse(args, Map(
          'check -> false))
  }
}

private object StatCommand {
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

private object GetCommand {
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
                case _: UnsupportedCharsetException => error(charset + ": no such charset")
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

private object SetCommand {
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
                case _: UnsupportedCharsetException => error(charset + ": no such charset")
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

private object GetACLCommand {
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

private object SetACLCommand {
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

private object CreateCommand {
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
                case _: UnsupportedCharsetException => error(charset + ": no such charset")
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

private object DeleteCommand {
  import Command._

  val Usage = """usage: rm|del [OPTIONS] PATH

  Deletes the node specified by PATH.

  The version of the node must be provided and match the version in ZooKeeper,
  otherwise the operation will fail. Alternatively, --force can be used to
  ensure deletion of the node without specifying a version.

options:
  --recursive, -r            : recursively deletes nodes under PATH
                               (implies --force on child nodes)
  --version, -v VERSION      : version required to match in ZooKeeper
  --force, -f                : forcefully delete node regardless of version
"""

  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = parse(args)
      val recurse = opts('recursive).asInstanceOf[Boolean]
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
      val node = Node(context resolve path)
      try {
        node.delete(version)
      } catch {
        case _: NoNodeException => error(node.path + ": no such node")
        case _: BadVersionException => error(version.get + ": version does not match")
        case _: NotEmptyException => error(node.path + ": node has children")
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
          case LongOption("recursive") | ShortOption("r") => parse(rest, opts + ('recursive -> true))
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
          'recursive -> false,
          'force -> false,
          'version -> None,
          'params -> Seq[String]()))
  }
}

private object QuitCommand {
  val Usage = """usage: exit|quit

  Exits the CLI.
"""

  def apply(zk: Zookeeper) = new Command {
    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      zk.close()
      null
    }
  }
}

private object HelpCommand {
  val Usage = """Type `help COMMAND` for more information.

  The TAB key can be used to auto-complete commands and node paths. Pressing
  TAB on the first argument will try to find commands matching the prefix,
  whereas, all subsequent TAB depressions will attempt to match existing nodes
  relative to the current working path.

  In all cases requiring paths, both absolute and relative forms may be given.
  Absolute paths start with `/`, which means that the current working path is
  ignored. Relative paths, on the other hand, are resolved in the context of
  the current working path. In addition, both `.` and `..` may be used in paths
  indicating the current and parent node, respectively.

commands:
  ls, dir        list nodes
  cd             change working path
  pwd            show working path
  get            get node data
  set            set node data
  stat, info     get node status
  mk, create     create new node
  rm, del        delete existing node
  getacl         get node ACL
  setacl         set node ACL
  config         show connection information and session state
  help, ?        show available commands
  exit, quit     exit program
"""

  private val Commands = Map(
        "ls" -> ListCommand.Usage,
        "dir" -> ListCommand.Usage,
        "cd" -> CdCommand.Usage,
        "pwd" -> PwdCommand.Usage,
        "get" -> GetCommand.Usage,
        "set" -> SetCommand.Usage,
        "stat" -> StatCommand.Usage,
        "info" -> StatCommand.Usage,
        "mk" -> CreateCommand.Usage,
        "create" -> CreateCommand.Usage,
        "rm" -> DeleteCommand.Usage,
        "del" -> DeleteCommand.Usage,
        "getacl" -> GetACLCommand.Usage,
        "setacl" -> SetACLCommand.Usage,
        "config" -> ConfigCommand.Usage,
        "help" -> HelpCommand.Usage,
        "exit" -> QuitCommand.Usage,
        "quit" -> QuitCommand.Usage
        )

  def apply() = new Command {
    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      if (args.isEmpty)
        println(Usage)
      else {
        val cmd = args.head
        Commands get cmd match {
          case Some(usage) => println(usage)
          case _ => println(cmd + ": no such command")
        }
      }
      context
    }
  }
}

private object LongOption {
  def unapply(arg: String): Option[String] = if (arg startsWith "--") Some(arg drop 2) else None
}

private object ShortOption {
  def unapply(arg: String): Option[String] = if (arg startsWith "-") Some(arg drop 1) else None
}

private class OptionException(message: String, cause: Throwable) extends Exception(message, cause) {
  def this(message: String) = this(message, null)
}
