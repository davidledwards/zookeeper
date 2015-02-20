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

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit.NANOSECONDS
import org.apache.zookeeper.{KeeperException => ZException, _}
import org.apache.zookeeper.KeeperException.Code
import org.apache.zookeeper.data.{ACL => ZACL, Stat}
import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language._

/**
 * An instance of a ZooKeeper client.
 */
trait Zookeeper {
  /**
   * Returns a view of this client in which operations are performed ''synchronously''.
   * 
   * @return a synchronous view of this client
   */
  def sync: SynchronousZookeeper

  /**
   * Returns a view of this client in which operations are performed ''asynchronously''.
   * 
   * @return an asynchronous view of this client
   */
  def async: AsynchronousZookeeper

  /**
   * Ensures that the value of a node, specified by the given path, is synchronized across the ZooKeeper cluster.
   * 
   * '''An important note on consistency''': ZooKeeper does not guarantee, for any given point in time, that all clients will
   * have a consistent view of the cluster. Since reads can be served by any node in the cluster, whereas writes are serialized
   * through the leader, there exists the possibility in which two separate clients may have inconsistent views. This scenario
   * occurs when the leader commits a change once consensus is reached, but the change has not yet propagated across the
   * cluster. Therefore, reads occurring before the commit has propagated will be globally inconsistent. This behavior is
   * normally acceptable, but for some use cases, writes may need to be globally visible before subsequent reads occur.
   * 
   * This method is particularly useful when a ''write'' occurring in one process is followed by a ''read'' in another process.
   * For example, consider the following sequence of operations:
   * 
   *  - process A writes a value
   *  - process A sends a message to process B
   *  - process B reads the value
   * 
   * The assumption is that process B expects to see the value written by process A, but as mentioned, ZooKeeper does not make
   * this guarantee. A call to this method before process B attempts to read the value ensures that all prior writes are
   * consistently applied across the cluster, thus observing the write in process A.
   * 
   * @return a future that completes when the node is synchronized across the cluster
   */
  def ensure(path: String): Future[Unit]

  /**
   * Closes the client connection to the ZooKeeper cluster.
   * 
   * A consequence of closing the connection is that ZooKeeper will expire the corresponding session, which further implies
   * that all ephemeral nodes created by this client will be deleted.
   */
  def close(): Unit
}

/**
 * A ZooKeeper client with ''synchronous'' operations.
 */
trait SynchronousZookeeper extends Zookeeper {
  /**
   * Creates a new node at the given path.
   * 
   * If a ''sequential'' [[Disposition disposition]] is provided in `disp`, then `path` is appended with a monotonically
   * increasing sequence, thus guaranteeing that all sequential nodes are unique with `path` as their prefix.
   * 
   * @param path the path of the node to create
   * @param data the data to associate with the node, which may be empty, but not `null`
   * @param acl an access control list to apply to the node, which must not be empty
   * @param disp the disposition of the node
   * @return the final path of the created node, which will differ from `path` if `disp` is either [[PersistentSequential]]
   * or [[EphemeralSequential]]
   */
  def create(path: String, data: Array[Byte], acl: Seq[ACL], disp: Disposition): String

  /**
   * Deletes the node specified by the given path.
   * 
   * @param path the path of the node
   * @param version a `Some` containing the expected version of the node or `None` if a version match is not required
   * 
   * @throws NoNodeException if the node does not exist
   * @throws BadVersionException if `version` is specified and does not match the node version
   * @throws NotEmptyException if the node contains children
   */
  def delete(path: String, version: Option[Int]): Unit

  /**
   * Returns the data and status of the node specified by the given path.
   * 
   * @param path the path of the node
   * @return a tuple containing the data and status of the node
   * 
   * @throws NoNodeException if the node does not exist
   */
  def get(path: String): (Array[Byte], Status)

  /**
   * Sets the data for the node specified by the given path.
   * 
   * @param path the path of the node
   * @param data the data to associate with the node, which may be empty, but not `null`
   * @param version a `Some` containing the expected version of the node or `None` if a version match is not required
   * @return the status of the node
   * 
   * @throws NoNodeException if the node does not exist
   * @throws BadVersionException if `version` is specified and does not match the node version
   */
  def set(path: String, data: Array[Byte], version: Option[Int]): Status

