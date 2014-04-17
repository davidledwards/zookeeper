/*
 * Copyright 2013 David Edwards
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.loopfor.zookeeper.cli

import com.loopfor.scalop._
import com.loopfor.zookeeper._
import java.io.{BufferedReader, File, FileInputStream, FileNotFoundException, IOException, InputStream, InputStreamReader}
import java.net.InetSocketAddress
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicReference
import org.apache.log4j.{Level, LogManager, PropertyConfigurator}
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.language._

object CLI {
  def main(args: Array[String]) {
    import Console.err
    val status = try run(args) catch {
      case e: OptException =>
        err println e.getMessage
        1
      case e: CLIException =>
        err println e.getMessage
        1
      case e: Exception =>
        err println s"internal error: ${e.getMessage}"
        err println ">> stack trace"
        e printStackTrace err
        1
    }
    sys exit status
  }

  private val Usage = """usage: zk [OPTIONS] SERVER[...]
       zk [-? | --help]

  An interactive client for a ZooKeeper cluster.

  At least one SERVER in the cluster must be specified, which is defined as
  `host[:port]`. If `port` is unspecified, then 2181 is assumed.

server options:
  --path, -p                 : root path (default=/)
  --timeout, -t SECONDS      : session timeout (default=60)
  --readonly, -r             : allow readonly connection

command options:
  --command, -c COMMAND      : execute COMMAND
  --file, -f FILE            : execute commands in FILE
  --encoding, -e CHARSET     : charset applicable to FILE (default=UTF-8)

log options:
  --log FILE                 : appends log messages to FILE
                               (default=$HOME/zk.log)
  --level LEVEL              : severity LEVEL of log messages
                               one of: all, info, warn, error (default=warn)
  --nolog                    : discard log messages
"""

  private val UTF_8 = Charset forName "UTF-8"
  private val DefaultPort = 2181

  private def run(args: Array[String]): Int = {
    if (args.size == 0) {
      println(Usage)
      0
    } else {
      implicit val opts = parser parse args
      if (help) {
        println(Usage)
        0
      } else {
        val rc = log match {
          case Some((file, level)) =>
            System.setProperty("zk.log", file.getAbsolutePath)
            System.setProperty("zk.level", level.toString)
            "log.properties"
          case _ =>
            "nolog.properties"
        }
        LogManager.resetConfiguration()
        PropertyConfigurator configure Thread.currentThread.getContextClassLoader.getResource(rc)

        val state = new AtomicReference[StateEvent](Disconnected)
        val config = Configuration(servers) withPath(path) withTimeout(timeout) withAllowReadOnly(readonly) withWatcher {
          (event, session) => state set event
        }
        val zk = try Zookeeper(config) catch {
          case e: IOException => CLIException(s"I/O error: ${e.getMessage}")
        }

        val verbs = Map(
              "config" -> ConfigCommand(config, log, state),
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
                  println(s"$cmd: no such command")
                  context
                }
              }

        def execute(args: Seq[String], context: Path): Option[Path] = {
          if (args.size > 0) {
            try Some(verbs(args.head)(args.head, args.tail, context)) catch {
              case e: OptException =>
                println(e.getMessage)
                None
              case e: CLIException =>
                println(e.getMessage)
                None
              case _: ConnectionLossException =>
                println("connection lost")
                None
              case _: SessionExpiredException =>
                println("session has expired; `exit` and restart CLI")
                None
              case _: NoAuthException =>
                println("not authorized")
                None
              case e: KeeperException =>
                println(s"internal zookeeper error: ${e.getMessage}")
                None
            }
          } else
            Some(context)
        }

        commands match {
          case Some(cmds) =>
            @tailrec def process(cmds: Seq[String], context: Path): Int = cmds.headOption match {
              case Some(cmd) =>
                val args = Splitter split cmd
                execute(args, context) match {
                  case Some(_context) => if (_context == null) 0 else process(cmds.tail, _context)
                  case None => 1
                }
              case None => 0
            }
            process(cmds, Path("/"))
          case None =>
            val reader = Reader(verbs.keySet, zk)
            @tailrec def process(context: Path): Unit = {
              val args = reader(context)
              execute(args, context) match {
                case Some(_context) => if (_context != null) process(_context)
                case None => process(context)
              }
            }
            process(Path("/"))
            0
        }
      }
    }
  }

  private lazy val parser =
    ("help", '?') ~> enable ~~ false ++
    ("path", 'p') ~> asString ~~ "" ++
    ("timeout", 't') ~> asInt ~~ 60 ++
    ("readonly", 'r') ~> enable ~~ false ++
    ("command", 'c') ~> asSomeString ~~ None ++
    ("file", 'f') ~> asSomeString ~~ None ++
    ("encoding", 'e') ~> asCharset ~~ UTF_8 ++
    "log" ~> as { (arg, _) => Some(new File(arg)) } ~~ None ++
    "level" ~> as { (arg, _) =>
      Some(arg.toLowerCase match {
        case "all" => Level.ALL
        case "info" => Level.INFO
        case "warn" => Level.WARN
        case "error" => Level.ERROR
        case _ => yell(s"$arg: must be one of (all, info, warn, error)")
      })
    } ~~ None ++
    "nolog" ~> enable ~~ false

  private def help(implicit opts: OptResult): Boolean = opts("help")

  private def servers(implicit opts: OptResult): Seq[InetSocketAddress] = opts.args match {
    case Nil => CLIException("no servers specified")
    case params => params map { server =>
      val i = server indexOf ':'
      val (host, port) =
        if (i == -1) (server, DefaultPort)
        else if (i == 0) CLIException(s"$server: missing host; expecting `host[:port]`")
        else
          (server take i,
            server drop i + 1 match {
              case "" => DefaultPort
              case p => try p.toInt catch {
                case _: NumberFormatException => CLIException(s"$server: port invalid; expecting `host[:port]`")
              }
            })
      new InetSocketAddress(host, port)
    }
  }

  private def path(implicit opts: OptResult): String = opts("path")

  private def timeout(implicit opts: OptResult): Duration = opts[Int]("timeout") seconds

  private def readonly(implicit opts: OptResult): Boolean = opts("readonly")

  private def commands(implicit opts: OptResult): Option[Seq[String]] = {
    def read(file: InputStream, cs: Charset): Seq[String] = {
      val f = new BufferedReader(new InputStreamReader(file, cs))
      @tailrec def read(cmds: ArrayBuffer[String]): Seq[String] = {
        val line = f.readLine()
        if (line == null) cmds.toSeq else read(cmds += line)
      }
      read(ArrayBuffer.empty)
    }
    val cmds = opts[Option[String]]("file") match {
      case Some(name) =>
        val file = try new FileInputStream(name) catch {
          case _: FileNotFoundException => CLIException(s"$name: file not found")
          case _: SecurityException => CLIException(s"$name: access denied")
        }
        try read(file, opts[Charset]("encoding")) catch {
          case e: IOException => CLIException(s"$name: I/O error: ${e.getMessage}")
        } finally file.close()
      case _ => opts[Option[String]]("command") match {
        case Some(cmd) => Seq(cmd)
        case _ => null
      }
    }
    Option(cmds)
  }

  private def log(implicit opts: OptResult): Option[(File, Level)] = {
    if (opts[Boolean]("nolog"))
      None
    else {
      val file = opts[Option[File]]("log") getOrElse new File(System getProperty "user.home", "zk.log")
      val level = opts[Option[Level]]("level") getOrElse Level.WARN
      Some(file, level)
    }
  }
}

private[cli] class CLIException(message: String, cause: Throwable) extends Exception(message, cause) {
  def this(message: String) = this(message, null)
}

private[cli] object CLIException {
  def apply(message: String): Nothing = throw new CLIException(message)
}
