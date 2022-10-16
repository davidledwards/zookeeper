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
package com.loopfor.zookeeper

import java.net.{InetAddress, Inet4Address, UnknownHostException}
import org.apache.zookeeper.ZooDefs.Ids
import org.apache.zookeeper.data.{Id => ZId}
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

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
  def permit(permission: Int): ACL = ACL(this, permission)

  private[zookeeper] def zid: ZId
}

abstract class BaseId(val scheme: String, val id: String) extends Id {
  private[zookeeper] val zid = new ZId(scheme, id)
}

/**
 * An identity whose [[scheme]] is `"world"` and [[id]] is `"anyone"`.
 *
 * @see [[https://zookeeper.apache.org/doc/r3.5.6/zookeeperProgrammers.html#sc_BuiltinACLSchemes Schemes]]
 */
case object WorldId extends BaseId("world", "anyone")

/**
 * An identity whose [[scheme]] is `"auth"` and [[id]] is `""`.
 *
 * @see [[https://zookeeper.apache.org/doc/r3.5.6/zookeeperProgrammers.html#sc_BuiltinACLSchemes Schemes]]
 */
case object AuthId extends BaseId("auth", "")

/**
 * An identity whose [[scheme]] is `"digest"` and [[id]] is equal to `"[[username]]:[[password]]"`.
 *
 * @param username a username, which may be empty
 * @param password a cleartext password, which may be empty
 *
 * @see [[https://zookeeper.apache.org/doc/r3.5.6/zookeeperProgrammers.html#sc_BuiltinACLSchemes Schemes]]
 */
case class DigestId(username: String, password: String) extends BaseId("digest", username + ":" + password)

/**
 * An identity whose [[scheme]] is `"host"` and [[id]] is equal to `"[[domain]]"`.
 *
 * @param domain an internet domain name
 *
 * @see [[https://zookeeper.apache.org/doc/r3.5.6/zookeeperProgrammers.html#sc_BuiltinACLSchemes Schemes]]
 */
case class HostId(domain: String) extends BaseId("host", domain)

/**
 * An identity whose [[scheme]] is `"ip"` and [[id]] is equal to `"[[addr]]/[[prefix]]"`.
 *
 * @param addr an IPv4 or IPv6 address in dotted decimal form
 * @param prefix the network prefix in bits, a range of [`0`,`32`] for IPv4 and [`0`,`128`] for IPv6
 *
 * @see [[https://zookeeper.apache.org/doc/r3.5.6/zookeeperProgrammers.html#sc_BuiltinACLSchemes Schemes]]
 */
case class IpId(addr: String, prefix: Int) extends BaseId("ip", addr + "/" + prefix)

/**
 * Constructs and deconstructs [[Id]] values.
 *
 * An [[Id]] is composed of two parts: a [[Id#scheme scheme]] and an [[Id#id id]]. There exists only a finite set of
 * ''schemes'' recognized by ZooKeeper, which are noted below. The acceptable form of ''id'' depends on the chosen
 * scheme.
 *
 * '''Schemes'''
 *
 * `world` -- ''id'' must be `"anyone"`.
 *
 * `auth` -- ''id'' must be `""` (empty string).
 *
 * `digest` -- ''id'' must be of the form `"''username'':''password''"`.
 *
 * `host` -- ''id'' should be an Internet domain name.
 *
 * `ip` -- ''id'' must be a valid IPv4 or IPv6 address with an optional network prefix, variations of which follow:
 *  - `"''addr''"` where prefix is assumed to be `32` and `128` for IPv4 and IPv6, respectively.
 *  - `"''addr''/''prefix''"` where prefix is in the range `[0,32]` and `[0,128]` for IPv4 and IPv6, respectively.
 *
 * @see [[https://zookeeper.apache.org/doc/r3.5.6/zookeeperProgrammers.html#sc_BuiltinACLSchemes Schemes]]
 */
