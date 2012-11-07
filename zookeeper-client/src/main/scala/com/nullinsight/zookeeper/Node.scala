package com.nullinsight.zookeeper

class Node private (zk: SynchronousZookeeper, val path: String) {
  private implicit val _zk = zk

  def parent: Node = {
    null
  }

  def create(data: Array[Byte], acl: Seq[ACL], disp: Disposition): Node = {
    Node(zk.create(path, data, acl, disp))
  }

  def delete(version: Int) {
    zk.delete(path, Some(version))
  }

  def delete() {
    zk.delete(path, None)
  }

  def get(): (Array[Byte], Status) = {
    zk.get(path)
  }

  def get(fn: PartialFunction[Event, Unit]): (Array[Byte], Status) = {
    zk.watch(fn).get(path)
  }

  def set(data: Array[Byte], version: Int): Status = {
    zk.set(path, data, Some(version))
  }

  def set(data: Array[Byte]): Status = {
    zk.set(path, data, None)
  }

  def exists(): Option[Status] = {
    zk.exists(path)
  }

  def children(): Seq[Node] = {
    zk.children(path) map { p => Node(p) }
  }

  def getACL(): (Seq[ACL], Status) = {
    zk.getACL(path)
  }

  def setACL(acl: Seq[ACL], version: Int): Status = {
    zk.setACL(path, acl, Some(version))
  }

  def setACL(acl: Seq[ACL]): Status = {
    zk.setACL(path, acl, None)
  }
}

object Node {
  def apply(path: String)(implicit zk: Zookeeper): Node = new Node(zk.sync, path)
}

object NodeTest {
  def main(args: Array[String]) {
    val config = Configuration(("localhost", 2181) :: Nil)
    implicit val zk = SynchronousZookeeper(config)
    val node = Node("/foobar").create(Array(), ACL.EveryoneAll, EphemeralSequential)
    println(node.path)
    zk.close()
  }
}