  /**
   * Returns the status of the node specified by the given path if it exists.
   * 
   * @param path the path of the node
   * @return a `Some` containing the node status or `None` if the node does not exist
   */
  def exists(path: String): Option[Status]

  /**
   * Returns the children of the node specified by the given path.
   * 
   * @param path the path of the node
   * @return an unordered sequence containing the names of each child node
   * 
   * @throws NoNodeException if the node does not exist
   */
  def children(path: String): Seq[String]

  /**
   * Returns the ACL and status of the node specified by the given path.
   * 
   * @param path the path of the node
   * @return a tuple containing the ACL and status of the node
   * 
   * @throws NoNodeException if the node does not exist
   */
  def getACL(path: String): (Seq[ACL], Status)

  /**
   * Sets the ACL for the node specified by the given path.
   * 
   * @param path the path of the node
   * @param acl an access control list to apply to the node, which must not be empty
   * @param version a `Some` containing the expected version of the node or `None` if a version match is not required
   * @return the status of the node
   * 
   * @throws NoNodeException if the node does not exist
   * @throws BadVersionException if `version` is specified and does not match the node version
   */
  def setACL(path: String, acl: Seq[ACL], version: Option[Int]): Status

  /**
   * Returns a synchronous client in which operations implicitly attach the specified watch function.
   * 
   * The partial function `fn` is invoked when a watch is triggered or the session state changes. This method is typically
   * used in a transient manner to introduce a watch function prior to performing a watchable ZooKeeper operation.
   * 
   * Example:
   * {{{
   * val zk = SynchronousZookeeper(config)
   * val (data, node) = zk watch {
   *   case e: NodeEvent => ...
   *   case e: StateEvent => ...
   * } get "/foo"
   * }}}
   */
  def watch(fn: PartialFunction[Event, Unit]): SynchronousWatchableZookeeper

  /**
   * Atomically performs a set of operations, either committing all or none.
   * 
   * The set of operations are applied by ZooKeeper in sequential order. If successful, this method returns a `Right`
   * containing a sequence of results that positionally correlate to the sequence of operations. Otherwise, it returns a
   * `Left` similarly containing a sequence of problems.
   * 
   * Example:
   * {{{
   * val ops = CheckOperation("/foo", None) ::
   *           CreateOperation("/bar", Array(), ACL.EveryoneAll, Persistent) :: Nil
   * zk transact ops match {
   *   case Right(results) => ...
   *   case Left(problems) => ...
   * }
   * }}}
   */
  def transact(ops: Seq[Operation]): Either[Seq[Problem], Seq[Result]]
}

/**
 * A ZooKeeper client with ''synchronous'' and ''watchable'' operations.
 */
trait SynchronousWatchableZookeeper extends Zookeeper {
  /**
   * Returns the data and status of the node specified by the given path and additionally sets a watch for any changes.
   * 
   * The watch is triggered when one of the following conditions occur:
   *  - the data associated with the node changes
   *  - the node is deleted
   *  - the session state changes
   * 
   * @param path the path of the node
   * @return a tuple containing the data and status of the node
   * 
   * @throws NoNodeException if the node does not exist
   */
  def get(path: String): (Array[Byte], Status)

  /**
   * Returns the status of the node specified by the given path if it exists and additionally sets a watch for any changes.
   * 
   * The watch is triggered when one of the following conditions occur:
   *  - the data associated with the node changes
   *  - the node is created
   *  - the node is deleted
   *  - the session state changes
   * 
   * @param path the path of the node
   * @return a `Some` containing the node status or `None` if the node does not exist
   */
  def exists(path: String): Option[Status]

  /**
   * Returns the children of the node specified by the given path and additionally sets a watch for any changes.
   * 
   * The watch is triggered when one of the following conditions occur:
   *  - the session state changes
   * 
   * @param path the path of the node
   * @return an unordered sequence containing the names of each child node
   * 
   * @throws NoNodeException if the node does not exist
   */
  def children(path: String): Seq[String]
}

