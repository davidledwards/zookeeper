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
package com.loopfor.zookeeper.cli

import org.scalatest.FunSuite
import scala.language._

class SplitterTest extends FunSuite {
  test("arguments without quotes") {
    val tests = Seq(
          ("foo", Seq("foo")),
          ("foo bar", Seq("foo", "bar")),
          ("foo bar --this -x --that --", Seq("foo", "bar", "--this", "-x", "--that", "--")))

    tests foreach { case (s, args) =>
      val _args = Splitter split s
      assert(_args === args)
    }
  }
}
