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
package com.loopfor.zookeeper.cli.command

import com.loopfor.scalop._
import com.loopfor.zookeeper._
import com.loopfor.zookeeper.cli._
import java.util.regex.PatternSyntaxException
import scala.util.matching.Regex

object Find {
  val Usage = """usage: find [OPTIONS] PATTERN [PATH] [--exec COMMAND]

  Finds all child nodes matching the regular expression PATTERN at the node
  specified by PATH, and optionally applies COMMAND to each node. PATH may be
  omitted, in which case the current working path is assumed.

  Note that PATTERN is a strict regular expression as defined by
  Java 6 (http://bit.ly/zk-regex). Pattern matching only applies to node names,
  not paths.

  COMMAND is a subset of those supported by the CLI with restricted options
  noted below. If COMMAND is omitted, then `print` is assumed. In all cases,
  where a specific command would otherwise require a PATH argument, find will
  supply the value using the absolute path of matching nodes. A list of all
  matching nodes is enumerated before COMMAND is applied in a subsequent
  phase, which means there exists the possibility that the state of those nodes
  may change before the command is executed. In general, mutating commands do
  not accept a --version option and instead operate as though --force were
  specified.

  Further documentation can be found typing `help COMMAND`.

    print

    ls|dir [OPTIONS]
      --long, -l

    get [OPTIONS]
      --hex, -h
      --string, -s
      --binary, -b

    getacl

    stat|info
      --compact, -c

    mk|create [OPTIONS] PATH [DATA]
      --recursive, -r
      --encoding, -e
      --sequential, -S
      --ephemeral, -E
      --acl, -A

    set [OPTIONS] [DATA]
      --encoding, -e

    setacl [OPTIONS] ACL[...]
      --add, -a
      --remove, -r
      --set, -s

    rm|del [OPTIONS]
      --recursive, -r

options:
  --recursive, -r            : recursively finds nodes under PATH
  --verbose, -v              : display matching nodes (default)
  --quiet, -q                : suppress display of matching nodes
  --halt, -h                 : stops executing COMMAND on error
                               (default is to continue if nonfatal)
  --exec COMMAND             : executes COMMAND (below)
"""

  private val Execs = Map(
        "print" -> Print.find _,
        "ls" -> Ls.find _,
        "dir" -> Ls.find _,
        "get" -> Get.find _,
        "getacl" -> GetACL.find _,
        "stat" -> Stat.find _,
        "info" -> Stat.find _,
        "mk" -> Mk.find _,
        "create" -> Mk.find _,
        "set" -> Set.find _,
        "setacl" -> SetACL.find _,
        "rm" -> Rm.find _,
        "del" -> Rm.find _
        )

  def command(zk: Zookeeper) = new CommandProcessor {
    implicit val _zk = zk

    lazy val parser =
      ("recursive", 'r') ~> enable ~~ false ++
      ("verbose", 'v') ~> enable ~~ true ++
      ("quiet", 'q') ~> enable ~~ false ++
      ("halt", 'h') ~> enable ~~ false

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      implicit val opts = parser parse args
      val recurse = recursiveOpt
      val verbose = verboseOpt
      val halt = haltOpt
      val (pattern, afterPattern) = patternArg
      val (path, afterPath) = pathArg(afterPattern)
      val exec = execArg(afterPath)

      val base = Node(context resolve path)
      val found = if (recurse) {
        def find(node: Node, found: Seq[Node]): Seq[Node] = {
          try {
            (found /: node.children()) { case (f, c) => c.name match {
              case pattern(_*) => find(c, f :+ c)
              case _ => find(c, f)
            }}
          } catch {
            case _: NoNodeException => found
          }
        }
        find(base, Seq.empty)
      } else {
        base.children() filter { c => c.name match {
          case pattern(_*) => true
          case _ => false
        }}
      }

      def execute(node: Node) = {
        try {
          if (verbose) println(s"${node.path}")
          exec(node)
          true
        } catch {
          case e: CLIException =>
            println(e.getMessage)
            !halt
          case _: ConnectionLossException =>
            println("connection lost")
            false
          case _: SessionExpiredException =>
            println("session has expired; `exit` and restart CLI")
            false
          case _: NoAuthException =>
            println("not authorized")
            !halt
          case e: KeeperException =>
            println(s"internal zookeeper error: ${e.getMessage}")
            !halt
        }
      }

      def noop(node: Node) = true

      (execute _ /: found) { case (op, node) =>
        if (op(node)) op else noop _
      }
      context
    }
  }

  private def recursiveOpt(implicit opts: OptResult): Boolean = opts("recursive")

  private def verboseOpt(implicit opts: OptResult): Boolean = !opts[Boolean]("quiet")

  private def haltOpt(implicit opts: OptResult): Boolean = opts("halt")

  private def patternArg(implicit opts: OptResult): (Regex, Seq[String]) = opts.args match {
    case Seq(pattern, rest @ _*) =>
      (try pattern.r catch {
        case _: PatternSyntaxException => complain(s"$pattern: invalid regular expression")
      }, rest)
    case _ => complain("pattern must be specified")
  }

  private def pathArg(args: Seq[String]): (Path, Seq[String]) = args match {
    case Seq(path, rest @ _*) if !isOption(path) => (Path(path), rest)
    case _ => (Path(""), args)
  }

  private def isOption(arg: String) = (arg startsWith "-") || (arg startsWith "--")

  private def execArg(args: Seq[String])(implicit zk: Zookeeper): FindProcessor = args match {
    case Seq("--exec", command @ _*) => command match {
      case Seq(c, cargs @ _*) => Execs.get(c) match {
        case Some(fn) => fn(zk, cargs)
        case None => complain(s"$c: no such command")
      }
      case Seq() => Print.find(zk, Seq.empty)
    }
    case Seq(arg, _*) => complain(s"$arg: expecting --exec")
    case Seq() => Print.find(zk, Seq.empty)
  }

  private object Print {
    def find(zk: Zookeeper, args: Seq[String]): FindProcessor = { _ => }
  }
}
