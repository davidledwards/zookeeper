/*
 * Copyright 2020 David Edwards
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
import com.loopfor.zookeeper.cli.command._
import java.io.{BufferedReader, File, FileInputStream, FileNotFoundException, IOException, InputStream, InputStreamReader}
import java.net.InetSocketAddress
import java.nio.charset.Charset
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.language.implicitConversions

object CLI {
  def main(args: Array[String]): Unit = {
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

options:
  --quiet, -q                : suppress console messages
  --log FILE                 : appends log messages to FILE
                               (default=$HOME/zk.log)
  --level LEVEL              : severity LEVEL of log messages
                               one of: all, info, warn, error (default=warn)
  --nolog                    : discard log messages
  --version                  : show version information
  --help, -? COMMAND         : show help for COMMAND
                               type `zk --help help` for list of commands
"""

  private val UTF_8 = Charset forName "UTF-8"
  private val DefaultPort = 2181

  private def run(args: Array[String]): Int = {
    if (args.size == 0) {
      println(Usage)
      0
    } else {
      val optr = opts <~ args.to(Seq)
      if (optr[Boolean]("version")) {
        println(s"zk ${Version.CLI}")
        0
      } else {
        optr.get[Option[String]]("help") match {
          case Some(opt) =>
            val help = opt match {
              case Some(cmd) => Help.usageOf(cmd)
              case None => Usage
            }
            println(help)
            0
          case None =>
            val path = optr[String]("path")
            val timeout = optr[Duration]("timeout")
            val readonly = optr[Boolean]("readonly")
            val commands = commandsOpt(optr)
            val verbose = !optr[Boolean]("quiet") && commands.isEmpty
            val log = logOpt(optr)
            val servers = serverArgs(optr)

            val rc = log match {
              case Some((file, level)) =>
                System.setProperty("zk.log", file.getAbsolutePath)
                System.setProperty("zk.level", level.toString)
                "logback-enabled.xml"
              case _ =>
                "logback-disabled.xml"
            }
            System.setProperty("logback.configurationFile", rc)

            if (verbose) {
              val hosts = (servers.map { s => s"${s.getHostName}:${s.getPort}" }).mkString(",")
              println(s"connecting to {${hosts}} @ ${if (path == "") "/" else path} ...")
            }

            val config = Configuration(servers).withPath(path).withTimeout(timeout).withAllowReadOnly(readonly)
            val zk = try Zookeeper(config) catch {
              case e: IOException => CLIException(s"I/O error: ${e.getMessage}")
            }

            val verbs = Map(
                  "config" -> Config.command(config, log, zk),
                  "cd" -> Cd.command(zk),
                  "pwd" -> Pwd.command(zk),
                  "ls" -> Ls.command(zk),
                  "dir" -> Ls.command(zk),
                  "stat" -> Stat.command(zk),
                  "info" -> Stat.command(zk),
                  "get" -> Get.command(zk),
                  "set" -> Set.command(zk),
                  "getacl" -> GetACL.command(zk),
                  "setacl" -> SetACL.command(zk),
                  "mk" -> Mk.command(zk),
                  "create" -> Mk.command(zk),
                  "rm" -> Rm.command(zk),
                  "del" -> Rm.command(zk),
                  "find" -> Find.command(zk),
                  "quit" -> Quit.command(zk),
                  "exit" -> Quit.command(zk),
                  "help" -> Help.command(),
                  "?" -> Help.command()).withDefaultValue(new CommandProcessor {
                    def apply(cmd: String, args: Seq[String], context: Path): Path = {
                      println(s"$cmd: no such command")
                      context
                    }
                  })

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
                @tailrec def process(cmds: Seq[String], context: Path): Int = cmds match {
                  case Seq(cmd, next @ _*) =>
                    val args = Splitter.split(cmd)
                    execute(args, context) match {
                      case Some(c) => if (c == null) 0 else process(next, c)
                      case None => 1
                    }
                  case Seq() => 0
                }
                process(cmds, Path("/"))
              case None =>
                val reader = Reader(verbs.keySet, zk)
                @tailrec def process(context: Path): Unit = {
                  val args = reader(context)
                  execute(args, context) match {
                    case Some(c) => if (c != null) process(c)
                    case None => process(context)
                  }
                }
                process(Path("/"))
                0
            }
        }
      }
    }
  }

  private val opts =
    "version" ~> just(true) ~~ false ::
    ("help", '?') ~> maybe[String] ::
    ("path", 'p') ~> as[String] ~~ "" ::
    ("timeout", 't') ~> as[Int, Duration] { _.seconds } ~~ 60.seconds ::
    ("readonly", 'r') ~> just(true) ~~ false ::
    ("command", 'c') ~> as[Option[String]] ~~ None ::
    ("file", 'f') ~> as[Option[String]] ~~ None ::
    ("encoding", 'e') ~> as[Charset] ~~ UTF_8 ::
    ("quiet", 'q') ~> just(true) ~~ false ::
    "log" ~> as[File] ~~ new File(System.getProperty("user.home"), "zk.log") ::
    "level" ~> as[Level] ~~ WarnLevel ::
    "nolog" ~> just(true) ~~ false ::
    Nil

  implicit def argToLevel(arg: String): Either[String, Level] = arg.toLowerCase match {
    case "all" => Right(AllLevel)
    case "info" => Right(InfoLevel)
    case "warn" => Right(WarnLevel)
    case "error" => Right(ErrorLevel)
    case _ => Left(s"$arg: must be one of (all, info, warn, error)")
  }

  private def commandsOpt(optr: OptResult): Option[Seq[String]] = {
    def read(file: InputStream, cs: Charset): Seq[String] = {
      val f = new BufferedReader(new InputStreamReader(file, cs))
      @tailrec def read(cmds: ArrayBuffer[String]): Seq[String] = {
        val line = f.readLine()
        if (line == null) cmds.toSeq else read(cmds += line)
      }
      read(ArrayBuffer.empty)
    }

    val cmds = optr[Option[String]]("file") match {
      case Some(name) =>
        val file = try new FileInputStream(name) catch {
          case _: FileNotFoundException => CLIException(s"$name: file not found")
          case _: SecurityException => CLIException(s"$name: access denied")
        }
        try read(file, optr[Charset]("encoding")) catch {
          case e: IOException => CLIException(s"$name: I/O error: ${e.getMessage}")
        } finally file.close()
      case _ => optr[Option[String]]("command") match {
        case Some(cmd) => Seq(cmd)
        case _ => null
      }
    }
    Option(cmds)
  }

  private def logOpt(optr: OptResult): Option[(File, Level)] = {
    if (optr[Boolean]("nolog"))
      None
    else {
      val file = optr[File]("log")
      val level = optr[Level]("level")
      Some(file, level)
    }
  }

  private def serverArgs(optr: OptResult): Seq[InetSocketAddress] = {
    def isHostChar(c: Char): Boolean = {
      (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_'
    }

    def validate(host: String): String = {
      // forall() was not working with Scala 3 compiler because of an implicit cast to Level, hence reason for
      // use of fold operation.
      if (host.foldLeft(true) { (valid, c) => valid && isHostChar(c) })
        host
      else
        throw CLIException(s"$host: invalid host name")
    }

    optr.args match {
      case Nil => CLIException("no servers specified")
      case params => params.map { server =>
        val i = server.indexOf(':')
        val (host, port) =
          if (i == -1) (server, DefaultPort)
          else if (i == 0) CLIException(s"$server: missing host; expecting `host[:port]`")
          else
            (server.take(i),
              server.drop(i + 1) match {
                case "" => DefaultPort
                case p => try p.toInt catch {
                  case _: NumberFormatException => CLIException(s"$server: port invalid; expecting `host[:port]`")
                }
              })
        new InetSocketAddress(validate(host), port)
      }
    }
  }
}
