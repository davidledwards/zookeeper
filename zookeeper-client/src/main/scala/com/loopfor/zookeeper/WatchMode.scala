package com.loopfor.zookeeper

import org.apache.zookeeper.AddWatchMode
import org.apache.zookeeper.AddWatchMode._

/**
 * Specifies the watch mode when watch is created.
 */
sealed trait WatchMode {
  private[zookeeper] val mode: AddWatchMode
}


/**
 * Set a watcher on the given path that does not get removed when triggered (i.e. it stays active until it is removed).
 *
 * To remove the watcher, use removeWatches() with WatcherType.Any.
 * The watcher behaves as if you placed an exists() watch and a getData() watch on the ZNode at the given path.
 */
object PersistentMode extends WatchMode {
  override def toString: String = "Persistent"
  private[zookeeper] val mode = PERSISTENT
}

/**
 * Set a watcher on the given path that:
 *
 * a) does not get removed when triggered (i.e. it stays active until it is removed);
 *
 * b) applies not only to the registered path but all child paths recursively.
 *
 * This watcher is triggered for both data and child events.
 * To remove the watcher, use removeWatches() with WatcherType.Any
 * The watcher behaves as if you placed an exists() watch and a getData() watch on the ZNode at the given path and any ZNodes that are children of the given path including children added later.
 * NOTE: when there are active recursive watches there is a small performance decrease as all segments of ZNode paths must be checked for watch triggering.
 */
object PersistentRecursiveMode extends WatchMode {
  override def toString: String = "PersistentRecursive"
  private[zookeeper] val mode = PERSISTENT_RECURSIVE
}

