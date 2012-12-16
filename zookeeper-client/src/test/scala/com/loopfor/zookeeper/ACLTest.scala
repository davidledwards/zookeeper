package com.loopfor.zookeeper

import com.loopfor.zookeeper.ACL._
import org.scalatest.FunSuite
import scala.language._

class ACLTest extends FunSuite {
  test("Id pattern matching") {
    Id("foo", "bar") match {
      case Id("foo", "bar") =>
    }
  }

  test("Id parsing") {
    val tests = Seq(
          ("foo:bar", "foo", "bar"),
          ("foo:", "foo", ""),
          (":bar", "", "bar"),
          (":", "", ""))

    tests foreach { case (s, scheme, id) =>
      Id parse s match {
        case Some(i) =>
          assert(i.scheme === scheme)
          assert(i.id === id)
        case _ => fail(s)
      }
    }
  }

  test("Id parsing with invalid strings") {
    Id parse "foo" match {
      case Some(_) => fail()
      case _ =>
    }
  }

  test("equivalence of Id with underlying ZooKeeper Id") {
    val id = Id("foo", "bar")
    val zid = Id.toId(id)
    assert(id.scheme === zid.getScheme())
    assert(id.id === zid.getId())
  }

  test("ACL pattern matching") {
    ACL("foo", "bar", Read) match {
      case ACL(Id("foo", "bar"), Read) =>
    }
    ACL(Id("foo", "bar"), All) match {
      case ACL(Id("foo", "bar"), All) =>
    }
  }

  test("ACL parsing") {
    val tests = Seq(
          ("foo:bar=", 0),
          ("foo:bar=r", Read),
          ("foo:bar=rw", Read | Write),
          ("foo:bar=rwc", Read | Write | Create),
          ("foo:bar=rwcd", Read | Write | Create | Delete),
          ("foo:bar=rwcda", Read | Write | Create | Delete | Admin),
          ("foo:bar=*", Read | Write | Create | Delete | Admin))

    tests foreach { case (s, permission) =>
      ACL parse s match {
        case Some(acl) => assert(acl.permission === permission)
        case _ => fail(s)
      }
    }
  }

  test("ACL parsing with invalid strings") {
    val tests = Seq(
          "foo:bar",
          "foo:bar=x",
          "foo:bar=rwcdax")

    tests foreach { s =>
      ACL parse s match {
        case Some(_) => fail(s)
        case _ =>
      }
    }
  }

  test("equivalence of ACL with underlying ZooKeeper ACL") {
    val acl = ACL("foo", "bar", Read | Write)
    val zacl = ACL.toZACL(acl)
    assert(acl.id.scheme === zacl.getId.getScheme)
    assert(acl.id.id === zacl.getId.getId)
    assert(acl.permission === zacl.getPerms)
  }

  test("ACL construction from Id") {
    val perm = Read | Write
    val acl = Id("foo", "bar") permit perm
    acl match {
      case ACL(Id("foo", "bar"), perm) =>
    }
  }

  test("implicit construction of Id") {
    val perm = Create | Delete
    val id: Id = ("foo", "bar")
    id match {
      case Id("foo", "bar") =>
    }
    val acl = ("foo", "bar") permit perm
    acl match {
      case ACL(Id("foo", "bar"), perm) =>
    }
  }

  test("equality and hash") {
    val a = Id("foo", "bar")
    val b = Id("foo", "bar")
    assert(a === b)
    assert(a.hashCode === b.hashCode)

    val c = Id("foo", "baz")
    assert(a != c)
    assert(a.hashCode != c.hashCode)
  }
}
