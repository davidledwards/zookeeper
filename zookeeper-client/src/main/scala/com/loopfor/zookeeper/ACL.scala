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
import org.apache.zookeeper.data.{ACL => ZACL, Id => ZId}
import scala.collection.JavaConverters._
import scala.language._

/**
 * An ''identity'' associated with an [[ACL]].
 */
sealed trait Id {
  /**
   * Returns the scheme.
   * 
   * @return a string representing the scheme
   */
  def scheme: String

  /**
   * Returns the id.
   * 
   * @return a string representing the id
   */
  def id: String

  /**
   * Returns an ACL for this identity using the given permission.
   * 
   * @param permission the bitwise union of permission that apply to the ACL
   * @return an ACL with the given `permission`
   */
  def permit(permission: Int): ACL
}

/**
 * An ''access control list'' assignable to a ZooKeeper node.
 */
sealed trait ACL {
  /**
   * Returns the identity associated with the ACL
   * 
   * @return the identity to which permissions apply
   */
  def id: Id

  /**
   * Returns the permissions that apply to this ACL.
   * 
   * @see [[ACL$#Read Read]], [[ACL$#Write Write]], [[ACL$#Create Create]], [[ACL$#Delete Delete]], [[ACL$#Admin Admin]],
   * [[ACL$#All All]]
   * 
   * @return the bitwise union of permissions that apply to this ACL
   */
  def permission: Int
}

/**
 * Constructs and deconstructs [[Id]] values.
 */
object Id {
  /**
   * An identity whose scheme is "`world`" and id is "`anyone`".
   */
  val Anyone: Id = Id(Ids.ANYONE_ID_UNSAFE)

  /**
   * An identity whose scheme is "`auth`" and id is "".
   * 
   * This is a special identity, usable only while setting ACLs, that is substituted with the identities used during client
   * authentication.
   */
  val Creator: Id = Id(Ids.AUTH_IDS)

  /**
   * Constructs a new identity.
   * 
   * @param scheme a string representing the scheme
   * @param id a string representing the id
   * @return an identity with the given `scheme` and `id`
   */
  def apply(scheme: String, id: String): Id = new Impl(scheme, id)

  /**
   * Constructs a new identity from the input string `s`.
   * 
   * @param s a string representing the identity
   * @return the identity in `s` if it conforms to the specified syntax
   * 
   * @throws IllegalArgumentException if `s` cannot be parsed
   * 
   * @see [[parse]]
   */
  def apply(s: String): Id = parse(s) match {
    case Some(id) => id
    case _ => throw new IllegalArgumentException(s + ": invalid syntax")
  }

  private[zookeeper] def apply(zid: ZId): Id = new Impl(zid.getScheme, zid.getId)

  /**
   * Used in pattern matching to deconstruct an identity.
   * 
   * @param id selector value
   * @return a `Some` containing `scheme` and `id` if the selector value is not `null`, otherwise `None`
   */
  def unapply(id: Id): Option[(String, String)] =
    if (id == null) None else Some(id.scheme, id.id)

  /**
   * Parses the identity in the input string `s`.
   * 
   * The syntax of `s` is ''scheme''`:`''id'', where `id` and `scheme` may be empty.
   * 
   * @param s a string representing the identity
   * @return a `Some` containing the identity in `s` if it conforms to the specified syntax, otherwise `None`
   */
  def parse(s: String): Option[Id] = (s indexOf ':') match {
    case -1 => None
    case n => Some(Id(s take n, s drop n + 1))
  }

  private[zookeeper] def toId(id: Id): ZId = id.asInstanceOf[ZId]

  private class Impl(val scheme: String, val id: String) extends ZId(scheme, id) with Id {
    def permit(permission: Int): ACL = ACL(this, permission)

    override def equals(that: Any): Boolean = that match {
      case _that: Id => _that.scheme == scheme && _that.id == id
      case _ => false
    }

