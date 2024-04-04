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

import org.apache.zookeeper.Watcher.Event.{EventType, KeeperState}
import org.scalatest.funsuite.AnyFunSuite

class EventTest extends AnyFunSuite {
  val PATH = "/test"
  val UNRECOGNIZED_STATE = 123456
  val UNRECOGNIZED_EVENT = 123456

  test("match StateEvents") {
    (Disconnected: StateEvent) match {
      case Disconnected => ()
    }
    (Connected: StateEvent) match {
      case Connected => ()
    }
    (AuthenticationFailed: StateEvent) match {
      case AuthenticationFailed => ()
    }
    (ConnectedReadOnly: StateEvent) match {
      case ConnectedReadOnly => ()
    }
    (Authenticated: StateEvent) match {
      case Authenticated => ()
    }
    (Expired: StateEvent) match {
      case Expired => ()
    }
    (Closed: StateEvent) match {
      case Closed => ()
    }
  }

  test("match NodeEvents") {
    Created(PATH) match {
      case Created(p) => assert(p === PATH)
    }
    Deleted(PATH) match {
      case Deleted(p) => assert(p === PATH)
    }
    DataChanged(PATH) match {
      case DataChanged(p) => assert(p === PATH)
    }
    ChildrenChanged(PATH) match {
      case ChildrenChanged(p) => assert(p === PATH)
    }
    ChildWatchRemoved(PATH) match {
      case ChildWatchRemoved(p) => assert(p === PATH)
    }
    DataWatchRemoved(PATH) match {
      case DataWatchRemoved(p) => assert(p === PATH)
    }
  }

  test("create StateEvents from underlying code") {
    StateEvent(KeeperState.Disconnected) match {
      case Disconnected => ()
    }
    StateEvent(KeeperState.SyncConnected) match {
      case Connected => ()
    }
    StateEvent(KeeperState.AuthFailed) match {
      case AuthenticationFailed => ()
    }
    StateEvent(KeeperState.ConnectedReadOnly) match {
      case ConnectedReadOnly => ()
    }
    StateEvent(KeeperState.SaslAuthenticated) match {
      case Authenticated => ()
    }
    StateEvent(KeeperState.Expired) match {
      case Expired => ()
    }
    StateEvent(KeeperState.Closed) match {
      case Closed => ()
    }
  }

  test("create NodeEvents from underlying code") {
    NodeEvent(EventType.NodeCreated, PATH) match {
      case Created(p) if (p == PATH) => ()
    }
    NodeEvent(EventType.NodeDeleted, PATH) match {
      case Deleted(p) if (p == PATH) => ()
    }
    NodeEvent(EventType.NodeDataChanged, PATH) match {
      case DataChanged(p) if (p == PATH) => ()
    }
    NodeEvent(EventType.NodeChildrenChanged, PATH) match {
      case ChildrenChanged(p) if (p == PATH) => ()
    }
    NodeEvent(EventType.ChildWatchRemoved, PATH) match {
      case ChildWatchRemoved(p) if (p == PATH) => ()
    }
    NodeEvent(EventType.DataWatchRemoved, PATH) match {
      case DataWatchRemoved(p) if (p == PATH) => ()
    }
    NodeEvent(EventType.PersistentWatchRemoved, PATH) match {
      case PersistentWatchRemoved(p) if (p == PATH) => ()
    }
  }
}
