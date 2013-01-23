/*
 * Copyright 2013 David Edwards
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.loopfor.zookeeper

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

  test("path resolution using + operator") {
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
      case (l, r, e) => assert((Path(l).resolve(r)).path === e)
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

  test("parent of path as option") {
    val testsOk = Seq(
          ("foo/bar", "foo"),
          ("/foo", "/"),
          ("/foo/bar", "/foo"))

    testsOk foreach {
      case (p, e) => assert(Path(p).parentOption.get.path === e)
    }

    val testsError = Seq("", "/", "foo")

    testsError foreach {
      case (p) => assert(Path(p).parentOption === None)
    }
  }

  test("deconstruct paths") {
    val tests = Seq(
          ("", Seq()),
          ("/", Seq("")),
          ("foo/bar", Seq("foo", "bar")),
          ("/foo/bar", Seq("", "foo", "bar")))

    tests foreach {
      case (p, e) => Path(p) match {
        case Path(parts @ _*) => assert(parts === e)
      }
    }
  }
}
