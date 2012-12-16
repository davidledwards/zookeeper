package com.loopfor.zookeeper

import org.apache.zookeeper.server.ZooKeeperServer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.fixture.FunSuite
import java.util.UUID

abstract class ZookeeperSuite extends FunSuite with BeforeAndAfterAll {
  type FixtureParam = Path

  private val server: ZooKeeperServer = ServerFixture()
  implicit val zk: Zookeeper = ClientFixture(server.getClientPort)

  override protected def afterAll {
    zk.close()
    server.shutdown()
  }

  override protected def withFixture(test: OneArgTest) {
    val root = zk.sync.create("/test_", Array(), ACL.AnyoneAll, PersistentSequential)
    super.withFixture(test.toNoArgTest(Path(root)))
  }
}
