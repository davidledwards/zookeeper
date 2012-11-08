package com.nullinsight.zookeeper

import com.nullinsight.zookeeper.ACL._
import org.scalatest.FunSuite
import scala.language._

class ACLTest extends FunSuite {
  private val TestScheme = "testscheme"
  private val TestId = "testid"
  private val TestPerm = Read | Write | Create

  test("Id pattern matching") {
    Id(TestScheme, TestId) match {
      case Id(TestScheme, TestId) => ()
    }
  }

  test("equivalence of Id with underlying ZooKeeper Id") {
    val id = Id(TestScheme, TestId)
    val zid = Id.toId(id)
    assert(id.scheme === zid.getScheme())
    assert(id.id === zid.getId())
  }

  test("ACL pattern matching") {
    ACL(TestScheme, TestId, Read) match {
      case ACL(Id(TestScheme, TestId), Read) => ()
    }
    ACL(Id(TestScheme, TestId), All) match {
      case ACL(Id(TestScheme, TestId), All) => ()
    }
  }

  test("equivalence of ACL with underlying ZooKeeper ACL") {
    val acl = ACL(TestScheme, TestId, Read | Write)
    val zacl = ACL.toZACL(acl)
    assert(acl.id.scheme === zacl.getId.getScheme)
    assert(acl.id.id === zacl.getId.getId)
    assert(acl.permission === zacl.getPerms)
  }

  test("ACL construction from Id") {
    val acl = Id(TestScheme, TestId) permit TestPerm
    acl match {
      case ACL(Id(TestScheme, TestId), TestPerm) => ()
    }
  }

  test("implicit construction of Id") {
    val id: Id = (TestScheme, TestId)
    id match {
      case Id(TestScheme, TestId) => ()
    }
    val acl = (TestScheme, TestId) permit TestPerm
    acl match {
      case ACL(Id(TestScheme, TestId), TestPerm) => ()
    }
  }
}
