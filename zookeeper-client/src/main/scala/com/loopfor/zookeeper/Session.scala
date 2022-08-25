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

import org.apache.zookeeper.ZooKeeper
import scala.concurrent.duration._
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
   * Returns the current state of this session.
   *
   * @return the current state of this session
   */
  def state: State

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
    val state: State = State(zk.getState)
    val credential: Credential = Credential(zk)
    val timeout: Duration = zk.getSessionTimeout.millis

    override def toString: String = s"Session(state=${state},credential=${credential},timeout=${timeout})"
  }
}