/**
 * A ZooKeeper client with ''asynchronous'' operations.
 */
trait AsynchronousZookeeper extends Zookeeper {
  /**
   * Asynchronously creates a new node at the given path.
   * 
   * If a ''sequential'' [[Disposition disposition]] is provided in `disp`, then `path` is appended with a monotonically
   * increasing sequence, thus guaranteeing that all sequential nodes are unique with `path` as their prefix.
   * 
   * @param path the path of the node to create
   * @param data the data to associate with the node, which may be empty, but not `null`
   * @param acl an access control list to apply to the node, which must not be empty
   * @param disp the disposition of the node
   * @return a future yielding the final path of the created node, which will differ from `path` if `disp` is either
   * [[PersistentSequential]] or [[EphemeralSequential]]
   */
  def create(path: String, data: Array[Byte], acl: Seq[ACL], disp: Disposition): Future[String]

  /**
   * Asynchronously deletes the node specified by the given path.
   * 
   * @param path the path of the node
   * @param version a `Some` containing the expected version of the node or `None` if a version match is not required
   * @return a future, which upon success, yields `Unit`, otherwise one of the following exceptions:
   *  - `NoNodeException` if the node does not exist
   *  - `BadVersionException` if `version` is specified and does not match the node version
   *  - `NotEmptyException` if the node contains children
   */
  def delete(path: String, version: Option[Int]): Future[Unit]

  /**
   * Asynchronously gets the data and status of the node specified by the given path.
   * 
   * @param path the path of the node
   * @return a future, which upon success, yeilds a tuple containing the data and status of the node, otherwise one of the
   * following exceptions:
   *  - NoNodeException if the node does not exist
   */
  def get(path: String): Future[(Array[Byte], Status)]

  /**
   * Asynchronously sets the data for the node specified by the given path.
   * 
   * @param path the path of the node
   * @param data the data to associate with the node, which may be empty, but not `null`
   * @param version a `Some` containing the expected version of the node or `None` if a version match is not required
   * @return a future, which upon success, yields the status of the node, otherwise one of the following exceptions:
   *  - NoNodeException if the node does not exist
   *  - BadVersionException if `version` is specified and does not match the node version
   */
  def set(path: String, data: Array[Byte], version: Option[Int]): Future[Status]

  /**
   * Asynchronously determines the status of the node specified by the given path if it exists.
   * 
   * @param path the path of the node
   * @return a future yielding a `Some` containing the node status or `None` if the node does not exist
   */
  def exists(path: String): Future[Option[Status]]

  /**
   * Asynchronously gets the children of the node specified by the given path.
   * 
   * @param path the path of the node
   * @return a future, which upon success, yields an unordered sequence containing the names of each child node, otherwise one
   * of the following exceptions:
   *  - NoNodeException if the node does not exist
   */
  def children(path: String): Future[(Seq[String], Status)]

  /**
   * Asynchronously gets the ACL and status of the node specified by the given path.
   * 
   * @param path the path of the node
   * @return a future, which upon success, yields a tuple containing the ACL and status of the node, otherwise one of the
   * following exceptions:
   *  - NoNodeException if the node does not exist
   */
  def getACL(path: String): Future[(Seq[ACL], Status)]

  /**
   * Asynchronously sets the ACL for the node specified by the given path.
   * 
   * @param path the path of the node
   * @param acl an access control list to apply to the node, which must not be empty
   * @param version a `Some` containing the expected version of the node or `None` if a version match is not required
   * @return a future, which upon success, yields the status of the node, otherwise one of the following conditions:
   *  - NoNodeException if the node does not exist
   *  - BadVersionException if `version` is specified and does not match the node version
   */
  def setACL(path: String, acl: Seq[ACL], version: Option[Int]): Future[Status]