    override def hashCode: Int = scheme.hashCode * 37 + id.hashCode

    override def toString: String = scheme + ":" + id
  }
}

/**
 * Constructs and deconstructs [[ACL]] values.
 * 
 * The permissions assigned to an ACL are constructed by performing a bitwise union of individual permission attributes:
 * [[Read]], [[Write]], [[Create]], [[Delete]], [[Admin]]. In addition, the [[All]] permission encompasses all of these
 * attributes.
 * 
 * Several commonly used ACL values have been predefined for sake of convenience: [[AnyoneAll]], [[AnyoneRead]],
 * [[CreatorAll]].
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
   * Constructs a new ACL using the given identity and permission.
   * 
   * @param id the identity to which permissions apply
   * @param permission the bitwise union of permissions that apply to the ACL
   * @return an ACL with the given `id` and `permission`
   */
  def apply(id: Id, permission: Int): ACL = new Impl(id, permission)

  /**
   * Constructs a new ACL using the given identity and permission.
   * 
   * @param scheme a string representing the scheme
   * @param id a string representing the id
   * @param permission the bitwise union of permissions that apply to the ACL
   * @return an ACL with the given `scheme`, `id` and `permission`, where `scheme` and `id` collectively represent the identity
   */
  def apply(scheme: String, id: String, permission: Int): ACL = new Impl(Id(scheme, id), permission)

  /**
   * Constructs a new ACL from the input string `s`.
   * 
   * @param s a string representing the ACL
   * @return the ACL in `s` if it conforms to the specific syntax
   * 
   * @see [[parse]]
   */
  def apply(s: String): ACL = parse(s) match {
    case Some(acl) => acl
    case _ => throw new IllegalArgumentException(s + ": invalid syntax")
  }

  /**
   * Used in pattern matching to deconstruct an ACL
   * 
   * @param acl selector value
   * @return a `Some` containing `id` and `permission` if the selector value is not `null`, otherwise `None`
   */
  def unapply(acl: ACL): Option[(Id, Int)] =
    if (acl == null) None else Some(acl.id, acl.permission)

  /**
   * Parses the ACL in the input string `s`.
   * 
   * The syntax of `s` is ''scheme''`:`''id''`=`[`rwcda*`], where `scheme` and `id` may be empty and any of `rwcda*` may be
   * repeated zero or more times.
   * 
   * @param s a string representing the ACL
   * @return a `Some` containing the ACL in `s` if it conforms to the specified syntax, otherwise `None`
   */
  def parse(s: String): Option[ACL] = (s indexOf '=') match {
    case -1 => None
    case n => Id parse (s take n) match {
      case Some(id) => (s drop n + 1) match {
        case Permission(p) => Some(ACL(id, p))
        case _ => None
      }
      case _ => None
    }
  }

  private[zookeeper] def apply(zacl: ZACL): ACL = new Impl(Id(zacl.getId), zacl.getPerms)

  private[zookeeper] def apply(zacl: java.util.List[ZACL]): Seq[ACL] =
    (Seq[ACL]() /: zacl.asScala) { case (acl, zacl) => acl :+ ACL(zacl)}

  private[zookeeper] def toZACL(acl: Seq[ACL]): java.util.List[ZACL] =
    (Seq[ZACL]() /: acl) { case (zacl, acl) => zacl :+ toZACL(acl) } asJava

  private[zookeeper] def toZACL(acl: ACL): ZACL = acl.asInstanceOf[ZACL]

  implicit def tupleToIdentity(id: (String, String)): Id = Id(id._1, id._2)

  private class Impl(val id: Id, val permission: Int) extends ZACL(permission, Id.toId(id)) with ACL {
    override def equals(that: Any): Boolean = that match {
      case _that: ACL => _that.id == id && _that.permission == permission
      case _ => false
    }

    override def hashCode: Int = id.hashCode * 37 + permission

    override def toString: String = id + "=" + Permission(permission)
  }

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
