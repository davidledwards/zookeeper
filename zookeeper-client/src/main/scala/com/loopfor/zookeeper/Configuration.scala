/*
 * Copyright 2013 David Edwards
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

import java.net.InetSocketAddress
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language._

/**
 * A client ''configuration'' for connecting to a ZooKeeper cluster.
 */
trait Configuration {
  /**
   * Returns a sequence of server endpoints in this cluster.
   * 
   * These endpoints do not necessarily need to reflect all servers in the cluster, only those to which the client desires to
   * make a possible connection.
   * 
   * @return a sequence of socket addresses
   */
  def servers: Seq[InetSocketAddress]

  /**
   * Returns an optional root path.
   * 
   * @return the root path if specified, otherwise an empty string
   */
  def path: String

  /**
   * Returns the period of time after which the session is expired.
   * 
   * Note that this value is only a proposal and that ZooKeeper may use a different timeout.
   * 
   * @return the session timeout
   */
  def timeout: Duration

  /**
   * Returns an optional function that is called when state changes occur in the session.
   * 
   * Note that a new session may be established if the client disconnects and reconnects to ZooKeeper, particularly with
   * respect to the session `id` and `password`.
   * 
   * @return a function that receives session state changes, otherwise `null` if not specified
   */
  def watcher: (StateEvent, Session) => Unit

  /**
   * Returns `true` if the client will allow read-only connections to ZooKeeper under conditions in which a majority of
   * servers cannot be established.
   * 
   * @return `true` if read-only is allowed, `false` otherwise
   */
  def allowReadOnly: Boolean

  /**
   * Returns an optional execution context to which asynchronous tasks are submitted.
   * 
   * Use this feature when an application container prefers to manage the execution context.
   * 
   * @return an execution context if specified, otherwise `null`
   */
  def exec: ExecutionContext
}

/**
 * Constructs and deconstructs [[Configuration]] values.
 * 
 * A configuration is constructed by first specifying ''required'' attributes via `Configuration()` and then attaching
 * ''optional'' attributes as necessary. A set of implicit methods conveniently convert between instances of [[Configuration]]
 * and [[Configuration.Builder Builder]].
 * 
 * Example:
 * {{{
 * val config = Configuration {
 *   ("foo.server.com", 2181) :: ("bar.server.com", 2181) :: Nil
 * } withTimeout {
 *   60 seconds
 * } withWatcher { (event, session) =>
 *   // ...
 * }
 * }}}
 * 
 * The type of `config` above is [[Configuration.Builder Builder]] since an implicit conversion occurred when attaching
 * optional attributes using the various `with` methods. An explicit conversion back to [[Configuration]], which can be
 * accomplished using `build()`, is unnecessary since another implicit will perform this function automatically.
 */
object Configuration {
  /**
   * Constructs a new default configuration using the given servers.
   * 
   * @param servers sequence of socket addresses corresponding to server endpoints
   * @return a default configuration with the given `servers`
   */
  def apply(servers: Seq[InetSocketAddress]): Configuration = new Builder(servers) build

  /**
   * Used in pattern matching to deconstruct a configuration.
   * 
   * @param config selector value
   * @return a `Some` containing configuration attributes if the selector value is not `null`, otherwise `None`
   */
  def unapply(config: Configuration): Option[(Seq[InetSocketAddress], String, Duration, (StateEvent, Session) => Unit,
        Boolean, ExecutionContext)] = {
    if (config == null)
      None
    else
      Some(config.servers, config.path, config.timeout, config.watcher, config.allowReadOnly, config.exec)
  }

  private class Impl(
        val servers: Seq[InetSocketAddress],
        val path: String,
        val timeout: Duration,
        val watcher: (StateEvent, Session) => Unit,
        val allowReadOnly: Boolean,
        val exec: ExecutionContext) extends Configuration

  class Builder private (
        servers: Seq[InetSocketAddress],
        path: String,
        timeout: Duration,
        watcher: (StateEvent, Session) => Unit,
        allowReadOnly: Boolean,
        exec: ExecutionContext) {
    private[Configuration] def this(servers: Seq[InetSocketAddress]) {
      this(servers, "", 60 seconds, null, false, null)
    }

    private[Configuration] def this(config: Configuration) {
      this(config.servers, config.path, config.timeout, config.watcher, config.allowReadOnly, config.exec)
    }

    def withPath(path: String): Builder =
      new Builder(servers, path, timeout, watcher, allowReadOnly, exec)

    def withTimeout(timeout: Duration): Builder =
      new Builder(servers, path, timeout, watcher, allowReadOnly, exec)

    def withWatcher(watcher: (StateEvent, Session) => Unit): Builder =
      new Builder(servers, path, timeout, watcher, allowReadOnly, exec)

    def withAllowReadOnly(allowReadOnly: Boolean): Builder =
      new Builder(servers, path, timeout, watcher, allowReadOnly, exec)

    def withExec(exec: ExecutionContext): Builder =
      new Builder(servers, path, timeout, watcher, allowReadOnly, exec)

    def build(): Configuration = new Impl(servers, path, timeout, watcher, allowReadOnly, exec)
  }

  implicit def configToBuilder(config: Configuration): Builder = new Builder(config)
  implicit def builderToConfig(builder: Builder): Configuration = builder.build
}