  /**
   * Returns an asynchronous client in which operations implicitly attach the specified watch function.
   * 
   * The partial function `fn` is invoked when a watch is triggered or the session state changes. This method is typically
   * used in a transient manner to introduce a watch function prior to performing a watchable ZooKeeper operation.
   * 
   * Example:
   * {{{
   * val zk = AsynchronousZookeeper(config)
   * val future = zk watch {
   *   case e: NodeEvent => ...
   *   case e: StateEvent => ...
   * } get "/foo"
   * ...
   * future onSuccess {
   *   case (data, node) => ...
   * }
   * future onFailure {
   *   case e: NoNodeException => ...
   * }
   * }}}
   */
  def watch(fn: PartialFunction[Event, Unit]): AsynchronousWatchableZookeeper
}

/**
 * A ZooKeeper client with ''asynchronous'' and ''watchable'' operations.
 */
trait AsynchronousWatchableZookeeper extends Zookeeper {
  /**
   * Asynchronously gets the data and status of the node specified by the given path and additionally sets a watch for any
   * changes.
   * 
   * The watch is triggered when one of the following conditions occur:
   *  - the data associated with the node changes
   *  - the node is deleted
   *  - the session state changes
   * 
   * @param path the path of the node
   * @return a future, which upon success, yields a tuple containing the data and status of the node, otherwise one of the
   * following exceptions:
   *  - NoNodeException if the node does not exist
   */
  def get(path: String): Future[(Array[Byte], Status)]

  /**
   * Asynchronously determines the status of the node specified by the given path if it exists and additionally sets a watch
   * for any changes.
   * 
   * The watch is triggered when one of the following conditions occur:
   *  - the data associated with the node changes
   *  - the node is created
   *  - the node is deleted
   *  - the session state changes
   * 
   * @param path the path of the node
   * @return a future yielding a `Some` containing the node status or `None` if the node does not exist
   */
  def exists(path: String): Future[Option[Status]]

  /**
   * Asynchronously gets the children of the node specified by the given path and additionally sets a watch for any changes.
   * 
   * The watch is triggered when one of the following conditions occur:
   *  - the session state changes
   * 
   * @param path the path of the node
   * @return a future, which upon success, yields an unordered sequence containing the names of each child node, otherwise
   * one of the following exceptions:
   *  - NoNodeException if the node does not exist
   */
  def children(path: String): Future[(Seq[String], Status)]
}

private class BaseZK(zk: ZooKeeper, exec: ExecutionContext) extends Zookeeper {
  private implicit val _exec = exec

  def sync: SynchronousZookeeper = new SynchronousZK(zk, exec)

  def async: AsynchronousZookeeper = new AsynchronousZK(zk, exec)

  def ensure(path: String): Future[Unit] = {
    val p = Promise[Unit]
    zk.sync(path, VoidHandler(p), null)
    p.future
  }

  def close(): Unit = zk.close()
}

private class SynchronousZK(zk: ZooKeeper, exec: ExecutionContext) extends BaseZK(zk, exec) with SynchronousZookeeper {
  def create(path: String, data: Array[Byte], acl: Seq[ACL], disp: Disposition) = {
    zk.create(path, data, ACL.toZACL(acl), disp.mode)
  }

  def delete(path: String, version: Option[Int]) {
    zk.delete(path, version getOrElse -1)
  }

  def get(path: String): (Array[Byte], Status) = {
    val stat = new Stat
    val data = zk.getData(path, false, stat)
    (if (data == null) Array() else data, Status(path, stat))
  }

  def set(path: String, data: Array[Byte], version: Option[Int]): Status = {
    val stat = zk.setData(path, data, version getOrElse -1)
    Status(path, stat)
  }

  def exists(path: String): Option[Status] = {
    val stat = zk.exists(path, false)
    if (stat == null) None else Some(Status(path, stat))
  }

  def children(path: String): Seq[String] = {
    zk.getChildren(path, false).asScala.toList
  }

  def getACL(path: String): (Seq[ACL], Status) = {
    val stat = new Stat
    val zacl = zk.getACL(path, stat)
    (ACL(zacl), Status(path, stat))
  }

