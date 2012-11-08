package com.nullinsight.zookeeper

import org.scalatest.FunSuite
import scala.language._

class PathTest extends FunSuite {
  test("path compression") {
    val tests = Seq(
          ("", ""),
          ("/", "/"),
          ("//", "/"),
          ("foo/bar", "foo/bar"),
          ("foo/bar/", "foo/bar"),
          ("foo//bar", "foo/bar"),
          ("//foo/bar", "/foo/bar"),
          ("foo//bar//", "foo/bar"))

    tests foreach {
      case (p, e) => assert(Path(p).path === e)
    }
  }

  test("path parsing") {
    val tests = Seq(
          ("", Seq()),
          ("/", Seq("")),
          ("foo/bar", Seq("foo", "bar")),
          ("/foo/bar", Seq("", "foo", "bar")))

    tests foreach {
      case (p, e) => assert(Path(p).parts === e)
    }
  }

  test("path normalization") {
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
      case (p, e) => assert(Path(p).normalize.path === e)
    }
  }

  test("path resolution") {
    val tests = Seq(
          ("", "", ""),
          ("", "/", "/"),
          ("/", "", "/"),
          ("foo", "", "foo"),
          ("", "foo", "foo"),
          ("foo", "bar", "foo/bar"),
          ("foo", "/bar", "/bar"),
          ("/foo", "", "/foo"),
          ("/foo", "bar", "/foo/bar"),
          ("/foo", "/bar", "/bar"))

    tests foreach {
      case (l, r, e) => assert(Path(l).resolve(r).path === e)
    }
  }

  test("absolute/relative paths") {
    val tests = Seq(
          ("", false),
          ("/", true),
          ("foo", false),
          ("/foo", true))

    tests foreach {
      case (p, e) => assert(Path(p).isAbsolute === e)
    }
  }

  test("parent of path") {
    val testsOk = Seq(
          ("foo/bar", "foo"),
          ("/foo", "/"),
          ("/foo/bar", "/foo"))

    testsOk foreach {
      case (p, e) => assert(Path(p).parent.path === e)
    }

    val testsError = Seq("", "/", "foo")

    testsError foreach {
      case (p) => intercept[NoSuchElementException] { Path(p).parent }
    }
  }
}
