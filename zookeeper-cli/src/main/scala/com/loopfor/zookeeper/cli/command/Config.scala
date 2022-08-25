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
package com.loopfor.zookeeper.cli.command

import com.loopfor.zookeeper._
import com.loopfor.zookeeper.cli.Level
import java.io.File

object Config {
  val Usage = """usage: config

  Shows connection information and session state.

  Possible session states, which indicate connectedness to the ZooKeeper
  cluster, include:
    * associating
    * connecting
    * connected
    * connected (read-only)
    * not connected
    * closed
    * authentication failed
"""

  def command(config: Configuration, log: Option[(File, Level)], zk: Zookeeper) = new CommandProcessor {
    def apply(cmd: String, args: Seq[String], context: Path) = {
      val servers = config.servers.map { s => s.getHostName + ":" + s.getPort } mkString(",")
      val path = Path("/").resolve(config.path).normalize
      val logfile = log match {
        case Some((file, _)) => file.getAbsolutePath
        case _ => "/dev/null"
      }
      println(s"servers: $servers")
      println(s"path: $path")
      println(s"timeout: ${config.timeout}")
      val session = zk.session()
      val state = session.state match {
        case AssociatingState => "associating"
        case AuthenticationFailedState => "authentication failed"
        case ClosedState => "closed"
        case ConnectedState => "connected"
        case ConnectedReadOnlyState => "connected (read-only)"
        case ConnectingState => "connecting"
        case NotConnectedState => "not connected"
        case s => s.toString
      }
      println(s"session: ${state}")
      println(s"id: ${session.credential.id}")
      println(s"log: $logfile")
      context
    }
  }
}
