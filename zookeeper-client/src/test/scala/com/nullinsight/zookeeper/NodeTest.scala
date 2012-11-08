package com.nullinsight.zookeeper

import org.scalatest.FunSuite
import scala.language._

class NodeTest extends FunSuite {
  test("normalized path when constructing node") {
    implicit val zk: Zookeeper = null

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
}