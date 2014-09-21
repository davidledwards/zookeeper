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
package com.loopfor.zookeeper

import org.apache.zookeeper.ZooDefs.{Ids, Perms}
import org.apache.zookeeper.data.{ACL => ZACL}
import scala.collection.JavaConverters._
import scala.language._
import scala.util.{Failure, Success, Try}

/**
 * An ''access control list'' assignable to a ZooKeeper node.
 *
 * @param id the identity to which permissions apply
 * @param permission the bitwise union of permissions that apply to this ACL
 */
case class ACL(id: Id, permission: Int) extends ZACL(permission, id.zid)

/**
 * Constructs and deconstructs [[ACL]] values.
 * 
 * The permissions assigned to an ACL are constructed by performing a bitwise union of individual permission attributes:
 * [[Read]], [[Write]], [[Create]], [[Delete]], [[Admin]]. In addition, the [[All]] permission encompasses all of these
 * attributes.
 * 
 * Several commonly used ACL values have been predefined for sake of convenience: [[AnyoneAll]], [[AnyoneRead]],
 * [[CreatorAll]].
 *
 * @see [[http://zookeeper.apache.org/doc/r3.4.6/zookeeperProgrammers.html#sc_ZooKeeperAccessControl ACLs]]
 */
object ACL {
  /**
   * A permission to read.
   */
  val Read: Int = Perms.READ

  /**
   * A permission to write.
   */
  val Write: Int = Perms.WRITE

  /**
   * A permission to create.
   */
  val Create: Int = Perms.CREATE

  /**
   * A permission to delete.
   */
  val Delete: Int = Perms.DELETE

  /**
   * A permission to perform administrative functions.
   */
  val Admin: Int = Perms.ADMIN

  /**
   * A composition of all permissions.
   * 
   * This is equivalent to the bitwise union of the following permissions:
   * {{{
   * Read | Write | Create | Delete | Admin
   * }}}
   */
  val All: Int = Perms.ALL

  /**
   * An ACL in which [[Id#Anyone Anyone]] is granted [[All]] permissions.
   */
  val AnyoneAll: Seq[ACL] = ACL(Ids.OPEN_ACL_UNSAFE)

  /**
   * An ACL in which [[Id#Anyone Anyone]] is granted [[Read]] permission only.
   */
  val AnyoneRead: Seq[ACL] = ACL(Ids.READ_ACL_UNSAFE)

  /**
   * An ACL in which the [[Id#Creator Creator]] is granted [[All]] permissions.
   */
  val CreatorAll: Seq[ACL] = ACL(Ids.CREATOR_ALL_ACL)

  /**
   * Constructs a new ACL from the input string `s`.
   * 
   * @param s a string representing the ACL
   * @return the ACL in `s` if it conforms to the specific syntax
   * 
   * @throws IllegalArgumentException if `s` does not conform to the proper syntax
   *
   * @see [[parse]]
   */
  def apply(s: String): ACL = parse(s) match {
    case Success(acl) => acl
    case Failure(e) => throw e
  }

  /**
   * Parses the ACL in the input string `s`.
   * 
   * The syntax of `s` is `"''scheme'':''id''=[rwcda*]"`, where the following apply:
   *  - the `:` delimiter may be omitted if ''id'' is not required
   *  - `rwcda*` may be repeated zero or more times
   *
   * @param s a string representing the ACL
   * @return a `Success` containing the ACL in `s` if it conforms to the proper syntax, otherwise a `Failure`
   * containing the offending exception
   */
  def parse(s: String): Try[ACL] = Try {
    def error(message: String): Nothing = throw new IllegalArgumentException(s"${s}: ${message}")
    s.split("=", 2) match {
      case Array(id, permission) =>
        (Id parse id) match {
          case Success(i) =>
            permission match {
              case Permission(p) => ACL(i, p)
              case _ => error("invalid permissions")
            }
          case Failure(e) => throw e
        }
      case _ => error("expecting permissions")
    }
  }

  private[zookeeper] def apply(zacl: ZACL): ACL = ACL(Id(zacl.getId), zacl.getPerms)

  private[zookeeper] def apply(zacl: java.util.List[ZACL]): Seq[ACL] =
    (Seq[ACL]() /: zacl.asScala) { case (acl, zacl) => acl :+ ACL(zacl)}

  private[zookeeper] def toZACL(acl: Seq[ACL]): java.util.List[ZACL] =
    (Seq[ZACL]() /: acl) { case (zacl, acl) => zacl :+ toZACL(acl) } asJava

  private[zookeeper] def toZACL(acl: ACL): ZACL = acl.asInstanceOf[ZACL]

  private object Permission {
    def apply(perms: Int): String = {
      (if ((perms & Read) == 0) "-" else "r") +
      (if ((perms & Write) == 0) "-" else "w") +
      (if ((perms & Create) == 0) "-" else "c") +
      (if ((perms & Delete) == 0) "-" else "d") +
      (if ((perms & Admin) == 0) "-" else "a")
    }

    def unapply(s: String): Option[Int] = {
      if (s == null) None
      else {
        val perms = (0 /: s) { case (p, c) =>
            if (c == 'r') p | Read
            else if (c == 'w') p | Write
            else if (c == 'c') p | Create
            else if (c == 'd') p | Delete
            else if (c == 'a') p | Admin
            else if (c == '*') p | All
            else return None
        }
        Some(perms)
      }
    }
  }
}
