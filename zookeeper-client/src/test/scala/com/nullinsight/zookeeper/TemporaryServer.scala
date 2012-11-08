package com.nullinsight.zookeeper

import org.apache.zookeeper.server.ZooKeeperServer
import org.apache.zookeeper.server.NIOServerCnxnFactory
import java.net.InetSocketAddress
import java.io.File
import java.util.UUID
import scala.concurrent.duration._
import scala.language._

object TemporaryServer {
  val TMP_PATH = System getProperty "java.io.tmpdir"

  def apply(port: Int, tickTime: Duration = 2000 millis): ZooKeeperServer = {
    val dir = new File(TMP_PATH, "zk-" + UUID.randomUUID())
    dir.deleteOnExit()
    val server = new ZooKeeperServer(dir, dir, tickTime.toMillis.asInstanceOf[Int])
    val con = new NIOServerCnxnFactory
    con.configure(new InetSocketAddress(port), 5000)
    con.startup(server)
    server
  }
}