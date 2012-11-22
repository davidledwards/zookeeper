package com.loopfor.zookeeper

import java.io.File
import java.net.InetSocketAddress
import java.util.UUID
import org.apache.zookeeper.server.{NIOServerCnxnFactory, ZooKeeperServer}

object ServerFixture {
  private val TMP_PATH = System getProperty "java.io.tmpdir"
  private val TICK_TIME = ZooKeeperServer.DEFAULT_TICK_TIME
  private val MAX_CONNECTIONS = 64

  def apply(): ZooKeeperServer = {
    val dir = new File(TMP_PATH, "zk-" + UUID.randomUUID())
    val server = new ZooKeeperServer(dir, dir, TICK_TIME)
    val con = new NIOServerCnxnFactory
    con.configure(new InetSocketAddress(0), MAX_CONNECTIONS)
    con.startup(server)
    server
  }
}
