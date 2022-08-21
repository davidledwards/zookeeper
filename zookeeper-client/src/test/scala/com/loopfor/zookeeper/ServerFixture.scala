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

import java.io.File
import java.net.InetSocketAddress
import java.util.UUID
import org.apache.zookeeper.server.{NIOServerCnxnFactory, ZooKeeperServer}

object ServerFixture {
  private val TMP_PATH = System.getProperty("java.io.tmpdir")
  private val TICK_TIME = ZooKeeperServer.DEFAULT_TICK_TIME
  private val MAX_CONNECTIONS = 64

  def apply(): ZooKeeperServer = {
    System.setProperty("zookeeper.extendedTypesEnabled", "true")
    System.setProperty("zookeeper.maxCnxns", "1")
    val dir = new File(TMP_PATH, "zk-" + UUID.randomUUID())
    val server = new ZooKeeperServer(dir, dir, TICK_TIME)
    val con = new NIOServerCnxnFactory
    con.configure(new InetSocketAddress(0), MAX_CONNECTIONS)
    con.startup(server)
    server
  }
}
