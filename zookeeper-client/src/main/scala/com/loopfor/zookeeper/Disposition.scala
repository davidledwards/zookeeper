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

import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.CreateMode._
import scala.concurrent.duration.Duration

/**
 * Specifies the disposition of nodes when created.
 */
sealed trait Disposition {
  private[zookeeper] val mode: CreateMode
}

/**
 * Specifies time-to-live for certain types of dispositions.
 */
sealed trait TimeToLive {
  /**
   * Returns the time-to-live duration.
   *
   * @return the time-to-live duration
   */
  val ttl: Duration
}

/**
 * Constructs [[TimeToLive]] values.
 */
object TimeToLive {
  /**
   * Used in pattern matching to deconstruct a time-to-live instance.
   *
   * @param ttl time-to-live value
   * @return a `Some` containing the TTL duration if the selector value is not `null`, otherwise `None`
   */
  def unapply(ttl: TimeToLive): Option[Duration] =
    if (ttl == null) None else Some(ttl.ttl)
}

/**
 * Indicates that a node will persist when the creator disconnects from ZooKeeper.
 */
object Persistent extends Disposition {
  override def toString: String = "Persistent"
  private[zookeeper] val mode = PERSISTENT
}

/**
 * Indicates that a node will persist when the creator disconnects from ZooKeeper.
 *
 * The node will be eligible for deletion if not modified within the given ''ttl'' and only after all its children have been
 * deleted.
 */
class PersistentTimeToLive private (val ttl: Duration) extends Disposition with TimeToLive {
  override def toString: String = s"PersistentTimeToLive($ttl)"
  private[zookeeper] val mode = PERSISTENT_WITH_TTL
}

/**
 * Constructs [[PersistentTimeToLive]] values.
 */
object PersistentTimeToLive {
  /**
   * Constructs a new persistent disposition with time-to-live.
   *
   * @param ttl the time-to-live duration
   * @return a persistent disposition with time-to-live specified by `ttl`
   */
  def apply(ttl: Duration) = new PersistentTimeToLive(ttl)
}

/**
 * Indicates that a node will persist when the creator disconnects from ZooKeeper ''and'' that the name will be appended with
 * a monotonically increasing number.
 */
object PersistentSequential extends Disposition {
  override def toString: String = "PersistentSequential"
  private[zookeeper] val mode = PERSISTENT_SEQUENTIAL
}

/**
 * Indicates that a node will persist when the creator disconnects from ZooKeeper ''and'' that the name will be appended with
 * a monotonically increasing number.
 *
 * The node will be eligible for deletion if not modified within the given ''ttl'' and only after all its children have been
 * deleted.
 */
class PersistentSequentialTimeToLive private (val ttl: Duration) extends Disposition with TimeToLive {
  override def toString: String = s"PersistentSequentialTimeToLive($ttl)"
  private[zookeeper] val mode = PERSISTENT_SEQUENTIAL_WITH_TTL
}

/**
 * Constructs [[PersistentSequentialTimeToLive]] values.
 */
object PersistentSequentialTimeToLive {
  /**
   * Constructs a new persistent sequential disposition with time-to-live.
   *
   * @param ttl the time-to-live duration
   * @return a persistent sequential disposition with time-to-live specified by `ttl`
   */
  def apply(ttl: Duration) = new PersistentSequentialTimeToLive(ttl)
}

/**
 * Indicates that a node will be automatically deleted when the creator disconnects from ZooKeeper.
 */
object Ephemeral extends Disposition {
  override def toString: String = "Ephemeral"
  private[zookeeper] val mode = EPHEMERAL
}

/**
 * Indicates that a node will be automatically deleted when the creator disconnects from ZooKeeper ''and'' that the name will
 * be appended with a monotonically increasing number.
 */
object EphemeralSequential extends Disposition {
  override def toString: String = "EphemeralSequential"
  private[zookeeper] val mode = EPHEMERAL_SEQUENTIAL
}

/**
 * Indicates that a node will be designated as a special purpose container useful for building higher-order constructs, such
 * as leader election and locking.
 *
 * The node will be eligible for deletion once all children have been deleted.
 */
object Container extends Disposition {
  override def toString: String = "Container"
  private[zookeeper] val mode = CONTAINER
}
