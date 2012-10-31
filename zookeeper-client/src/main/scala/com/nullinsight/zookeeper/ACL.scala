package com.nullinsight.zookeeper

import org.apache.zookeeper.data.{ACL => ZACL, Id => ZId}
import org.apache.zookeeper.ZooDefs._
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
   * An identity representing ''anyone''.
   */
  val Anyone: Id = Id(Ids.ANYONE_ID_UNSAFE)

  /**
   * Constructs a new identity.
   * 
   * @param scheme a string representing the scheme
   * @param id a string representing the id
   * @return an identity with the given `scheme` and `id`
   */
  def apply(scheme: String, id: String): Id = new Impl(scheme, id)

  private[zookeeper] def apply(zid: ZId): Id = new Impl(zid.getScheme, zid.getId)

  /**
   * Used in pattern matching to deconstruct an identity.
   * 
   * @param id selector value
   * @return a `Some` containing `scheme` and `id` if the selector value is not `null`, otherwise `None`
   */
  def unapply(id: Id): Option[(String, String)] =
    if (id == null) None else Some(id.scheme, id.id)

  private[zookeeper] def toId(id: Id): ZId = id.asInstanceOf[ZId]

  private class Impl(val scheme: String, val id: String) extends ZId(scheme, id) with Id {
    def permit(permission: Int): ACL = ACL(this, permission)

    override def toString: String = "Id(" + scheme + "," + id + ")"
  }
}

/**
 * Constructs and deconstructs [[ACL]] values.
 * 
 * The permissions assigned to an ACL are constructed by performing a bitwise union of individual permission attributes:
 * [[Read]], [[Write]], [[Create]], [[Delete]], [[Admin]]. In addition, the [[All]] permission encompasses all of these
 * attributes.
 * 
 * Several commonly used ACL values have been predefined for sake of convenience: [[EveryoneAll]], [[EveryoneRead]],
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
   * An ACL in which everyone is granted all permissions.
   */
  val EveryoneAll: Seq[ACL] = ACL(Ids.OPEN_ACL_UNSAFE)

  /**
   * An ACL in which everyone is granted [[Read]] permission only.
   */
  val EveryoneRead: Seq[ACL] = ACL(Ids.READ_ACL_UNSAFE)

  /**
   * An ACL in which the creator is granted all permissions.
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
   * Used in pattern matching to deconstruct an ACL
   * 
   * @param acl selector value
   * @return a `Some` containing `id` and `permission` if the selector value is not `null`, otherwise `None`
   */
  def unapply(acl: ACL): Option[(Id, Int)] =
    if (acl == null) None else Some(acl.id, acl.permission)

  private[zookeeper] def apply(zacl: ZACL): ACL = new Impl(Id(zacl.getId), zacl.getPerms)

  private[zookeeper] def apply(zacl: java.util.List[ZACL]): Seq[ACL] =
    (Seq[ACL]() /: zacl.asScala) { case (acl, zacl) => acl :+ ACL(zacl)}

  private[zookeeper] def toZACL(acl: Seq[ACL]): java.util.List[ZACL] =
    (Seq[ZACL]() /: acl) { case (zacl, acl) => zacl :+ toZACL(acl) } asJava

  private[zookeeper] def toZACL(acl: ACL): ZACL = acl.asInstanceOf[ZACL]

  implicit def tupleToIdentity(id: (String, String)): Id = Id(id._1, id._2)

  private class Impl(val id: Id, val permission: Int) extends ZACL(permission, Id.toId(id)) with ACL {
    override def toString: String = "ACL(" + id + "," + permission + ")"
  }
}
