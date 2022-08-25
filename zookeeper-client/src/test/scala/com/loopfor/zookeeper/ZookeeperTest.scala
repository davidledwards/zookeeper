/*
 * Copyright 2022 David Edwards
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

class ZookeeperTest extends ZookeeperSuite {
  test("valid session") { _ =>
    val session = zk.session()
    assert(session !== null)

    // It is somewhat questionable to assert that the session is connected since the state
    // could change by the time this assertion is executed. However, given that the test is
    // executing under a controlled setup, it seemes reasonable to make this assumption.
    assert(session.state === ConnectedState)

    // Assumption is that session id is nonzero.
    val cred = session.credential
    assert(cred !== null)
    assert(cred.id !== 0)

    // Assumption is that session timeout is positive.
    val timeout = session.timeout
    assert(timeout !== null)
    assert(timeout.toNanos > 0)
  }
}
