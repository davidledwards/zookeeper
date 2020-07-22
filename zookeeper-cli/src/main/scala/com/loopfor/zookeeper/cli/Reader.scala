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
package com.loopfor.zookeeper.cli

import com.loopfor.zookeeper._
import jline.console.ConsoleReader
import jline.console.completer.{Completer, ArgumentCompleter, StringsCompleter}
import scala.jdk.CollectionConverters._

object Reader {
  def apply(commands: Set[String], zk: Zookeeper) = new (Path => Seq[String]) {
    private val reader = new ConsoleReader
    reader.setBellEnabled(false)
    reader.setHistoryEnabled(true)
    reader.setPrompt("zk> ")

    private val delimiter = new ArgumentCompleter.WhitespaceArgumentDelimiter()
    private val first = new StringsCompleter(commands.asJava)

    def apply(context: Path): Seq[String] = {
      // Completer is added and removed with each invocation since completion is relative to the path context and the
      // context may change with each subsequent command. Keeping the same reader for the duration of the user session
      // is necessary to retain command history, otherwise ^p/^n operations have no effect.
      val completer = new ArgumentCompleter(delimiter, first, new PathCompleter(zk, context))
      completer.setStrict(false)
      reader.addCompleter(completer)
      try {
        val line = reader.readLine()
        if (line == null) Seq("quit") else Splitter.split(line)
      } finally {
        reader.removeCompleter(completer)
      }
    }
  }

  private class PathCompleter(zk: Zookeeper, context: Path) extends Completer {
    private implicit val _zk = zk

    def complete(buffer: String, cursor: Int, candidates: java.util.List[CharSequence]): Int = {
      val (node, prefix) = if (buffer == null)
        (Node(context), "")
      else {
        val path = context.resolve(buffer)
        if (buffer.endsWith("/")) (Node(path), "")
        else (Node(path.parentOption match {
          case Some(p) => p
          case _ => path
        }), path.name)
      }
      if (prefix == "." || prefix == "..") {
        candidates.add("/")
        buffer.size
      } else {
        try {
          val results = node.children().filter { _.name.startsWith(prefix) }
          if (results.size == 1 && results.head.name == prefix)
            candidates.add(results.head.name + "/")
          else
            results.sortBy { _.name }.foreach { c => candidates.add(c.name) }
          if (buffer == null) 0 else buffer.lastIndexOf('/') + 1
        } catch {
          case _: KeeperException => return -1
        }
      }
    }
  }
}
