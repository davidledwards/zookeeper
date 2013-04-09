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
import java.io.{BufferedReader, FileInputStream, FileNotFoundException, IOException, InputStream, InputStreamReader}
import java.net.InetSocketAddress
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.language._

class CLIException(message: String) extends Exception(message)

object CLI {
  def main(args: Array[String]) {
    import Console.err
    try {
      val status = run(args)
      sys exit status
    } catch {
      case e: OptException =>
        err println e.getMessage
        sys exit 1
      case e: CLIException =>
        err println e.getMessage
        sys exit 1
      case e: Exception =>
        err println s"internal error: ${e.getMessage}"
        err println ">> stack trace"
        e printStackTrace err
        sys exit 1
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

  private lazy val parser =
    ("help", '?') ~> enable ~~ false ++
    ("path", 'p') ~> asString ~~ "" ++
    ("timeout", 't') ~> asInt ~~ 60 ++
    ("readonly", 'r') ~> enable ~~ false ++
    ("command", 'c') ~> asSomeString ~~ None ++
    ("file", 'f') ~> asSomeString ~~ None ++
    ("encoding", 'e') ~> asCharset ~~ UTF_8

  private def run(args: Array[String]): Int = {
    if (args.size == 0) {
      println(Usage)
      return 0
    }
    val opts = parser parse args
    if (opts[Boolean]("help")) {
      println(Usage)
      return 0
    }

    val servers = opts.args match {
      case Nil => error("no servers specified")
      case params => params map { server =>
        val i = server indexOf ':'
        if (i == -1) error(s"$server: missing port; expecting `host:port`")
        else if (i == 0) error(s"$server: missing host; expecting `host:port`")
        else {
          new InetSocketAddress(server take i, try {
            (server drop i + 1).toInt
          } catch {
            case _: NumberFormatException => error(s"$server: port invalid; expecting `host:port`")
          })
        }
      }
    }

    val path = opts[String]("path")
    val timeout = opts[Int]("timeout") seconds
    val readonly = opts[Boolean]("readonly")

    def read(file: InputStream, cs: Charset): Seq[String] = {
      val f = new BufferedReader(new InputStreamReader(file, cs))
      @tailrec def read(cmds: ArrayBuffer[String]): Seq[String] = {
        val line = f.readLine()
        if (line == null) cmds.toSeq else read(cmds += line)
      }
      read(ArrayBuffer.empty)
    }

    val cmds = {
      opts[Option[String]]("file") match {
        case Some(name) =>
          val file = try new FileInputStream(name) catch {
            case _: FileNotFoundException => error(s"$name: file not found")
            case _: SecurityException => error(s"$name: access denied")
          }
          try read(file, opts[Charset]("encoding")) catch {
            case e: IOException => error(s"$name: I/O error: ${e.getMessage}")
          } finally file.close()
        case _ => opts[Option[String]]("command") match {
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
      case e: IOException => error(s"I/O error: ${e.getMessage}")
    }

    val commands = Map(
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
        println(s"$cmd: no such command")
        context
      }
    }

    def execute(args: Seq[String], context: Path): Path = {
      if (args.size > 0)
        commands(args.head)(args.head, args.tail, context)
      else
        context
    }

    cmds match {
      case null =>
        val reader = Reader(commands.keySet, zk)

        @tailrec def process(context: Path) {
          if (context != null) {
            val args = reader(context)
            process(try execute(args, context) catch {
              case e: OptException =>
                println(e.getMessage)
                context
              case e: CLIException =>
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
                println(s"internal zookeeper error: ${e.getMessage}")
                context
            })
          }
        }
        process(Path("/"))
        0
      case cmds =>
        (Path("/") /: cmds) { case (context, cmd) =>
          val args = Splitter split cmd
          try execute(args, context) catch {
            case e: OptException =>
              println(e.getMessage)
              return 1
            case e: CLIException =>
              println(e.getMessage)
              return 1
            case _: ConnectionLossException =>
              println("connection lost")
              return 1
            case _: SessionExpiredException =>
              println("session has expired")
              return 1
            case _: NoAuthException =>
              println("not authorized")
              return 1
            case e: KeeperException =>
              println(s"internal zookeeper error: ${e.getMessage}")
              return 1
          }
        }
        0
    }
  }

  private def error(message: String): Nothing = throw new CLIException(message)
}
