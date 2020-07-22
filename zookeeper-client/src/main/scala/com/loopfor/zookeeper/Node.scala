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

/**
 * Represents a ''node'' in ZooKeeper.
 */
trait Node {
  /**
   * Returns the name of this node.
   * 
   * @return the name of this node
   */
  def name: String

  /**
   * Returns the normalized path of this node.
   * 
   * @return the normalized path of this node
   */
  def path: Path

  /**
   * Returns the parent node.
   * 
   * @return the parent node
   * @throws NoSuchElementException if removal of [[name]] from [[path]] yields `""` or `"/"`
   */
  def parent: Node

  /**
   * Returns the parent node wrapped in an `Option`.
   * 
   * @return a `Some` containing the parent node or `None` if removal of [[name]] from [[path]] yields `""` or `"/"`
   */
  def parentOption: Option[Node]

  /**
   * Resolves the given `path` relative to this node.
   * 
   * @param path the path to resolve relative to this node
   * @return a new node in which the given `path` is resolved relative to this node
   * 
   * @see [[Path]], method `resolve`, for details on path resolution
   */
  def resolve(path: String): Node

  /**
   * Resolves the given `path` relative to this node.
   * 
   * @param path the path to resolve relative to this node
   * @return a new node in which the given `path` is resolved relative to this node
   * 
   * @see [[Path]], method `resolve`, for details on path resolution
   */
  def resolve(path: Path): Node

  /**
   * Creates this node.
   * 
   * @param data the data to associate with this node, which may be empty, but not `null`
   * @param acl an access control list to apply to this node, which must not be empty
   * @param disp the disposition of this node
   * @return a new node whose [[path]] will differ if `disp` is either [[PersistentSequential]],
   * [[PersistentSequentialTimeToLive]] or [[EphemeralSequential]]
   * 
   * @see [[SynchronousZookeeper.create]] for further details
   */
  def create(data: Array[Byte], acl: Seq[ACL], disp: Disposition): Node

  /**
   * Deletes this node.
   * 
   * @param version a `Some` containing the expected version of the node or `None` if a version match is not required
   * 
   * @see [[SynchronousZookeeper.delete]] for further details
   */
  def delete(version: Option[Int]): Unit

  /**
   * Returns the data and status of this node.
   * 
   * @return a tuple containing the data and status of this node
   * 
   * @see [[SynchronousZookeeper.get]] for further details
   */
  def get(): (Array[Byte], Status)

  /**
   * Returns the data and status of this node and additionally sets a watch for any changes.
   * 
   * @param fn a partial function invoked when applicable events occur
   * @return a tuple containing the data and status of this node
   * 
   * @see [[SynchronousWatchableZookeeper.get]] for further details
   */
  def get(fn: PartialFunction[Event, Unit]): (Array[Byte], Status)

  /**
   * Sets the data for this node.
   * 
   * @param data the data to associate with this node, which may be empty, but not `null`
   * @param version a `Some` containing the expected version of this node or `None` if a version match is not required
   * @return the status of the node
   * 
   * @see [[SynchronousZookeeper.set]] for further details
   */
  def set(data: Array[Byte], version: Option[Int]): Status

  /**
   * Returns the status of this node if it exists.
   * 
   * @return a `Some` containing this node status or `None` if this node does not exist
   * 
   * @see [[SynchronousZookeeper.exists]] for further details
   */
  def exists(): Option[Status]

  /**
   * Returns the status of this node if it exists and additionally sets a watch for any changes.
   * 
   * @param fn a partial function invoked when applicable events occur
   * @return a `Some` containing this node status or `None` if this node does not exist
   * 
   * @see [[SynchronousWatchableZookeeper.exists]] for further details
   */
  def exists(fn: PartialFunction[Event, Unit]): Option[Status]

  /**
   * Returns the children of this node.
   * 
   * @return an unordered sequence containing each child node
   * 
   * @see [[SynchronousZookeeper.children]] for further details
   */
  def children(): Seq[Node]

  /**
   * Returns the children of this node and additionally sets a watch for any changes.
   * 
   * @param fn a partial function invoked when applicable events occur
   * @return an unordered sequence containing each child node
   * 
   * @see [[SynchronousWatchableZookeeper.children]] for further details
   */
  def children(fn: PartialFunction[Event, Unit]): Seq[Node]

  /**
   * Returns the ACL and status of this node.
   * 
   * @return a tuple containing the ACL and status of this node
   * 
   * @see [[SynchronousZookeeper.getACL]] for further details
   */
  def getACL(): (Seq[ACL], Status)

  /**
   * Sets the ACL for this node.
   * 
   * @param acl an access control list to apply to this node, which must not be empty
   * @return the status of this node
   * 
   * @see [[SynchronousZookeeper.setACL]] for further details
   */
  def setACL(acl: Seq[ACL], version: Option[Int]): Status
}

/**
 * Constructs and deconstructs [[Node]] values.
 */
object Node {
  def apply(path: String)(implicit zk: Zookeeper): Node =
    apply(Path(path))(zk)

  def apply(path: Path)(implicit zk: Zookeeper): Node =
    new Impl(zk.sync, path.normalize)

  def unapply(node: Node): Option[Path] =
    if (node == null) None else Some(node.path)

  private class Impl(zk: SynchronousZookeeper, val path: Path) extends Node {
    private implicit val _zk = zk

    lazy val name: String = path.name

    lazy val parent: Node = Node(path.parent)

    lazy val parentOption: Option[Node] = path.parentOption match {
      case Some(p) => Some(Node(p))
      case _ => None
    }

    def resolve(path: String): Node =
      Node(this.path resolve path)

    def resolve(path: Path): Node =
      resolve(path.path)

    def create(data: Array[Byte], acl: Seq[ACL], disp: Disposition): Node =
      Node(zk.create(path.path, data, acl, disp))

    def delete(version: Option[Int]): Unit =
      zk.delete(path.path, version)

    def get(): (Array[Byte], Status) =
      zk.get(path.path)

    def get(fn: PartialFunction[Event, Unit]): (Array[Byte], Status) =
      zk.watch(fn).get(path.path)

    def set(data: Array[Byte], version: Option[Int]): Status =
      zk.set(path.path, data, version)

    def exists(): Option[Status] =
      zk.exists(path.path)

    def exists(fn: PartialFunction[Event, Unit]): Option[Status] =
      zk.watch(fn).exists(path.path)

    def children(): Seq[Node] =
      zk.children(path.path).map { c => Node(path.resolve(c)) }

    def children(fn: PartialFunction[Event, Unit]): Seq[Node] =
      zk.watch(fn).children(path.path).map { c => Node(path.resolve(c)) }

    def getACL(): (Seq[ACL], Status) =
      zk.getACL(path.path)

    def setACL(acl: Seq[ACL], version: Option[Int]): Status =
      zk.setACL(path.path, acl, version)
  }
}
