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

import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.CreateMode._

/**
 * Specifies the disposition of nodes when created.
 */
sealed trait Disposition {
  private[zookeeper] val mode: CreateMode
}

/**
 * Indicates that a node will persist when the creator disconnects from ZooKeeper.
 */
object Persistent extends Disposition {
  private[zookeeper] val mode = PERSISTENT
  override def toString: String = "Persistent"
}

/**
 * Indicates that a node will persist when the creator disconnects from ZooKeeper ''and'' that the name will be appended with
 * a monotonically increasing number.
 */
object PersistentSequential extends Disposition {
  private[zookeeper] val mode = PERSISTENT_SEQUENTIAL
  override def toString: String = "PersistentSequential"
}

/**
 * Indicates that a node will be automatically deleted when the creator disconnects from ZooKeeper.
 */
object Ephemeral extends Disposition {
  private[zookeeper] val mode = EPHEMERAL
  override def toString: String = "Ephemeral"
}

/**
 * Indicates that a node will be automatically deleted when the creator disconnects from ZooKeeper ''and'' that the name will
 * be appended with a monotonically increasing number.
 */
object EphemeralSequential extends Disposition {
  private[zookeeper] val mode = EPHEMERAL_SEQUENTIAL
  override def toString: String = "EphemeralSequential"
}
