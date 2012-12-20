package com.loopfor.zookeeper.cli

import com.loopfor.zookeeper._
import java.util.concurrent.atomic.AtomicReference

object ConfigCommand {
  val Usage = """usage: config

  Shows connection information and session state.

  Possible session states, which indicate connectedness to the ZooKeeper
  cluster, include:
    * Disconnected
    * Connected
    * ConnectedReadOnly
    * AuthenticationFailed
    * Authenticated
    * Expired

  In general, session state may change between connected and disconnected
  because of temporary loss of connectivity, but once expired, the CLI must
  be stopped before a new session can be established.
"""

  def apply(config: Configuration, state: AtomicReference[StateEvent]) = new Command {
    def apply(cmd: String, args: Seq[String], context: Path) = {
      println("servers: " + (config.servers map { s => s.getHostName + ":" + s.getPort } mkString ","))
      println("path: " + Path("/").resolve(config.path).normalize)
      println("timeout: " + config.timeout)
      println("session: " + state.get)
      context
    }
  }
}