  def setACL(path: String, acl: Seq[ACL], version: Option[Int]): Status = {
    val stat = zk.setACL(path, ACL.toZACL(acl), version getOrElse -1)
    Status(path, stat)
  }

  def watch(fn: PartialFunction[Event, Unit]): SynchronousWatchableZookeeper = {
    new SynchronousWatchableZK(zk, exec, fn)
  }

  def transact(ops: Seq[Operation]): Either[Seq[Problem], Seq[Result]] = {
    try {
      val _ops = ops map { _.op }
      val results = zk.multi(_ops.asJava).asScala
      Right(ops zip results map {
        case (op, result) => op match {
          case _op: CreateOperation => CreateResult(_op, result.asInstanceOf[OpResult.CreateResult].getPath)
          case _op: DeleteOperation => DeleteResult(_op)
          case _op: SetOperation => SetResult(_op, Status(_op.path, result.asInstanceOf[OpResult.SetDataResult].getStat))
          case _op: CheckOperation => CheckResult(_op)
        }
      })
    } catch {
      case e: KeeperException if e.getResults != null =>
        Left(ops zip e.getResults.asScala map {
          case (op, result) =>
            val rc = result.asInstanceOf[OpResult.ErrorResult].getErr
            val error = if (rc == 0) None else Some(ZException.create(Code.get(rc)))
            op match {
              case _op: CreateOperation => CreateProblem(_op, error)
              case _op: DeleteOperation => DeleteProblem(_op, error)
              case _op: SetOperation => SetProblem(_op, error)
              case _op: CheckOperation => CheckProblem(_op, error)
            }
        })
    }
  }
}

private class SynchronousWatchableZK(zk: ZooKeeper, exec: ExecutionContext, fn: PartialFunction[Event, Unit])
      extends BaseZK(zk, exec) with SynchronousWatchableZookeeper {
  private[this] val watcher = new Watcher {
    def process(event: WatchedEvent) {
      val e = Event(event)
      if (fn.isDefinedAt(e)) fn(e)
    }
  }

  def get(path: String): (Array[Byte], Status) = {
    val stat = new Stat
    val data = zk.getData(path, watcher, stat)
    (if (data == null) Array() else data, Status(path, stat))
  }

  def exists(path: String): Option[Status] = {
    val stat = zk.exists(path, watcher)
    if (stat == null) None else Some(Status(path, stat))
  }

  def children(path: String): Seq[String] = {
    zk.getChildren(path, watcher).asScala.toList
  }
}

private class AsynchronousZK(zk: ZooKeeper, exec: ExecutionContext) extends BaseZK(zk, exec) with AsynchronousZookeeper {
  private implicit val _exec = exec

  def create(path: String, data: Array[Byte], acl: Seq[ACL], disp: Disposition): Future[String] = {
    val p = Promise[String]
    zk.create(path, data, ACL.toZACL(acl), disp.mode, StringHandler(p), null)
    p.future
  }

  def delete(path: String, version: Option[Int]): Future[Unit] = {
    val p = Promise[Unit]
    zk.delete(path, version getOrElse -1, VoidHandler(p), null)
    p.future
  }

  def get(path: String): Future[(Array[Byte], Status)] = {
    val p = Promise[(Array[Byte], Status)]
    zk.getData(path, false, DataHandler(p), null)
    p.future
  }

  def set(path: String, data: Array[Byte], version: Option[Int]): Future[Status] = {
    val p = Promise[Status]
    zk.setData(path, data, version getOrElse -1, StatHandler(p), null)
    p.future
  }

  def exists(path: String): Future[Option[Status]] = {
    val p = Promise[Option[Status]]
    zk.exists(path, false, ExistsHandler(p), null)
    p.future
  }

  def children(path: String): Future[(Seq[String], Status)] = {
    val p = Promise[(Seq[String], Status)]
    zk.getChildren(path, false, ChildrenHandler(p), null)
    p.future
  }

  def getACL(path: String): Future[(Seq[ACL], Status)] = {
    val p = Promise[(Seq[ACL], Status)]
    zk.getACL(path, new Stat, ACLHandler(p), null)
    p.future
  }

