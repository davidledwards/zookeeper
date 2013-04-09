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

object SetACLCommand {
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

    private lazy val parser =
      ("add", 'a') ~> enable ~~ false ++
      ("remove", 'r') ~> enable ~~ false ++
      ("set", 's') ~> enable ~~ false ++
      ("force", 'f') ~> enable ~~ false ++
      ("version", 'v') ~> asSomeInt ~~ None

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = parser parse args
      val action =
        if (opts[Boolean]("set")) 'set
        else if (opts[Boolean]("add")) 'add
        else if (opts[Boolean]("remove")) 'remove
        else 'set
      val version = {
        val force = opts[Boolean]("force")
        if (force) None
        else opts[Option[Int]]("version") match {
          case None => error("version must be specified; otherwise use --force")
          case v => v
        }
      }
      val path = if (opts.args.isEmpty) error("path must be specified") else opts.args.head
      val acl = opts.args.tail match {
        case Seq() => error("ACL must be specified")
        case acls => acls map { acl =>
          ACL parse acl match {
            case Some(a) => a
            case _ => error(s"$acl: invalid ACL syntax")
          }
        }
      }
      val node = Node(context resolve path)
      val (curACL, _) = try node.getACL() catch {
        case _: NoNodeException => error(s"${node.path}: no such node")
      }
      val newACL = action match {
        case 'add => (toMap(curACL) /: acl) { case (c, a) => c + (a.id -> a) }.values.toSeq
        case 'remove => (toMap(curACL) /: acl) { case (c, a) => c - a.id }.values.toSeq
        case 'set => acl
      }
      if (newACL.isEmpty) error("new ACL would be empty")
      try node.setACL(newACL, version) catch {
        case _: NoNodeException => error(s"${node.path}: no such node")
        case _: BadVersionException => error(s"${version.get}: version does not match")
        case _: InvalidACLException => error(s"${newACL.mkString(",")}: invalid ACL")
      }
      context
    }

    private def toMap(acl: Seq[ACL]): Map[Id, ACL] =
      (Map[Id, ACL]() /: acl) { case (m, a) => m + (a.id -> a) }
  }
}
