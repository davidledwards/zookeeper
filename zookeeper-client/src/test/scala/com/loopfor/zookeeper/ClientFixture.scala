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

import java.util.concurrent.SynchronousQueue
import java.util.concurrent.TimeUnit
import scala.language._

object ClientFixture {
  private val CONNECT_TIMEOUT = 5000L

  def apply(port: Int): Zookeeper = {
    // Used to synchronize on completion of ZK server connection.
    val state = new SynchronousQueue[StateEvent]()

    // Connect to ZK server.
    val config = Configuration(("localhost", port) :: Nil).withWatcher {
      (event, _) => state.put(event)
    }
    val zk = Zookeeper(config)

    // Wait for ZK server connection to complete with announcement of first event,
    // which is presumed to be successful.
    state.poll(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS) match {
      case null =>
        throw new IllegalStateException("connection to ZooKeeper server exceeded ${CONNECT_TIMEOUT} milliseconds")
      case Connected =>
        zk
      case e =>
        throw new IllegalStateException("${e}: unexpected event during connection to ZooKeeper")
    }
  }
}
