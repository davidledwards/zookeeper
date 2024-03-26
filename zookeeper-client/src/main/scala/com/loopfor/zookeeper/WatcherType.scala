package com.loopfor.zookeeper

import org.apache.zookeeper.Watcher
import org.apache.zookeeper.Watcher.WatcherType._

sealed trait WatcherType {
  private[zookeeper] val watcherType: Watcher.WatcherType
}

object ANY extends WatcherType {
  override def toString: String = "Any"
  private[zookeeper] val watcherType = Any
}

object CHILDREN extends WatcherType {
  override def toString: String = "Children"
  private[zookeeper] val watcherType = Children
}

object DATA extends WatcherType {
  override def toString: String = "Data"
  private[zookeeper] val watcherType = Data
}



