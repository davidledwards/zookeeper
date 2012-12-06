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
import jline.console.completer.Completer
import jline.console.completer.ArgumentCompleter
import java.util.concurrent.atomic.AtomicReference
import java.io.IOException

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
  --path, -p                 : root path (/=default)
  --timeout, -t SECONDS      : session timeout (0=default)
  --readonly, -r             : allow readonly connection
"""

  private val COMMANDS = Seq("ls", "quit")

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
          "cd" -> CdCommand(),
          "pwd" -> PwdCommand(),
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
  val Usage = """usage: cd [PATH]

  Changes the current working path to PATH if specified. If PATH is omitted,
  then '/' is assumed.
"""

  def apply() = new Command {
    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      if (args.isEmpty) Path("/") else context.resolve(args.head).normalize
    }
  }
}

private object PwdCommand {
  val Usage = """usage: pwd

  Shows the current working path.
"""

  def apply() = new Command {
    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      println(context.path)
      context
    }
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
  val Usage = """usage: get [OPTIONS] [PATH...]

  Gets the data for the node specified by each PATH. PATH may be omitted, in
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
        try {
          val (data, status) = node.get()
          if (count > 1)
            println(node.path + ":")
          display(data)
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

  private def display(data: Array[Byte]) {
    @tailrec def display(n: Int) {
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

  private def charOf(b: Byte): Char = if (b >= 32 && b < 127) b.asInstanceOf[Char] else '.'

  private def pad(n: Int): String = Array.fill(n)(' ') mkString
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
  val Usage = """usage: mk|create [OPTIONS] [PATH...]

  Creates the node specified by each PATH. PATH may be omitted, in which case
  the current working path is assumed.

options:
  --recursive, -r            : recursively creates intermediate nodes
  --sequential, -s           : appends sequence to node name
  --ephemeral, -e            : node automatically deleted when CLI exits
"""

  def apply(zk: Zookeeper) = new Command {
    private implicit val _zk = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = try parse(args) catch {
        case e: OptionException => println(e.getMessage)
        return context
      }
      val recurse = opts('recursive).asInstanceOf[Boolean]
      val disp = disposition(opts)
      val path = opts('params).asInstanceOf[Seq[String]].head
      val node = Node(context resolve path)
      try {
        node.create(Array(), ACL.EveryoneAll, disp)
      } catch {
        case e: NodeExistsException => println(Path(e.getPath).normalize + ": node already exists")
        case _: NoNodeException => println(node.parent.path + ": no such parent node")
        case _: NoChildrenForEphemeralsException => println(node.parent.path + ": parent node is ephemeral")
      }
      context
    }
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
          case LongOption("sequential") | ShortOption("s") => parse(rest, opts + ('sequential -> true))
          case LongOption("ephemeral") | ShortOption("e") => parse(rest, opts + ('ephemeral -> true))
          case LongOption(_) | ShortOption(_) => throw new OptionException(arg + ": no such option")
          case _ => opts + ('params -> args)
        }
      }
    }
    parse(args, Map(
          'recursive -> false,
          'sequential -> false,
          'ephemeral -> false,
          'params -> Seq("")))
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
      if (params.isEmpty) {
        println("path must be specified")
        return context
      }
      val path = params.head
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
TAB key can be used to auto-complete commands and node paths.

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
