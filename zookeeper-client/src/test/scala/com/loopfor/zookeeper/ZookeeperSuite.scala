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

import org.apache.zookeeper.server.ZooKeeperServer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.FixtureAnyFunSuite
import java.util.UUID
import org.scalatest.FixtureTestSuite

abstract class ZookeeperSuite extends FixtureAnyFunSuite with BeforeAndAfterAll {
  type FixtureParam = Path

  private val server: ZooKeeperServer = ServerFixture()
  implicit val zk: Zookeeper = ClientFixture(server.getClientPort)

  override protected def afterAll(): Unit = {
    zk.close()
    server.shutdown()
  }

  override protected def withFixture(test: OneArgTest) = {
    val root = zk.sync.create("/test_", Array(), ACL.AnyoneAll, PersistentSequential)
    super.withFixture(test.toNoArgTest(Path(root)))
  }
}
