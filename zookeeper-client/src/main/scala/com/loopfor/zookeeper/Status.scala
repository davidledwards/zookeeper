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

import org.apache.zookeeper.data.Stat

/**
 * The ''status'' of a ZooKeeper node.
 * 
 * Each modification to ZooKeeper is stamped with a monotonically-increasing sequence number, known as a ''transaction id'' or
 * ''zxid'', which conveys a total ordering of all changes. Thus, given any two changes, A and B, denoted by transaction ids,
 * `zxid,,A,,` and `zxid,,B,,`, respectively, A is said to ''happen before'' B if `zxid,,A,,` < `zxid,,B,,`. Note that the
 * ''transaction id'' is scoped to the entire ZooKeeper repository, not to individual nodes.
 * 
 * In addition to stamping all repository changes with a transaction id, which establishes total order, each modification to a
 * given node also causes some ''version'' of that node to increment.
 */
trait Status {
  /**
   * Returns the path of this node.
   */
  def path: String

  /**
   * Returns the transaction id corresponding to the creation of this node.
   */
  def czxid: Long

  /**
   * Returns the transaction id corresponding to the last modification of this node.
   */
  def mzxid: Long

  /**
   * Returns the transaction id corresponding to the last modification of the children of this node.
   */
  def pzxid: Long

  /**
   * Returns the time in milliseconds since ''epoch'' corresponding to the creation of this node.
   */
  def ctime: Long

  /**
   * Returns the time in milliseconds since ''epoch'' corresponding to the last modification of this node.
   */
  def mtime: Long

  /**
   * Returns the number of changes to the data of this node.
   */
  def version: Int

  /**
   * Returns the number of changes to the children of this node.
   */
  def cversion: Int

  /**
   * Returns the number of changes to the [[ACL]] of this node.
   */
  def aversion: Int

  /**
   * Returns the session id of the owner if this node is ''ephemeral'', otherwise the value is `0`.
   */
  def ephemeralOwner: Long

  /**
   * Returns the length of the data associated with this node.
   */
  def dataLength: Int

  /**
   * Returns the number of children associated with this node.
   */
  def numChildren: Int
}

private[zookeeper] object Status {
  def apply(path: String, stat: Stat): Status = {
    val _path = path
    new Status{
      val path: String = _path
      val czxid: Long = stat.getCzxid
      val mzxid: Long = stat.getMzxid
      val pzxid: Long = stat.getPzxid
      val ctime: Long = stat.getCtime
      val mtime: Long = stat.getMtime
      val version: Int = stat.getVersion
      val cversion: Int = stat.getCversion
      val aversion: Int = stat.getAversion
      val ephemeralOwner: Long = stat.getEphemeralOwner
      val dataLength: Int = stat.getDataLength
      val numChildren: Int = stat.getNumChildren

      override def toString: String = {
        "Status(path=" + path + ",czxid=" + czxid + ",mzxid=" + mzxid + ",ctime=" + ctime + ",mtime=" +
        mtime + ",version=" + version + ",cversion=" + cversion + ",aversion=" + aversion + ",ephemeralOwner=" +
        ephemeralOwner + ",dataLength=" + dataLength + ",numChildren=" + numChildren + ")"
      }
    }
  }
}