object Id {
  /**
   * An identity whose scheme is "`world`" and id is "`anyone`".
   *
   * Equivalent to [[WorldId]].
   */
  val Anyone: Id = Id(Ids.ANYONE_ID_UNSAFE)

  /**
   * An identity whose scheme is "`auth`" and id is "".
   *
   * This is a special identity, usable only while setting ACLs, that is substituted with the identities used during client
   * authentication.
   *
   * Equivalent to [[AuthId]].
   */
  val Creator: Id = Id(Ids.AUTH_IDS)

  /**
   * Constructs a new identity.
   *
   * @param scheme a string representing the scheme
   * @param id a string representing the id
   * @return an identity with the given `scheme` and `id`
   *
   * @throws IllegalArgumentException if a valid identity cannot be constructed from `scheme` and `id`
   *
   * @see [[parse]]
   */
  def apply(scheme: String, id: String): Id = apply(scheme + ":" + id)

  /**
   * Constructs a new identity from the input string `s`.
   *
   * @param s a string representing the identity
   * @return the identity in `s` if it conforms to the proper syntax
   *
   * @throws IllegalArgumentException if `s` does not conform to the proper syntax
   *
   * @see [[parse]]
   */
  def apply(s: String): Id = parse(s) match {
    case Success(id) => id
    case Failure(e) => throw e
  }

  private[zookeeper] def apply(zid: ZId): Id = apply(zid.getScheme, zid.getId)

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
   * The syntax of `s` is `"''scheme'':''id''"`, where the `:` delimiter may be omitted if ''id'' is not required for the
   * given ''scheme''.
   *
   * @param s a string representing the identity
   * @return a `Success` containing the identity in `s` if it conforms to the proper syntax, otherwise a `Failure`
   * containing the offending exception
   */
  def parse(s: String): Try[Id] = Try {
    def error(message: String): Nothing =
      throw new IllegalArgumentException(s"${s}: ${message}")

    s.split(":", 2) match {
      case Array("world", id) =>
        if (id == "anyone") WorldId
        else if (id == "") error("missing id 'anyone'")
        else error("id not recognized")
      case Array("world") =>
        error("missing id 'anyone'")
      case Array("auth", id) =>
        if (id == "") AuthId else error("id must be empty")
      case Array("auth") =>
        AuthId
      case Array("digest", id) =>
        id.split(":", 2) match {
          case Array(username, password) => DigestId(username, password)
          case _ => error("missing password")
        }
      case Array("digest") =>
        error("missing username:password")
      case Array("host", id) =>
        if (id == "") error("missing domain") else HostId(id)
      case Array("host") =>
        error("missing domain")
      case Array("ip", id) =>
        if (id == "") error("missing address")
        else {
          def verify(addr: String, prefix: Option[String]) = {
            val a = try InetAddress.getByName(addr) catch {
              case _: UnknownHostException => error("unknown host")
            }
            val MaxPrefix = if (a.isInstanceOf[Inet4Address]) 32 else 128
            val p = prefix match {
              case Some(_prefix) =>
                try {
                  val p = _prefix.toInt
                  if (p < 0 || p > MaxPrefix) error("invalid prefix")
                  p
                } catch {
                  case _: NumberFormatException =>
                    if (_prefix == "") error("missing prefix")
                    else error("invalid prefix")
                }
              case None => MaxPrefix
            }
            (a.getHostAddress, p)
          }
          id.split("/", 2) match {
            case Array(addr, prefix) =>
              val (a, p) = verify(addr, Some(prefix))
              IpId(a, p)
            case Array(addr) =>
              val (a, p) = verify(addr, None)
              IpId(a, p)
          }
        }
      case Array("ip") =>
        error("missing address")
      case _ =>
        error("scheme not recognized")
    }
  }

  implicit def tupleToIdentity(id: (String, String)): Id = Id(id._1, id._2)
}
