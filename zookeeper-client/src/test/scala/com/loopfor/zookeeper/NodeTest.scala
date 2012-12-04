package com.loopfor.zookeeper

import java.util.UUID
import scala.language._

class NodeTest extends ZookeeperSuite {
  test("normalized path when constructing node") { root =>
    val tests = Seq(
          ("", ""),
          ("/", "/"),
          ("..", ".."),
          ("/..", "/"),
          ("/../..", "/"),
          ("foo", "foo"),
          ("foo/.", "foo"),
          ("foo/..", ""),
          ("./foo", "foo"),
          ("../foo", "../foo"),
          ("foo/../bar", "bar"),
          ("foo/./bar/../baz/.", "foo/baz"),
          ("foo/../../bar", "../bar"),
          ("/foo/./bar/../baz", "/foo/baz"),
          ("/foo/..", "/"),
          ("/foo/../..", "/"),
          ("/foo/../../bar", "/bar"))

    tests foreach {
      case (p, e) => assert(Node(p).path.path === e)
    }
  }

  test("path name") { root =>
    val tests = Seq(
          ("", ""),
          ("/", ""),
          ("..", ".."),
          ("/..", ""),
          ("/../..", ""),
          ("foo", "foo"),
          ("foo/.", "foo"),
          ("foo/..", ""),
          ("./foo", "foo"),
          ("../foo", "foo"),
          ("foo/../bar", "bar"),
          ("foo/./bar/../baz/.", "baz"),
          ("foo/../../bar", "bar"),
          ("/foo/./bar/../baz", "baz"),
          ("/foo/..", ""),
          ("/foo/../..", ""),
          ("/foo/../../bar", "bar"))

    tests foreach {
      case (p, e) => println(Node(p).name); assert(Node(p).name === e)
    }
  }

  test("create persistent node") { root =>
    val path = root + "foo"
    val node = Node(path).create(Array(), ACL.EveryoneAll, Persistent)
    assert(node.path === path)
  }

  test("set and get") { root =>
    val node = Node(root + "foo").create(Array(), ACL.EveryoneAll, Persistent)
    val in = randomBytes()
    node.set(in, Some(0))
    val (out, status) = node.get()
    assert(in === out)
  }

  test("set and get without version") { root =>
    val node = Node(root + "foo").create(Array(), ACL.EveryoneAll, Persistent)
    val in = randomBytes()
    node.set(in, None)
    val (out, status) = node.get()
    assert(in === out)
  }

  test("set with wrong version") { root =>
    val node = Node(root + "foo").create(Array(), ACL.EveryoneAll, Persistent)
    intercept[BadVersionException] {
      node.set(randomBytes(), Some(Int.MaxValue))
    }
  }

  test("node exists") { root =>
    val node = Node(root + "foo").create(Array(), ACL.EveryoneAll, Persistent)
    assert(node.exists().isDefined)
  }

  test("node does not exist") { root =>
    assert(Node(root + "foo").exists().isEmpty)
  }

  private def randomBytes() = UUID.randomUUID().toString.getBytes("UTF-8")
}