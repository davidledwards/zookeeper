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

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.MILLISECONDS

class ZookeeperTest extends ZookeeperSuite {
  private val EmptyData = Array[Byte]()
  private val TestData = Array[Byte](0xD, 0xE, 0xA, 0xD, 0xB, 0xE, 0xE, 0xF)

  test("valid session") { _ =>
    val session = zk.session()
    assert(session !== null)

    // It is somewhat questionable to assert that the session is connected since the state
    // could change by the time this assertion is executed. However, given that the test is
    // executing under a controlled setup, it seemes reasonable to make this assumption.
    assert(session.state === ConnectedState)

    // Assumption is that session id is nonzero.
    val cred = session.credential
    assert(cred !== null)
    assert(cred.id !== 0)

    // Assumption is that session timeout is positive.
    val timeout = session.timeout
    assert(timeout !== null)
    assert(timeout.toNanos > 0)
  }

  test("persistent, non-recursive watch on node") { root =>
    // Use blocking queue to relay events from watcher.
    val event = new LinkedBlockingQueue[NodeEvent](1)
    val zkw = zk.sync.watch {
      case e: NodeEvent => event.put(e)
      case _ => // ignore state changes
    }

    // Sets persistent, non-recursive watch on root node.
    zkw.observe(root.path)

    // Verify that data change in root node triggers watch.
    zk.sync.set(root.path, TestData, None)
    var e = event.take()
    e match {
      case DataChanged(p) => assert(p === root.path)
      case _ => fail(e.toString)
    }

    // Verify that creation of child node triggers watch.
    zk.sync.create(root.resolve("child_0").path, TestData, ACL.AnyoneAll, Persistent)
    e = event.take()
    e match {
      case ChildrenChanged(p) => assert(p === root.path)
      case _ => fail(e.toString)
    }

    // Verify that data change in child node does not trigger watch.
    zk.sync.set(root.resolve("child_0").path, EmptyData, None)
    e = event.poll(10, MILLISECONDS)
    e match {
      case null => // expected
      case _ => fail(e.toString)
    }

    // Since watch is persistent, verify that creation of another child node triggers watch.
    zk.sync.create(root.resolve("child_1").path, TestData, ACL.AnyoneAll, Persistent)
    e = event.take()
    e match {
      case ChildrenChanged(p) => assert(p === root.path)
      case _ => fail(e.toString)
    }

    // Verify that deletion of child node triggers watch.
    zk.sync.delete(root.resolve("child_1").path, None)
    e = event.take()
    e match {
      case ChildrenChanged(p) => assert(p === root.path)
    }
  
    // Since watch is non-recursive, creation of grandchild must not trigger watch.
    zk.sync.create(root.resolve("child_0/grandchild").path, TestData, ACL.AnyoneAll, Persistent)
    e = event.poll(10, MILLISECONDS)
    e match {
      case null => // expected
      case _ => fail(e.toString)
    }

    // Verify that watch is removed.
    zkw.unobserve(root.path, PersistentObserver)
    e = event.take()
    e match {
      case PersistentWatchRemoved(p) => assert(p === root.path)
      case _ => fail(e.toString)
    }

    // Verify exception if watch no longer associated with node.
    intercept[NoWatcherException] {
      zkw.unobserve(root.path, PersistentObserver)
    }
  }

  test("persistent, recursive watch on node") { root =>
    // Use blocking queue to relay events from watcher.
    val event = new LinkedBlockingQueue[NodeEvent](1)
    val zkw = zk.sync.watch {
      case e: NodeEvent => event.put(e)
      case _ => // ignore state changes
    }

    // Sets persistent, recursive watch on root node.
    zkw.observe(root.path, true)

    // Verify that data change in root node triggers watch.
    zk.sync.set(root.path, TestData, None)
    var e = event.take()
    e match {
      case DataChanged(p) => assert(p === root.path)
      case _ => fail(e.toString)
    }

    // Verify that creation of child node triggers watch.
    var path = root.resolve("child_0").path
    zk.sync.create(path, TestData, ACL.AnyoneAll, Persistent)
    e = event.take()
    e match {
      case Created(p) => assert(p === path)
      case _ => fail(e.toString)
    }

    // Since watch is recursive, verify that data change in child node triggers watch.
    zk.sync.set(path, EmptyData, None)
    e = event.take()
    e match {
      case DataChanged(p) => assert(p === path)
      case _ => fail(e.toString)
    }

    // Verify that deletion of child node triggers watch.
    zk.sync.delete(path, None)
    e = event.take()
    e match {
      case Deleted(p) => assert(p === path)
      case _ => fail(e.toString)
    }

    // Since watch is persistent, verify that creation of another child node triggers watch.
    path = root.resolve("child_1").path
    zk.sync.create(path, TestData, ACL.AnyoneAll, Persistent)
    e = event.take()
    e match {
      case Created(p) => assert(p === path)
      case _ => fail(e.toString)
    }

    // Since watch is recursive, creation of grandchild must trigger watch.
    path = root.resolve("child_1/grandchild").path
    zk.sync.create(path, TestData, ACL.AnyoneAll, Persistent)
    e = event.take()
    e match {
      case Created(p) => assert(p === path)
      case _ => fail(e.toString)
    }

    // Verify that data changed in grandchild triggers watch.
    zk.sync.set(path, EmptyData, None)
    e = event.take()
    e match {
      case DataChanged(p) => assert(p === path)
      case _ => fail(e.toString)
    }

    // Verify that deletion of grandchild triggers watch.
    zk.sync.delete(path, None)
    e = event.take()
    e match {
      case Deleted(p) => assert(p === path)
      case _ => fail(e.toString)
    }

    // Verify that watch is removed.
    zkw.unobserve(root.path, PersistentRecursiveObserver)
    e = event.take()
    e match {
      case PersistentWatchRemoved(p) => assert(p === root.path)
      case _ => fail(e.toString)
    }

    // Verify exception if watch no longer associated with node.
    intercept[NoWatcherException] {
      zkw.unobserve(root.path, PersistentRecursiveObserver)
    }
  }
}
