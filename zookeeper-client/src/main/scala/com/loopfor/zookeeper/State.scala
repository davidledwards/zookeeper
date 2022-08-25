/*
 * Copyright 2022 David Edwards
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

import org.apache.zookeeper.ZooKeeper.States

/**
 * The ''state'' of a ZooKeeper session.
 */
sealed trait State

/**
 * A state indicating that the session is in the process of associating.
 */
case object AssociatingState extends State

/**
 * A state indicating that session authentication failed.
 */
case object AuthenticationFailedState extends State

/**
 * A state indicating that the session has been closed.
 */
case object ClosedState extends State

/**
 * A state indicating that the session is connected.
 */
case object ConnectedState extends State

/**
 * A state indicating that the session is connected in read-only mode.
 */
case object ConnectedReadOnlyState extends State

/**
 * A state indicating that the session is in the process of connecting.
 */
case object ConnectingState extends State

/**
 * A state indicating that the session is not connected.
 */
case object NotConnectedState extends State

private[zookeeper] object State {
  private val states = Map(
    States.ASSOCIATING -> AssociatingState,
    States.AUTH_FAILED -> AuthenticationFailedState,
    States.CLOSED -> ClosedState,
    States.CONNECTED -> ConnectedState,
    States.CONNECTEDREADONLY -> ConnectedReadOnlyState,
    States.CONNECTING -> ConnectingState,
    States.NOT_CONNECTED -> NotConnectedState
  )

  def apply(state: States): State = states.get(state) match {
    case Some(s) => s
    case _ => new Unrecognized(state)
  }

  private class Unrecognized(state: States) extends State {
    override def toString: String = s"UnrecognizedState[${state}]"
  }
}