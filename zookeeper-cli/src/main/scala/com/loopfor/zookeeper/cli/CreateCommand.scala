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
import java.io.{FileInputStream, FileNotFoundException, IOException}
import java.nio.charset.Charset
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuilder

object CreateCommand {
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

    private lazy val parser =
      ("recursive", 'r') ~> enable ~~ false ++
      ("encoding", 'e') ~> asCharset ~~ UTF_8 ++
      ("sequential", 'S') ~> enable ~~ false ++
      ("ephemeral", 'E') ~> enable ~~ false ++
      ("acl", 'A') ~> as { (arg, opts) =>
        val acl = ACL parse arg match {
          case Some(a) => a
          case _ => yell(s"$arg: invalid ACL syntax")
        }
        opts("acl").asInstanceOf[Seq[ACL]] :+ acl
      } ~~ Seq()

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val opts = parser parse args
      val recurse = opts[Boolean]("recursive")
      val disp = disposition(opts)
      val acl = opts[Seq[ACL]]("acl") match {
        case Seq() => ACL.AnyoneAll
        case a => a
      }
      val params = opts.args
      val path = if (params.isEmpty) error("path must be specified") else params.head
      val data = params.tail.headOption match {
        case Some(d) => d.headOption match {
          case Some('@') =>
            val name = d drop 1
            val file = try new FileInputStream(name) catch {
              case _: FileNotFoundException => error(s"$name: file not found")
              case _: SecurityException => error(s"$name: access denied")
            }
            try read(file) catch {
              case e: IOException => error(s"$name: I/O error: ${e.getMessage}")
            } finally
              file.close()
          case _ => d getBytes opts[Charset]("encoding")
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
        case e: NodeExistsException => error(s"${Path(e.getPath).normalize}: node already exists")
        case _: NoNodeException => error(s"${node.parent.path}: no such parent node")
        case e: NoChildrenForEphemeralsException => error(s"${Path(e.getPath).normalize}: parent node is ephemeral")
        case _: InvalidACLException => error(s"${acl.mkString(",")}: invalid ACL")
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

  private def disposition(opts: OptResult): Disposition = {
    val sequential = opts[Boolean]("sequential")
    val ephemeral = opts[Boolean]("ephemeral")
    if (sequential && ephemeral) EphemeralSequential
    else if (sequential) PersistentSequential
    else if (ephemeral) Ephemeral
    else Persistent
  }
}
