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

class IdTest extends FunSuite {
  test("construction of valid Id instances") {
    val tests = Seq(
      ("world:anyone", "world", "anyone"),
      ("auth", "auth", ""),
      ("auth:", "auth", ""),
      ("digest:username:password", "digest", "username:password"),
      ("digest:username:", "digest", "username:"),
      ("digest::password", "digest", ":password"),
      ("digest::", "digest", ":"),
      ("host:foo.com", "host", "foo.com"),
      ("ip:1.2.3.4", "ip", "1.2.3.4/32"),
      ("ip:1.2.3.4/0", "ip", "1.2.3.4/0"),
      ("ip:1.2.3.4/32", "ip", "1.2.3.4/32"),
      ("ip:1:2:3:4:5:6:7:8", "ip", "1:2:3:4:5:6:7:8/128"),
      ("ip:1:2:3:4:5:6:7:8/0", "ip", "1:2:3:4:5:6:7:8/0"),
      ("ip:1:2:3:4:5:6:7:8/128", "ip", "1:2:3:4:5:6:7:8/128")
    )

    tests foreach { case (s, scheme, id) =>
      val i = Id(s)
      assert(i.scheme === scheme)
      assert(i.id === id)
      assert(i.zid.getScheme === scheme)
      assert(i.zid.getId === id)
    }
  }

  test("construction of invalid Id instances") {
    val tests = Seq(
      "world",
      "world:",
      "world:bad",
      "auth:bad",
      "digest",
      "digest:",
      "host",
      "host:",
      "ip",
      "ip:",
      "ip:1.2.3.4/",
      "ip:1.2.3.4/-1",
      "ip:1.2.3.4/33",
      "ip:1.2.3.4/bad",
      "ip:1:2:3:4:5:6:7:8/",
      "ip:1:2:3:4:5:6:7:8/-1",
      "ip:1:2:3:4:5:6:7:8/129",
      "ip:1:2:3:4:5:6:7:8/bad",
      "ip:bad",
      "bad"
    )

    tests foreach { s =>
      intercept[IllegalArgumentException] { Id(s) }
    }
  }

  test("construction using Id and matching with specific Id") {
    Id("world:anyone") match {
      case WorldId =>
      case _ => fail()
    }
    Id("auth") match {
      case AuthId =>
      case _ => fail()
    }
    Id("digest:username:password") match {
      case DigestId("username", "password") =>
      case _ => fail()
    }
    Id("host:foo.com") match {
      case HostId("foo.com") =>
      case _ => fail()
    }
    Id("ip:1.2.3.4/24") match {
      case IpId("1.2.3.4", 24) =>
      case _ => fail()
    }
  }

  test("construction using specific Id and matching with Id") {
    WorldId match {
      case Id("world", "anyone") =>
      case _ => fail()
    }
    AuthId match {
      case Id("auth", "") =>
      case _ => fail()
    }
    DigestId("username", "password") match {
      case Id("digest", "username:password") =>
      case _ => fail()
    }
    HostId("foo.com") match {
      case Id("host", "foo.com") =>
      case _ => fail()
    }
    IpId("1.2.3.4", 24) match {
      case Id("ip", "1.2.3.4/24") =>
      case _ => fail()
    }
  }

  test("implicit construction of Id") {
    val id: Id = ("world", "anyone")
    assert(id.scheme === "world")
    assert(id.id === "anyone")
  }
}
