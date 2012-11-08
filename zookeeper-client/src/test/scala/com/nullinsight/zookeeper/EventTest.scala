package com.nullinsight.zookeeper

import org.scalatest.FunSuite
import org.apache.zookeeper.Watcher.Event.{EventType, KeeperState}

class EventTest extends FunSuite {
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
  }

  test("match NodeEvents") {
    Created(PATH) match {
      case Created(p) if (p == PATH) => ()
    }
    Deleted(PATH) match {
      case Deleted(p) if (p == PATH) => ()
    }
    DataChanged(PATH) match {
      case DataChanged(p) if (p == PATH) => ()
    }
    ChildrenChanged(PATH) match {
      case ChildrenChanged(p) if (p == PATH) => ()
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
  }
}
