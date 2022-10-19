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
import java.io.{FileInputStream, FileNotFoundException, IOException}
import java.nio.charset.Charset
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuilder
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.{Failure, Success}

object Mk {
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

  The --ttl option can be used in conjunction with persistent nodes only,
  which specifies the time-to-live before becoming eligible for deletion, but
  only if all child nodes have been removed.

  The --container option creates a special type of node suitable for building
  higher order constructs, such as locks and leader election protocols. Such
  nodes become eligible for deletion once all child nodes have been removed.

  One or more optional ACL entries may be specified with --acl, which must
  conform to the following syntax: <scheme>:<id>=[rwcda*]. See *setacl* command
  for further explanation of the ACL syntax.

options:
  --recursive, -r            : recursively create intermediate nodes
  --encoding, -e CHARSET     : charset used for encoding DATA (default=UTF-8)
  --sequential, -S           : appends sequence to node name
  --ephemeral, -E            : node automatically deleted when CLI exits
  --ttl, -T TTL              : time-to-live (millis) for persistent nodes
                               TTL must be greater than 0
                               ignored when -E specified
  --container, -C            : node designated as container
                               eligible for deletion when last child deleted
                               takes precedence over -S and -E
  --acl, -A                  : ACL assigned to node (default=world:anyone=*)
"""

  private lazy val opts =
    ("recursive", 'r') ~> just(true) ~~ false ::
    ("encoding", 'e') ~> as[Charset] ~~ Charset.forName("UTF-8") ::
    ("sequential", 'S') ~> just(true) ~~ false ::
    ("ephemeral", 'E') ~> just(true) ~~ false ::
    ("ttl", 'T') ~> as[Duration] ::
    ("container", 'C') ~> just(true) ~~ false ::
    ("acl", 'A') ~>+ as[ACL] ::
    Nil

  def command(zk: Zookeeper) = new CommandProcessor {
    implicit val _zk: Zookeeper = zk

    def apply(cmd: String, args: Seq[String], context: Path): Path = {
      val optr = opts <~ args
      val recurse = optr[Boolean]("recursive")
      val disp = dispOpt(optr)
      val acl = aclOpt(optr)
      val (path, afterPath) = pathArg(optr, false)
      val data = dataArg(optr, afterPath)
      val node = Node(context.resolve(path))
      create(node, recurse, disp, acl, data)
      context
    }
  }

  def find(zk: Zookeeper, args: Seq[String]) = new FindProcessor {
    implicit val _zk: Zookeeper = zk
    val optr = opts <~ args
    val recurse = optr[Boolean]("recursive")
    val disp = dispOpt(optr)
    val acl = aclOpt(optr)
    val (path, afterPath) = pathArg(optr, true)
    val data = dataArg(optr, afterPath)

    def apply(node: Node): Unit = {
      create(node resolve path, recurse, disp, acl, data)
    }
  }

  private def create(node: Node, recurse: Boolean, disp: Disposition, acl: Seq[ACL], data: Array[Byte])
      (implicit zk: Zookeeper): Unit = {
    try {
      if (recurse) {
        node.path.parts.tail.dropRight(1).foldLeft(Path("/")) { case (parent, part) =>
          val node = Node(parent.resolve(part))
          try node.create(Array.empty, ACL.AnyoneAll, Persistent) catch {
            case _: NodeExistsException =>
          }
          node.path
        }
      }
      node.create(data, acl, disp)
    } catch {
      case e: NodeExistsException => complain(s"${Path(e.getPath).normalize}: node already exists")
      case _: NoNodeException => complain(s"${node.parent.path}: no such parent node")
      case e: NoChildrenForEphemeralsException => complain(s"${Path(e.getPath).normalize}: parent node is ephemeral")
      case _: InvalidACLException => complain(s"${acl.mkString(",")}: invalid ACL")
      case e: UnimplementedException => complain(s"${Path(e.getPath).normalize}: feature (such as --ttl) may not be enabled")
    }
  }

  private def dispOpt(optr: OptResult): Disposition = {
    if (optr[Boolean]("container"))
      Container
    else {
      val ttl = optr.get[Duration] ("ttl")
      (optr[Boolean]("sequential"), optr[Boolean]("ephemeral")) match {
        case (false, true) =>
          Ephemeral
        case (true, true) =>
          EphemeralSequential
        case (false, false) =>
          ttl match {
            case Some(t) => PersistentTimeToLive(t)
            case None => Persistent
          }
        case (true, false) =>
          ttl match {
            case Some(t) => PersistentSequentialTimeToLive(t)
            case None => PersistentSequential
          }
      }
    }
  }

  private def aclOpt(optr: OptResult): Seq[ACL] = optr.get("acl") match {
    case Some(acl) => acl
    case None => ACL.AnyoneAll
  }

  private def pathArg(optr: OptResult, relative: Boolean): (Path, Seq[String]) = optr.args match {
    case Seq(path, rest @ _*) =>
      val p = Path(path)
      (if (relative) p.path.headOption match {
        case Some('/') => Path(p.path.drop(1))
        case _ => p
      } else p, rest)
    case Seq() => complain("path must be specified")
  }

  private def dataArg(optr: OptResult, args: Seq[String]): Array[Byte] = args match {
    case Seq(data, _*) => data.headOption match {
      case Some('@') =>
        val name = data.drop(1)
        val file = try new FileInputStream(name) catch {
          case _: FileNotFoundException => complain(s"$name: file not found")
          case _: SecurityException => complain(s"$name: access denied")
        }
        try read(file) catch {
          case e: IOException => complain(s"$name: I/O error: ${e.getMessage}")
        } finally
          file.close()
      case _ => data.getBytes(optr[Charset]("encoding"))
    }
    case Seq() => Array.empty[Byte]
  }

  private def read(file: FileInputStream): Array[Byte] = {
    @tailrec def read(buffer: ArrayBuilder[Byte]): Array[Byte] = {
      val c = file.read()
      if (c == -1) buffer.result() else read(buffer += c.toByte)
    }
    read(ArrayBuilder.make[Byte])
  }

  implicit def argToTTL(arg: String): Either[String, Duration] = {
    try {
      val ttl = arg.toLong
      if (ttl > 0) Right(ttl.millis) else Left("must be greater than zero")
    } catch {
      case _: NumberFormatException => Left("unrecognized TTL")
    }
  }

  implicit def argToACL(arg: String): Either[String, ACL] = ACL.parse(arg) match {
    case Success(acl) => Right(acl)
    case Failure(e) => Left(e.getMessage)
  }
}
