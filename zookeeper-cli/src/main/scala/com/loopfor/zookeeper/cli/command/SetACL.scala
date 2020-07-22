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
package com.loopfor.zookeeper.cli.command

import com.loopfor.scalop._
import com.loopfor.zookeeper._
import com.loopfor.zookeeper.cli._
import scala.util.{Failure, Success}

object SetACL {
  val Usage = """usage: setacl [OPTIONS] PATH ACL[...]

  Sets the ACL for the node specified by PATH.

  At least one ACL entry must be provided, which must conform to the following
  syntax: <scheme>:<id>=[rwcda*], where zero or more of [rwcda*] characters may
  be given as permissions. The permission values are (r)ead, (w)rite, (c)reate,
  (d)elete, (a)dmin and all(*). The :<id> fragment is omitted if the scheme is
  `auth` (see below).

  Unless otherwise specified, --set is assumed, which means that the given ACL
  replaces the current ACL associated with the node at PATH. Both --add
  and --remove options first query the current ACL before applying the
  respective operation. Therefore, the entire operation is not atomic, though
  specifying --version ensures that no intervening operations have changed the
  state.

  Valid scheme/id combinations:
    `world:anyone`
      Represents anyone. This is the only acceptable form of this scheme.

    `auth`
      Represents any authenticated user, hence elimination of :<id> fragment.
      The is the only acceptable form of this scheme.

    `digest:<username>:<password>`
      Represents users backed by a directory in ZooKeeper.
        ex: digest:alice:secret

    `host:<domain>`
      Represents a specific host or hosts within a given domain.
        ex: host:server1.foo.com
            host:foo.com

    `ip:<address>[/<prefix>]`
      Represents an IPv4 or IPv6 address in dotted decimal notation. An
      optional network prefix specifies the number of leading bits to consider
      when matching. <prefix> may be in the range [0,32] for IPv4 and [0,128]
      for IPv6. If not specified, <prefix> is assumed to be the maximum bits.
        ex: ip:1.2.3.4
            ip:1.2.3.4/24

options:
  --add, -a                  : adds ACL to existing list
  --remove, -r               : removes ACL from existing list
  --set, -s                  : replaces existing list with ACL (default)
  --version, -v VERSION      : version required to match in ZooKeeper
  --force, -f                : forcefully set ACL regardless of version
"""

  def command(zk: Zookeeper) = new CommandProcessor {
    implicit val _zk = zk

    val opts =
      ("add", 'a') ~> just(true) ~~ false ::
      ("remove", 'r') ~> just(true) ~~ false ::
      ("set", 's') ~> just(true) ~~ false ::
      ("force", 'f') ~> just(true) ~~ false ::
      ("version", 'v') ~> as[Option[Int]] ~~ None ::
      Nil

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val optr = opts <~ args
      val action = actionOpt(optr)
      val version = versionOpt(optr)
      val (path, afterPath) = pathArg(optr)
      val acl = aclArgs(afterPath)
      val node = Node(context.resolve(path))
      setACL(node, version, action, acl)
      context
    }
  }

  def find(zk: Zookeeper, args: Seq[String]) = new FindProcessor {
    val opts =
      ("add", 'a') ~> just(true) ~~ false ::
      ("remove", 'r') ~> just(true) ~~ false ::
      ("set", 's') ~> just(true) ~~ false ::
      Nil
    val optr = opts <~ args
    val action = actionOpt(optr)
    val acl = aclArgs(optr.args)

    def apply(node: Node): Unit = {
      setACL(node, None, action, acl)
    }
  }

  private def setACL(node: Node, version: Option[Int], action: Symbol, acl: Seq[ACL]): Unit = {
    val (curACL, _) = try node.getACL() catch {
      case _: NoNodeException => complain(s"${node.path}: no such node")
    }
    val newACL = action match {
      case Symbol("add") => acl.foldLeft(toMap(curACL)) { case (c, a) => c + (a.id -> a) }.values.toSeq
      case Symbol("remove") => acl.foldLeft(toMap(curACL)) { case (c, a) => c - a.id }.values.toSeq
      case Symbol("set") => acl
    }
    newACL match {
      case Seq() => complain("new ACL would be empty")
      case _ =>
        try node.setACL(newACL, version) catch {
          case _: NoNodeException => complain(s"${node.path}: no such node")
          case _: BadVersionException => complain(s"${version.get}: version does not match")
          case _: InvalidACLException => complain(s"${newACL.mkString(",")}: invalid ACL")
        }
    }
  }

  private def actionOpt(optr: OptResult): Symbol = {
    if (optr[Boolean]("set")) Symbol("set")
    else if (optr[Boolean]("add")) Symbol("add")
    else if (optr[Boolean]("remove")) Symbol("remove")
    else Symbol("set")
  }

  private def versionOpt(optr: OptResult): Option[Int] = {
    val force = optr[Boolean]("force")
    if (force) None
    else optr[Option[Int]]("version") match {
      case None => complain("version must be specified; otherwise use --force")
      case v => v
    }
  }

  private def pathArg(optr: OptResult): (Path, Seq[String]) = optr.args match {
    case Seq(path, rest @ _*) => (Path(path), rest)
    case Seq() => complain("path must be specified")
  }

  private def aclArgs(args: Seq[String]): Seq[ACL] = args match {
    case Seq() => complain("ACL must be specified")
    case _ => args.map { acl =>
      ACL.parse(acl) match {
        case Success(a) => a
        case Failure(e) => complain(e.getMessage)
      }
    }
  }

  private def toMap(acl: Seq[ACL]): Map[Id, ACL] = {
    acl.foldLeft(Map.empty[Id, ACL]) { case (m, a) => m + (a.id -> a) }
  }
}
