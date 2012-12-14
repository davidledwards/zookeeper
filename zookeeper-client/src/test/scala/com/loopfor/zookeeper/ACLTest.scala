package com.loopfor.zookeeper

import com.loopfor.zookeeper.ACL._
import org.scalatest.FunSuite
import scala.language._

class ACLTest extends FunSuite {
  private val TestScheme = "testscheme"
  private val TestId = "testid"
  private val TestPerm = Read | Write | Create

  test("Id pattern matching") {
    Id("foo", "bar") match {
      case Id("foo", "bar") =>
    }
  }

  test("Id pattern matching with strings") {
    val tests = Seq(
          ("foo:bar", "foo", "bar"),
          ("foo:", "foo", ""),
          (":bar", "", "bar"),
          (":", "", ""))

    tests foreach { case (s, scheme, id) =>
      s match {
        case Id(s, i) =>
          assert(s === scheme)
          assert(i === id)
      }
    }

    "foo" match {
      case Id(_, _) => fail()
      case _ =>
    }
  }

  test("Id pattern matching with invalid strings") {
    "foo" match {
      case Id(_, _) => fail()
      case _ =>
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

  test("ACL pattern matching with strings") {
    val tests = Seq(
          ("foo:bar=", 0),
          ("foo:bar=r", Read),
          ("foo:bar=rw", Read | Write),
          ("foo:bar=rwc", Read | Write | Create),
          ("foo:bar=rwcd", Read | Write | Create | Delete),
          ("foo:bar=rwcda", Read | Write | Create | Delete | Admin),
          ("foo:bar=*", Read | Write | Create | Delete | Admin))

    tests foreach { case (acl, permission) =>
      acl match {
        case ACL(Id(_), p) => assert(p === permission)
      }
    }
  }

  test("ACL pattern matching with invalid strings") {
    val tests = Seq(
          "foo:bar",
          "foo:bar=x",
          "foo:bar=rwcdax")

    tests foreach { acl => acl match {
      case ACL(_, _) => fail()
      case _ =>
    }}
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
