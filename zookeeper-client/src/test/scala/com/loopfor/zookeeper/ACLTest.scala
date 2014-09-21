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

import com.loopfor.zookeeper.ACL._
import org.scalatest.FunSuite

class ACLTest extends FunSuite {
  test("construction of valid ACL instances") {
    val tests = Seq(
      ("world:anyone=", WorldId, 0),
      ("world:anyone=r", WorldId, Read),
      ("world:anyone=rw", WorldId, Read | Write),
      ("world:anyone=rwc", WorldId, Read | Write | Create),
      ("world:anyone=rwcd", WorldId, Read | Write | Create | Delete),
      ("world:anyone=rwcda", WorldId, Read | Write | Create | Delete | Admin),
      ("world:anyone=*", WorldId, All),
      ("auth=*", AuthId, All),
      ("auth:=*", AuthId, All),
      ("digest:username:password=*", DigestId("username", "password"), All),
      ("digest:username:=*", DigestId("username", ""), All),
      ("digest::password=*", DigestId("", "password"), All),
      ("digest::=*", DigestId("", ""), All),
      ("host:foo.com=*", HostId("foo.com"), All),
      ("ip:1.2.3.4=*", IpId("1.2.3.4", 32), All),
      ("ip:1.2.3.4/0=*", IpId("1.2.3.4", 0), All),
      ("ip:1.2.3.4/32=*", IpId("1.2.3.4", 32), All),
      ("ip:1:2:3:4:5:6:7:8=*", IpId("1:2:3:4:5:6:7:8", 128), All),
      ("ip:1:2:3:4:5:6:7:8/0=*", IpId("1:2:3:4:5:6:7:8", 0), All),
      ("ip:1:2:3:4:5:6:7:8/128=*", IpId("1:2:3:4:5:6:7:8", 128), All)
    )

    tests foreach { case (s, id, permission) =>
      val acl = ACL(s)
      assert(acl.id === id)
      assert(acl.permission === permission)
      assert(acl.getId === id.zid)
      assert(acl.getPerms === permission)
    }
  }

  test("construction of invalid ACL instances") {
    val tests = Seq(
      "world:anyone",
      "world:anyone=x",
      "world=rw"
    )

    tests foreach { s =>
      intercept[IllegalArgumentException] { ACL(s) }
    }
  }

  test("ACL pattern matching") {
    ACL("world:anyone=r") match {
      case ACL(WorldId, Read) =>
      case _ => fail()
    }
    ACL("auth=r") match {
      case ACL(AuthId, Read) =>
      case _ => fail()
    }
    ACL("digest:username:password=r") match {
      case ACL(DigestId("username", "password"), Read) =>
      case _ => fail()
    }
    ACL("host:foo.com=r") match {
      case ACL(HostId("foo.com"), Read) =>
      case _ => fail()
    }
    ACL("ip:1.2.3.4/24=r") match {
      case ACL(IpId("1.2.3.4", 24), Read) =>
      case _ => fail()
    }
  }

  test("ACL construction from Id") {
    val acl = Id("world", "anyone").permit(Read | Write)
    assert(acl.id === Id("world", "anyone"))
    assert(acl.permission === (Read | Write))
  }
}
