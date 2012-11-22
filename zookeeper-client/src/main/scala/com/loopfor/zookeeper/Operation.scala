package com.loopfor.zookeeper

import org.apache.zookeeper.Op

/**
 * An ''operation'' that may be composed with others to form an atomic transaction to ZooKeeper.
 */
sealed trait Operation {
  /**
   * Returns the path of the node corresponding to this operation.
   * 
   * @return the path of the node corresponding to this operation
   */
  def path: String
  private[zookeeper] def op: Op
}

/**
 * A ''create'' operation.
 * 
 * @param path the path of the node
 * @param data the data to associate with the node
 * @param acl the ACL to associate with the node
 * @param disp the disposition of the node
 */
case class CreateOperation(path: String, data: Array[Byte], acl: Seq[ACL], disp: Disposition) extends Operation {
  private[zookeeper] val op: Op = Op.create(path, data, ACL.toZACL(acl), disp.mode.toFlag)
}

/**
 * A ''delete'' operation.
 * 
 * @param path the path of the node
 * @param version a `Some` containing the expected version of the node or `None` if a version match is not required
 */
case class DeleteOperation(path: String, version: Option[Int]) extends Operation {
  private[zookeeper] val op: Op = Op.delete(path, version getOrElse -1)
}

/**
 * A ''check'' operation.
 * 
 * @param path the path of the node
 * @param version a `Some` containing the expected version of the node or `None` if a version match is not required
 */
case class CheckOperation(path: String, version: Option[Int]) extends Operation {
  private[zookeeper] val op: Op = Op.check(path, version getOrElse -1)
}

/**
 * A ''set'' operation.
 * 
 * @param path the path of the node
 * @param data the data to associate with the node
 * @param version a `Some` containing the expected version of the node or `None` if a version match is not required
 */
case class SetOperation(path: String, data: Array[Byte], version: Option[Int]) extends Operation {
  private[zookeeper] val op: Op = Op.setData(path, data, version getOrElse -1)
}

/**
 * The ''result'' of an [[Operation operation]] within an atomic transaction.
 */
sealed trait Result {
  /**
   * Returns the operation corresponding to this result.
   * 
   * @param op the operation corresponding to this result
   */
  def op: Operation
}

/**
 * The result of a ''create'' operation.
 * 
 * @param op the create operation corresponding to this result
 * @param path the final path of the created node, which will differ from the path in `op` if either [[PersistentSequential]]
 * or [[EphemeralSequential]] disposition is specified
 */
case class CreateResult(op: CreateOperation, path: String) extends Result

/**
 * The result of a ''delete'' operation.
 * 
 * @param op the delete operation corresponding to this result
 */
case class DeleteResult(op: DeleteOperation) extends Result

/**
 * The result of a ''set'' operation.
 * 
 * @param op the set operation corresponding to this result
 * @param node the status of the node after the operation is applied
 */
case class SetResult(op: SetOperation, status: Status) extends Result

/**
 * The result of a ''check'' operation.
 * 
 * @param op the check operation corresponding to this result
 */
case class CheckResult(op: CheckOperation) extends Result

/**
 * A ''problem'' with an [[Operation operation]] in the context of an atomic operation.
 */
sealed trait Problem {
  /**
   * Returns the operation corresponding to this problem.
   * 
   * @param op the operation corresponding to this problem
   */
  def op: Operation

  /**
   * Returns the exception associated with this problem.
   * 
   * @return a `Some` containing the exception that led to this problem or `None` if no problem was reported
   */
  def error: Option[KeeperException]
}

/**
 * A problem with a ''create'' operation.
 * 
 * @param op the create operation corresponding to this problem
 * @param error a `Some` containing the exception that led to this problem or `None` if no problem was reported
 */
case class CreateProblem(op: CreateOperation, error: Option[KeeperException]) extends Problem

/**
 * A problem with a ''delete'' operation.
 * 
 * @param op the delete operation corresponding to this problem
 * @param error a `Some` containing the exception that led to this problem or `None` if no problem was reported
 */
case class DeleteProblem(op: DeleteOperation, error: Option[KeeperException]) extends Problem

/**
 * A problem with a ''set'' operation.
 * 
 * @param op the set operation corrresponding to this problem
 * @param error a `Some` containing the exception that led to this problem or `None` if no problem was reported
 */
case class SetProblem(op: SetOperation, error: Option[KeeperException]) extends Problem

/**
 * A problem with a ''check'' operation.
 * 
 * @param op the check operation corresponding to this problem
 * @param error a `Some` containing the exception that led to this problem or `None` if no problem was reported
 */
case class CheckProblem(op: CheckOperation, error: Option[KeeperException]) extends Problem
