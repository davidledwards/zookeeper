package com.loopfor.zookeeper

trait Node {
  def path: Path
  def parent: Node
  def parentOption: Option[Node]
  def create(data: Array[Byte], acl: Seq[ACL], disp: Disposition): Node
  def delete(version: Option[Int])
  def get(): (Array[Byte], Status)
  def get(fn: PartialFunction[Event, Unit]): (Array[Byte], Status)
  def set(data: Array[Byte], version: Option[Int]): Status
  def exists(): Option[Status]
  def exists(fn: PartialFunction[Event, Unit]): Option[Status]
  def children(): Seq[Node]
  def children(fn: PartialFunction[Event, Unit]): Seq[Node]
  def getACL(): (Seq[ACL], Status)
  def setACL(acl: Seq[ACL], version: Option[Int]): Status
}

object Node {
  def apply(path: String)(implicit zk: Zookeeper): Node = apply(Path(path))(zk)

  def apply(path: Path)(implicit zk: Zookeeper): Node = new Impl(zk.sync, path.normalize)

  def unapply(node: Node): Option[Path] =
    if (node == null) None else Some(node.path)

  private class Impl(zk: SynchronousZookeeper, val path: Path) extends Node {
    private implicit val _zk = zk

    lazy val parent: Node = Node(path.parent)

    lazy val parentOption: Option[Node] = path.parentOption match {
      case Some(p) => Some(Node(p))
      case _ => None
    }

    def create(data: Array[Byte], acl: Seq[ACL], disp: Disposition): Node =
      Node(zk.create(path.path, data, acl, disp))

    def delete(version: Option[Int]) =
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
      zk.children(path.path) map { Node(_) }

    def children(fn: PartialFunction[Event, Unit]): Seq[Node] =
      zk.watch(fn).children(path.path) map { Node(_) }

    def getACL(): (Seq[ACL], Status) =
      zk.getACL(path.path)

    def setACL(acl: Seq[ACL], version: Option[Int]): Status =
      zk.setACL(path.path, acl, version)
  }
}

object NodeExamples {
  def main(args: Array[String]) {
    val config = Configuration(("localhost", 2181) :: Nil)
    implicit val zk = SynchronousZookeeper(config)
    val node = Node("/foobar").create(Array(), ACL.EveryoneAll, EphemeralSequential)
    println(node.path)
    zk.close()
  }
}