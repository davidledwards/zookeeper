package com.nullinsight.zookeeper

import org.apache.zookeeper.ZooKeeper
import scala.concurrent.util._
import scala.concurrent.util.duration._
import scala.language._

/**
 * A ''session'' established with ZooKeeper.
 * 
 * Note that a session, particularly the life cycle, is managed by ZooKeeper servers, not the client. Under normal
 * circumstances in which the client explicitly disconnects from the ZooKeeper cluster, its session is automatically expired.
 * However, in cases where the client does not properly disconnect, ZooKeeper retains the session for a period of time defined
 * by `timeout`.
 */
trait Session {
  /**
   * Returns the credential associated with this session.
   * 
   * @return the credential associated with this session
   */
  def credential: Credential

  /**
   * Returns the period of time after which the session is expired.
   * 
   * This value is ultimately decided by ZooKeeper, and therefore, may not be equal to the `timeout` specified in
   * [[Configuration]].
   * 
   * @return the period of time after which the session is expired
   */
  def timeout: Duration
}

/**
 * Constructs and deconstructs [[Session]] values.
 */
object Session {
  /**
   * Used in pattern matching to deconstruct a session.
   * 
   * @param session selector value
   * @return a `Some` containing `credential` and `timeout` if the selector value is not `null`, otherwise `None`
   */
  def unapply(session: Session): Option[(Credential, Duration)] =
    if (session == null) None else Some(session.credential, session.timeout)

  private[zookeeper] def apply(zk: ZooKeeper): Session = new Session {
    val credential: Credential = Credential(zk)
    val timeout: Duration = zk.getSessionTimeout millis

    override def toString: String = "Session(credential=" + credential + ",timeout=" + timeout + ")"
  }
}