  def setACL(path: String, acl: Seq[ACL], version: Option[Int]): Future[Status] = {
    val p = Promise[Status]
    zk.setACL(path, ACL.toZACL(acl), version getOrElse -1, StatHandler(p), null)
    p.future
  }

  def watch(fn: PartialFunction[Event, Unit]): AsynchronousWatchableZookeeper = {
    new AsynchronousWatchableZK(zk, exec, fn)
  }
}

private class AsynchronousWatchableZK(zk: ZooKeeper, exec: ExecutionContext, fn: PartialFunction[Event, Unit])
      extends BaseZK(zk, exec) with AsynchronousWatchableZookeeper {
  private implicit val _exec = exec

  private[this] val watcher = new Watcher {
    def process(event: WatchedEvent) {
      val e = Event(event)
      if (fn.isDefinedAt(e)) fn(e)
    }
  }

  def get(path: String): Future[(Array[Byte], Status)] = {
    val p = Promise[(Array[Byte], Status)]
    zk.getData(path, watcher, DataHandler(p), null)
    p.future
  }

  def exists(path: String): Future[Option[Status]] = {
    val p = Promise[Option[Status]]
    zk.exists(path, watcher, ExistsHandler(p), null)
    p.future
  }

  def children(path: String): Future[(Seq[String], Status)] = {
    val p = Promise[(Seq[String], Status)]
    zk.getChildren(path, watcher, ChildrenHandler(p), null)
    p.future
  }
}

private object StringHandler {
  def apply(p: Promise[String]) = new AsyncCallback.StringCallback {
    def processResult(rc: Int, path: String, context: Object, name: String) {
      (Code.get(rc): @unchecked) match {
        case Code.OK => p success name
        case code => p failure ZException.create(code)
      }
    }
  }
}

private object VoidHandler {
  def apply(p: Promise[Unit]) = new AsyncCallback.VoidCallback {
    def processResult(rc: Int, path: String, context: Object) {
      (Code.get(rc): @unchecked) match {
        case Code.OK => p success (())
        case code => p failure ZException.create(code)
      }
    }
  }
}

private object DataHandler {
  def apply(p: Promise[(Array[Byte], Status)]) = new AsyncCallback.DataCallback {
    def processResult(rc: Int, path: String, context: Object, data: Array[Byte], stat: Stat) {
      (Code.get(rc): @unchecked) match {
        case Code.OK => p success (if (data == null) Array() else data, Status(path, stat))
        case code => p failure ZException.create(code)
      }
    }
  }
}

private object ChildrenHandler {
  def apply(p: Promise[(Seq[String], Status)]) = new AsyncCallback.Children2Callback {
    def processResult(rc: Int, path: String, context: Object, children: java.util.List[String], stat: Stat) {
      (Code.get(rc): @unchecked) match {
        case Code.OK => p success (children.asScala.toList, Status(path, stat))
        case code => p failure ZException.create(code)
      }
    }
  }
}

private object ACLHandler {
  def apply(p: Promise[(Seq[ACL], Status)]) = new AsyncCallback.ACLCallback {
    def processResult(rc: Int, path: String, context: Object, zacl: java.util.List[ZACL], stat: Stat) {
      (Code.get(rc): @unchecked) match {
        case Code.OK => p success (ACL(zacl), Status(path, stat))
        case code => p failure ZException.create(code)
      }
    }
  }
}

private object StatHandler {
  def apply(p: Promise[Status]) = new AsyncCallback.StatCallback {
    def processResult(rc: Int, path: String, context: Object, stat: Stat) {
      (Code.get(rc): @unchecked) match {
        case Code.OK => p success Status(path, stat)
        case code => p failure ZException.create(code)
      }
    }
  }
}

private object ExistsHandler {
  def apply(p: Promise[Option[Status]]) = new AsyncCallback.StatCallback {
    def processResult(rc: Int, path: String, context: Object, stat: Stat) {
      (Code.get(rc): @unchecked) match {
        case Code.OK => p success (if (stat == null) None else Some(Status(path, stat)))
        case code => p failure ZException.create(code)
      }
    }
  }
}

