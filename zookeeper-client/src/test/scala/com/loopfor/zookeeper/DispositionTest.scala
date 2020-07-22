/*
 * Copyright 2020 David Edwards
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
import org.scalatest.funsuite.AnyFunSuite
import scala.concurrent.duration._

class DispositionTest extends AnyFunSuite {
  test("conditional dispositions with TTL values") {
    val tests = Seq(
      (PersistentTimeToLive(Duration.Zero), 0L),
      (PersistentTimeToLive(1.second), 1000L),
      (PersistentSequentialTimeToLive(Duration.Zero), 0L),
      (PersistentSequentialTimeToLive(1.second), 1000L)
    )

    tests.foreach { case (disp, millis) =>
      assert(disp.ttl.toMillis === millis)
    }
  }
}
