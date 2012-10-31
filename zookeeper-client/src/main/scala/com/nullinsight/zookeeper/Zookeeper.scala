package com.nullinsight.zookeeper

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit.NANOSECONDS
import org.apache.zookeeper.{KeeperException => ZException, _}
import org.apache.zookeeper.KeeperException.Code
import org.apache.zookeeper.data.{ACL => ZACL, Stat}
import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent._
import scala.language._

trait Zookeeper {
  def sync: SynchronousZookeeper
  def async: AsynchronousZookeeper
  def flush(path: String): Future[Unit]
  def close(): Unit
}

trait SynchronousZookeeper extends Zookeeper {
  def create(path: String, data: Array[Byte], acl: Seq[ACL], disp: Disposition): String
  def delete(path: String, version: Int): Unit
  def get(path: String): (Array[Byte], Node)
  def set(path: String, data: Array[Byte], version: Int): Node
  def exists(path: String): Option[Node]
  def children(path: String): Seq[String]
  def getACL(path: String): (Seq[ACL], Node)
  def setACL(path: String, acl: Seq[ACL], version: Int): Node
  def watch(fn: PartialFunction[Event, Unit]): SynchronousWatchableZookeeper
  def transact(ops: Seq[Operation]): Either[Seq[Problem], Seq[Result]]
}

trait SynchronousWatchableZookeeper extends Zookeeper {
  def get(path: String): (Array[Byte], Node)
  def exists(path: String): Option[Node]
  def children(path: String): Seq[String]
}

trait AsynchronousZookeeper extends Zookeeper {
  def create(path: String, data: Array[Byte], acl: Seq[ACL], disp: Disposition): Future[String]
  def delete(path: String, version: Int): Future[Unit]
  def get(path: String): Future[(Array[Byte], Node)]
  def set(path: String, data: Array[Byte], version: Int): Future[Node]
  def exists(path: String): Future[Option[Node]]
  def children(path: String): Future[(Seq[String], Node)]
  def getACL(path: String): Future[(Seq[ACL], Node)]
  def setACL(path: String, acl: Seq[ACL], version: Int): Future[Node]
  def watch(fn: PartialFunction[Event, Unit]): AsynchronousWatchableZookeeper
}

trait AsynchronousWatchableZookeeper extends Zookeeper {
  def get(path: String): Future[(Array[Byte], Node)]
  def exists(path: String): Future[Option[Node]]
  def children(path: String): Future[(Seq[String], Node)]
}

private class BaseZK(zk: ZooKeeper, exec: ExecutionContext) extends Zookeeper {
  private implicit val _exec = exec

  def sync: SynchronousZookeeper = new SynchronousZK(zk, exec)

  def async: AsynchronousZookeeper = new AsynchronousZK(zk, exec)

  def flush(path: String): Future[Unit] = {
    val p = promise[Unit]
    zk.sync(path, VoidHandler(p), null)
    p.future
  }

  def close(): Unit = zk.close()
}

private class SynchronousZK(zk: ZooKeeper, exec: ExecutionContext) extends BaseZK(zk, exec) with SynchronousZookeeper {
  def create(path: String, data: Array[Byte], acl: Seq[ACL], disp: Disposition) = {
    zk.create(path, data, ACL.toZACL(acl), disp.mode)
  }

  def delete(path: String, version: Int) {
    zk.delete(path, version)
  }

  def get(path: String) = {
    val stat = new Stat
    val data = zk.getData(path, false, stat)
    (data, Node(path, stat))
  }

  def set(path: String, data: Array[Byte], version: Int) = {
    val stat = zk.setData(path, data, version)
    Node(path, stat)
  }

  def exists(path: String) = {
    val stat = zk.exists(path, false)
    if (stat == null) None else Some(Node(path, stat))
  }

  def children(path: String) = {
    zk.getChildren(path, false).asScala.toList
  }

  def getACL(path: String) = {
    val stat = new Stat
    val zacl = zk.getACL(path, stat)
    (ACL(zacl), Node(path, stat))
  }

  def setACL(path: String, acl: Seq[ACL], version: Int) = {
    val stat = zk.setACL(path, ACL.toZACL(acl), version)
    Node(path, stat)
  }

  def watch(fn: PartialFunction[Event, Unit]) = {
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
          case _op: SetOperation => SetResult(_op, Node(_op.path, result.asInstanceOf[OpResult.SetDataResult].getStat))
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

  def get(path: String) = {
    val stat = new Stat
    val data = zk.getData(path, watcher, stat)
    (data, Node(path, stat))
  }

  def exists(path: String) = {
    val stat = zk.exists(path, watcher)
    if (stat == null) None else Some(Node(path, stat))
  }

  def children(path: String): Seq[String] = {
    zk.getChildren(path, watcher).asScala.toList
  }
}

private class AsynchronousZK(zk: ZooKeeper, exec: ExecutionContext) extends BaseZK(zk, exec) with AsynchronousZookeeper {
  private implicit val _exec = exec

  def create(path: String, data: Array[Byte], acl: Seq[ACL], disp: Disposition): Future[String] = {
    val p = promise[String]
    zk.create(path, data, ACL.toZACL(acl), disp.mode, StringHandler(p), null)
    p.future
  }

  def delete(path: String, version: Int): Future[Unit] = {
    val p = promise[Unit]
    zk.delete(path, version, VoidHandler(p), null)
    p.future
  }

