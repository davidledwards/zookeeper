package com.loopfor.zookeeper.cli

import com.loopfor.zookeeper._
import java.io.{FileInputStream, FileNotFoundException, IOException}
import java.net.InetSocketAddress
import java.nio.charset.{Charset, UnsupportedCharsetException}
import java.text.DateFormat
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
          "get" -> GetCommand(zk),
          "getacl" -> GetACLCommand(zk),
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
          case _: ConnectionLossException =>
            println("connection lost")
            context
          case _: SessionExpiredException =>
            println("session has expired; `exit` and restart CLI")
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

object Reader {
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
  val Usage= """usage: ls|dir [OPTIONS] [PATH...]

  List child nodes for each PATH. PATH may be omitted, in which case the
  current working path is assumed.

options:
  --recursive, -r            : recursively list nodes
"""

  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = try parse(args) catch {
        case e: OptionException => println(e.getMessage)
        return context
      }
      val recurse = opts('recursive).asInstanceOf[Boolean]
      val paths = opts('params).asInstanceOf[Seq[String]]
      val count = paths.size
      (1 /: paths) { case (i, path) =>
        val node = Node(context resolve path)
        try {
          val children = node.children() sortBy { _.name }
          if (count > 1)
            println(node.path + ":")
          if (recurse)
            traverse(children, 0)
          else
            children foreach { child => println(child.name) }
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

  private def traverse(children: Seq[Node], depth: Int) {
    children foreach { child =>
      if (depth > 0)
        print(pad(depth) + "+ ")
      println(child.name)
      try {
        traverse(child.children() sortBy { _.name }, depth + 1)
      } catch {
        case _: NoNodeException => // ignore nodes that disappear during traversal
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
          case LongOption(_) | ShortOption(_) => throw new OptionException(arg + ": no such option")
          case _ => opts + ('params -> args)
        }
      }
    }
    parse(args, Map(
          'recursive -> false,
          'params -> Seq("")))
  }

  private def pad(depth: Int) = Array.fill((depth - 1) * 2)(' ') mkString
}

private object CdCommand {
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
      val opts = try parse(args) catch {
        case e: OptionException => println(e.getMessage)
        return context
      }
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
          case LongOption(_) | ShortOption(_) => throw new OptionException(arg + ": no such option")
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
  val Usage = """usage: pwd [OPTIONS]

  Shows the current working path.

options:
  --check, -c                : check existence of node at working path
"""

  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = try parse(args) catch {
        case e: OptionException => println(e.getMessage)
        return context
      }
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
          case LongOption(_) | ShortOption(_) => throw new OptionException(arg + ": no such option")
          case _ => opts + ('params -> args)
        }
      }
    }
    parse(args, Map(
          'check -> false))
  }
}

private object StatCommand {
  val Usage = """usage stat [OPTIONS] [PATH...]

  Gets the status for the node specified by each PATH. PATH may be omitted, in
  which case the current working path is assumed.

options:
"""

  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val paths = if (args.isEmpty) args :+ "" else args
      val count = paths.size
      (1 /: paths) { case (i, path) =>
        val node = Node(context resolve path)
        node.exists() match {
          case Some(status) =>
            if (count > 1)
              println(node.path + ":")
            println(StatusFormatter(status))
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

private object StatusFormatter {
  private val dateFormat = DateFormat.getDateTimeInstance

  def apply(status: Status): String = {
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
}

private object GetCommand {
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
  --all, -a                  : display node status
"""

  private val UTF_8 = Charset forName "UTF-8"

  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = try parse(args) catch {
        case e: OptionException => println(e.getMessage)
        return context
      }
      val format = opts('format).asInstanceOf[Symbol]
      val all = opts('all).asInstanceOf[Boolean]
      val paths = opts('params).asInstanceOf[Seq[String]]
      val count = paths.size
      (1 /: paths) { case (i, path) =>
        val node = Node(context resolve path)
        try {
          val (data, status) = node.get()
          if (count > 1)
            println(node.path + ":")
          if (all) {
            println(StatusFormatter(status))
            println()
          }
          format match {
            case 'hex => displayHex(data)
            case 'string => displayString(data, opts('encoding).asInstanceOf[Charset])
            case 'binary => displayBinary(data)
          }
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

  private def parse(args: Seq[String]): Map[Symbol, Any] = {
    @tailrec def parse(args: Seq[String], opts: Map[Symbol, Any]): Map[Symbol, Any] = {
      if (args.isEmpty)
        opts
      else {
        val arg = args.head
        val rest = args.tail
        arg match {
          case "--" => opts + ('params -> rest)
          case LongOption("hex") | ShortOption("h") => parse(rest, opts + ('format -> 'hex))
          case LongOption("string") | ShortOption("s") => parse(rest, opts + ('format -> 'string))
          case LongOption("binary") | ShortOption("b") => parse(rest, opts + ('format -> 'binary))
          case LongOption("encoding") | ShortOption("e") => rest.headOption match {
            case Some(charset) =>
              val cs = try Charset forName charset catch {
                case _: UnsupportedCharsetException => throw new OptionException(charset + ": no such charset")
              }
              parse(rest.tail, opts + ('encoding -> cs))
            case _ => throw new OptionException(arg + ": missing argument")
          }
          case LongOption("all") | ShortOption("a") => parse(rest, opts + ('all -> true))
          case LongOption(_) | ShortOption(_) => throw new OptionException(arg + ": no such option")
          case _ => opts + ('params -> args)
        }
      }
    }
    parse(args, Map(
          'format -> 'hex,
          'encoding -> UTF_8,
          'all -> false,
          'params -> Seq("")))
  }

  private def displayHex(data: Array[Byte]) {
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

  private def displayString(data: Array[Byte], cs: Charset) =
    println(new String(data, cs))

  private def displayBinary(data: Array[Byte]) =
    Console.out.write(data, 0, data.length)
}

private object GetACLCommand {
  val Usage = """usage: getacl [OPTIONS] [PATH...]

  Gets the ACL for the node specified by each PATH. PATH may be omitted, in
  which case the current working path is assumed.

options:
"""

  def apply(zk: Zookeeper) = new Command {
    implicit private val _zk = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val paths = if (args.isEmpty) args :+ "" else args
      val count = paths.size
      (1 /: paths) { case (i, path) =>
        val node = Node(context resolve path)
        try {
          val (acl, status) = node.getACL()
          if (count > 1)
            println(node.path + ":")
          display(acl)
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

  private def display(acl: Seq[ACL]) {
    acl foreach { a =>
      print(a.id.scheme + ":" + a.id.id + "=")
      val p = a.permission
      print(if ((p & ACL.Read) == 0) "-" else "r")
      print(if ((p & ACL.Write) == 0) "-" else "w")
      print(if ((p & ACL.Create) == 0) "-" else "c")
      print(if ((p & ACL.Delete) == 0) "-" else "d")
      print(if ((p & ACL.Admin) == 0) "-" else "a")
      println()
    }
  }
}

private object CreateCommand {
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

options:
  --recursive, -r            : recursively create intermediate nodes
  --encoding, -e CHARSET     : charset used for encoding DATA (default=UTF-8)
  --sequential, -S           : appends sequence to node name
  --ephemeral, -E            : node automatically deleted when CLI exits
"""

  private val UTF_8 = Charset forName "UTF-8"

  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = try parse(args) catch {
        case e: OptionException => println(e.getMessage)
        return context
      }
      val recurse = opts('recursive).asInstanceOf[Boolean]
      val disp = disposition(opts)
      val params = opts('params).asInstanceOf[Seq[String]]
      val path = if (params.isEmpty) {
        println("path must be specified")
        return context
      } else
        params.head
      val data = params.tail.headOption match {
        case Some(d) => d.headOption match {
          case Some('@') =>
            val name = d drop 1
            val file = try new FileInputStream(name) catch {
              case _: FileNotFoundException =>
                println(name + ": file not found")
                return context
              case _: SecurityException =>
                println(name + ": access denied")
                return context
            }
            try read(file) catch {
              case e: IOException =>
                println(name + ": I/O error: " + e.getMessage)
                return context
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
            try node.create(Array(), ACL.EveryoneAll, Persistent) catch {
              case _: NodeExistsException =>
            }
            node.path
          }
        }
        node.create(data, ACL.EveryoneAll, disp)
      } catch {
        case e: NodeExistsException => println(Path(e.getPath).normalize + ": node already exists")
        case _: NoNodeException => println(node.parent.path + ": no such parent node")
        case e: NoChildrenForEphemeralsException => println(Path(e.getPath).normalize + ": parent node is ephemeral")
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
                case _: UnsupportedCharsetException => throw new OptionException(charset + ": no such charset")
              }
              parse(rest.tail, opts + ('encoding -> cs))
            case _ => throw new OptionException(arg + ": missing argument")
          }
          case LongOption("sequential") | ShortOption("S") => parse(rest, opts + ('sequential -> true))
          case LongOption("ephemeral") | ShortOption("E") => parse(rest, opts + ('ephemeral -> true))
          case LongOption(_) | ShortOption(_) => throw new OptionException(arg + ": no such option")
          case _ => opts + ('params -> args)
        }
      }
    }
    parse(args, Map(
          'recursive -> false,
          'encoding -> UTF_8,
          'sequential -> false,
          'ephemeral -> false,
          'params -> Seq[String]()))
  }
}

private object DeleteCommand {
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
      val opts = try parse(args) catch {
        case e: OptionException => println(e.getMessage)
        return context
      }
      val recurse = opts('recursive).asInstanceOf[Boolean]
      val force = opts('force).asInstanceOf[Boolean]
      val version = opts('version).asInstanceOf[Option[Int]]
      val params = opts('params).asInstanceOf[Seq[String]]
      val path = if (params.isEmpty) {
        println("path must be specified")
        return context
      } else
        params.head
      if (!force && version.isEmpty) {
        println("version must be specified; otherwise use --force")
        return context
      }
      val node = Node(context resolve path)
      try {
        node.delete(if (force) None else version)
      } catch {
        case _: NoNodeException => println(node.path + ": no such node")
        case _: BadVersionException => println(version.get + ": version does not match")
        case _: NotEmptyException => println(node.path + ": node has children")
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
                case _: NumberFormatException => throw new OptionException(version + ": version must be an integer")
              }
              parse(rest.tail, opts + ('version -> Some(_version)))
            case _ => throw new OptionException(arg + ": missing argument")
          }
          case LongOption(_) | ShortOption(_) => throw new OptionException(arg + ": no such option")
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
  stat           get node status
  mk, create     create new node
  rm, del        delete existing node
  getacl         get node ACL
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
        "stat" -> StatCommand.Usage,
        "mk" -> CreateCommand.Usage,
        "create" -> CreateCommand.Usage,
        "rm" -> DeleteCommand.Usage,
        "del" -> DeleteCommand.Usage,
        "getacl" -> GetACLCommand.Usage,
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
