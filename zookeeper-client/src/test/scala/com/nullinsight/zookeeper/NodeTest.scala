package com.nullinsight.zookeeper

import scala.language._
import java.util.UUID

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

  test("create persistent node") { root =>
    val path = root.resolve("foo")
    val node = Node(path).create(Array(), ACL.EveryoneAll, Persistent)
    assert(node.path === path)
  }

  test("set and get") { root =>
    val path = root.resolve("foo")
    val node = Node(path).create(Array(), ACL.EveryoneAll, Persistent)
    val in = randomBytes()
    node.set(in, Some(0))
    val (out, status) = node.get()
    assert(in === out)
  }

  test("set and get without version") { root =>
    val path = root.resolve("foo")
    val node = Node(path).create(Array(), ACL.EveryoneAll, Persistent)
    val in = randomBytes()
    node.set(in, None)
    val (out, status) = node.get()
    assert(in === out)
  }

  test("set with wrong version") { root =>
    val path = root.resolve("foo")
    val node = Node(path).create(Array(), ACL.EveryoneAll, Persistent)
    val in = randomBytes()
    intercept[BadVersionException] {
      node.set(in, Some(Int.MaxValue))
    }
  }

  private def randomBytes() = UUID.randomUUID().toString.getBytes("UTF-8")
}