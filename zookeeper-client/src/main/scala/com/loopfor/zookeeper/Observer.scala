/*
 * Copyright 2022 David Edwards
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

import org.apache.zookeeper.Watcher.WatcherType

sealed trait Observer {
  private[zookeeper] val kind: WatcherType
}

object AnyObserver extends Observer {
  override def toString: String = "Any"
  private[zookeeper] val kind = WatcherType.Any
}

object ChildrenObserver extends Observer {
  override def toString: String = "Children"
  private[zookeeper] val kind = WatcherType.Children
}

object DataObserver extends Observer {
  override def toString: String = "Data"
  private[zookeeper] val kind = WatcherType.Data
}

object PersistentObserver extends Observer {
  override def toString: String = "Persistent"
  private[zookeeper] val kind = WatcherType.Persistent
}

object PersistentRecursiveObserver extends Observer {
  override def toString: String = "PersistentRecursive"
  private[zookeeper] val kind = WatcherType.PersistentRecursive
}

private[zookeeper] object Observer {
  private val kinds = Map(
    WatcherType.Any -> AnyObserver,
    WatcherType.Children -> ChildrenObserver,
    WatcherType.Data -> DataObserver,
    WatcherType.Persistent -> PersistentObserver,
    WatcherType.PersistentRecursive -> PersistentRecursiveObserver
  )

  def apply(kind: WatcherType): Observer = kinds.get(kind) match {
    case Some(o) => o
    case _ => new Unrecognized(kind)
  }

  private class Unrecognized(private[zookeeper] val kind: WatcherType) extends Observer {
    override def toString: String = s"UnrecognizedObserver[${kind}]"
  }
}