/**
 * Constructs [[Zookeeper]] values.
 */
object Zookeeper {
  /**
   * Constructs a new ZooKeeper client using the given configuration.
   * 
   * @param config the client configuration
   * @return a client with the given `config`
   */
  def apply(config: Configuration): Zookeeper = apply(config, null)

  /**
   * Constructs a new ZooKeeper client using the given configuration and session credential.
   * 
   * @param config the client configuration
   * @param cred the session credentials
   * @return a client with the given `config` and `cred`
   */
  def apply(config: Configuration, cred: Credential): Zookeeper = {
    val servers = ("" /: config.servers) {
      case (buf, addr) => (if (buf.isEmpty) buf else buf + ",") + addr.getHostName + ":" + addr.getPort
    }
    val path = (if (config.path startsWith "/") "" else "/") + config.path
    val timeout = {
      val millis = config.timeout.toMillis
      if (millis < 0) 0 else if (millis > Int.MaxValue) Int.MaxValue else millis.asInstanceOf[Int]
    }
    val watcher = {
      val fn = if (config.watcher == null) (_: StateEvent, _: Session) => () else config.watcher
      new ConnectionWatcher(fn)
    }
    val exec = if (config.exec == null) ExecutionContext.global else config.exec
    val zk = cred match {
      case null => new ZooKeeper(servers + path, timeout, watcher, config.allowReadOnly)
      case _ => new ZooKeeper(servers + path, timeout, watcher, cred.id, cred.password, config.allowReadOnly)
    }
    watcher.associate(zk)
    new BaseZK(zk, exec)
  }

  private class ConnectionWatcher(fn: (StateEvent, Session) => Unit) extends Watcher {
    private[this] val ref = new AtomicReference[ZooKeeper]

    def process(e: WatchedEvent) {
      @tailrec def waitfor(zk: ZooKeeper): ZooKeeper = {
        if (zk == null) waitfor(ref.get()) else zk
      }
      val zk = waitfor(ref.get())
      Event(e) match {
        case event: StateEvent => fn(event, Session(zk))
        case event => throw new AssertionError("unexpected event: " + event)
      }
    }

    def associate(zk: ZooKeeper) {
      if (!ref.compareAndSet(null, zk))
        throw new AssertionError("zookeeper instance already associated")
    }
  }
}

/**
 * Constructs [[SynchronousZookeeper]] values.
 * 
 * This companion object is provided as a convenience and is equivalent to:
 * {{{
 * Zookeeper(...).sync
 * }}}
 * 
 * @see [[Zookeeper]]
 */
object SynchronousZookeeper {
  /**
   * Constructs a new synchronous ZooKeeper client using the given configuration.
   * 
   * @param config the client configuration
   * @return a client with the given `config`
   */
  def apply(config: Configuration): SynchronousZookeeper = Zookeeper(config).sync

  /**
   * Constructs a new synchronous ZooKeeper client using the given configuration and session credential.
   * 
   * @param config the client configuration
   * @param cred the session credentials
   * @return a client with the given `config` and `cred`
   */
  def apply(config: Configuration, cred: Credential): SynchronousZookeeper = Zookeeper(config, cred).sync
}

/**
 * Constructs [[AsynchronousZookeeper]] values.
 * 
 * This companion object is provided as a convenience and is equivalent to:
 * {{{
 * Zookeeper(...).async
 * }}}
 * 
 * @see [[Zookeeper]]
 */
object AsynchronousZookeeper {
  /**
   * Constructs a new asynchronous ZooKeeper client using the given configuration.
   * 
   * @param config the client configuration
   * @return a client with the given `config`
   */
  def apply(config: Configuration): AsynchronousZookeeper = Zookeeper(config).async

  /**
   * Constructs a new asynchronous ZooKeeper client using the given configuration and session credential.
   * 
   * @param config the client configuration
   * @param cred the session credentials
   * @return a client with the given `config` and `cred`
   */
  def apply(config: Configuration, cred: Credential): AsynchronousZookeeper = Zookeeper(config, cred).async
}