  def get(path: String): Future[(Array[Byte], Node)] = {
    val p = promise[(Array[Byte], Node)]
    zk.getData(path, false, DataHandler(p), null)
    p.future
  }

  def set(path: String, data: Array[Byte], version: Int): Future[Node] = {
    val p = promise[Node]
    zk.setData(path, data, version, StatHandler(p), null)
    p.future
  }

  def exists(path: String): Future[Option[Node]] = {
    val p = promise[Option[Node]]
    zk.exists(path, false, ExistsHandler(p), null)
    p.future
  }

  def children(path: String): Future[(Seq[String], Node)] = {
    val p = promise[(Seq[String], Node)]
    zk.getChildren(path, false, ChildrenHandler(p), null)
    p.future
  }

  def getACL(path: String): Future[(Seq[ACL], Node)] = {
    val p = promise[(Seq[ACL], Node)]
    zk.getACL(path, new Stat, ACLHandler(p), null)
    p.future
  }

  def setACL(path: String, acl: Seq[ACL], version: Int): Future[Node] = {
    val p = promise[Node]
    zk.setACL(path, ACL.toZACL(acl), version, StatHandler(p), null)
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

  def get(path: String): Future[(Array[Byte], Node)] = {
    val p = promise[(Array[Byte], Node)]
    zk.getData(path, watcher, DataHandler(p), null)
    p.future
  }

  def exists(path: String): Future[Option[Node]] = {
    val p = promise[Option[Node]]
    zk.exists(path, watcher, ExistsHandler(p), null)
    p.future
  }

  def children(path: String): Future[(Seq[String], Node)] = {
    val p = promise[(Seq[String], Node)]
    zk.getChildren(path, watcher, ChildrenHandler(p), null)
    p.future
  }
}

private object StringHandler {
  def apply(p: Promise[String]) = new AsyncCallback.StringCallback {
    def processResult(rc: Int, path: String, context: Object, name: String) {
      Code.get(rc) match {
        case Code.OK => p success name
        case code => p failure ZException.create(code)
      }
    }
  }
}

private object VoidHandler {
  def apply(p: Promise[Unit]) = new AsyncCallback.VoidCallback {
    def processResult(rc: Int, path: String, context: Object) {
      Code.get(rc) match {
        case Code.OK => p success ()
        case code => p failure ZException.create(code)
      }
    }
  }
}

private object DataHandler {
  def apply(p: Promise[(Array[Byte], Node)]) = new AsyncCallback.DataCallback {
    def processResult(rc: Int, path: String, context: Object, data: Array[Byte], stat: Stat) {
      Code.get(rc) match {
        case Code.OK => p success (data, Node(path, stat))
        case code => p failure ZException.create(code)
      }
    }
  }
}

private object ChildrenHandler {
  def apply(p: Promise[(Seq[String], Node)]) = new AsyncCallback.Children2Callback {
    def processResult(rc: Int, path: String, context: Object, children: java.util.List[String], stat: Stat) {
      Code.get(rc) match {
        case Code.OK => p success (children.asScala.toList, Node(path, stat))
        case code => p failure ZException.create(code)
      }
    }
  }
}

private object ACLHandler {
  def apply(p: Promise[(Seq[ACL], Node)]) = new AsyncCallback.ACLCallback {
    def processResult(rc: Int, path: String, context: Object, zacl: java.util.List[ZACL], stat: Stat) {
      Code.get(rc) match {
        case Code.OK => p success (ACL(zacl), Node(path, stat))
        case code => p failure ZException.create(code)
      }
    }
  }
}

private object StatHandler {
  def apply(p: Promise[Node]) = new AsyncCallback.StatCallback {
    def processResult(rc: Int, path: String, context: Object, stat: Stat) {
      Code.get(rc) match {
        case Code.OK => p success Node(path, stat)
        case code => p failure ZException.create(code)
      }
    }
  }
}

private object ExistsHandler {
  def apply(p: Promise[Option[Node]]) = new AsyncCallback.StatCallback {
    def processResult(rc: Int, path: String, context: Object, stat: Stat) {
      Code.get(rc) match {
        case Code.OK => p success (if (stat == null) None else Some(Node(path, stat)))
        case code => p failure ZException.create(code)
      }
    }
  }
}

object Zookeeper {
  def apply(config: Configuration): Zookeeper = apply(config, null)

  def apply(config: Configuration, cred: Credential): Zookeeper = {
    val servers = ("" /: config.servers) {
      case (buf, addr) => (if (buf.isEmpty) buf else buf + ",") + addr.getHostName + ":" + addr.getPort
    }
    val path = (if (config.path startsWith "/") "" else "/") + config.path
    val timeout = config.timeout.toMillis.asInstanceOf[Int]
    val watcher = new ConnectionWatcher(if (config.watcher == null) (_: StateEvent, _: Session) => () else config.watcher)
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
        throw new IllegalStateException("zookeeper instance already associated")
    }
  }
}

object SynchronousZookeeper {
  def apply(config: Configuration): SynchronousZookeeper = Zookeeper(config).sync
  def apply(config: Configuration, cred: Credential): SynchronousZookeeper = Zookeeper(config, cred).sync
}

object AsynchronousZookeeper {
  def apply(config: Configuration): AsynchronousZookeeper = Zookeeper(config).async
  def apply(config: Configuration, cred: Credential): AsynchronousZookeeper = Zookeeper(config, cred).async
}
