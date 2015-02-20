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

import scala.annotation.tailrec
import scala.collection.mutable.StringBuilder
import scala.language._

/**
 * Represents an ''absolute'' or ''relative'' path to a node in ZooKeeper.
 * 
 * An ''absolute'' path starts with the `/` character. All other forms are considered ''relative''. Paths are virtually
 * identical to those on Unix file systems and may include both `.` and `..` parts, indicating the current and parent node,
 * respectively.
 * 
 * Examples:
 * {{{
 * "/"
 * "../foo"
 * "/foo/bar"
 * "./foo/../bar"
 * }}}
 */
trait Path {
  /**
   * Returns the name of the last part of the path.
   * 
   * Examples:
   * {{{
   * "" parent is ""
   * "/" parent is ""
   * "/foo" parent is "/"
   * "/foo/bar" parent is "/foo"
   * "foo/bar" parent is "foo"
   * }}}
   * 
   * @return the last part of [[path]], which is an empty string if [[path]] is either `""` or `"/"`.
   */
  def name: String

  /**
   * Returns the path.
   * 
   * @return the path
   */
  def path: String

  /**
   * Returns the parent path.
   * 
   * @return the parent of [[path]]
   * @throws NoSuchElementException if removal of [[name]] from [[path]] yields `""` or `"/"`
   */
  def parent: Path

  /**
   * Returns the parent path wrapped in an `Option`.
   * 
   * @return a `Some` containing the parent of [[path]] or `None` if removal of [[name]] from [[path]] yields `""` or `"/"`
   */
  def parentOption: Option[Path]

  /**
   * Returns a sequence containing the parts of the path.
   * 
   * Parts represent the node names sandwiched between `/` characters. An ''absolute'' path, which is prefixed with `/`, always
   * yields a sequence containing an empty string as the first element. The path `""` contains no parts, hence an empty
   * sequence is returned. In all other cases, parts are non-empty strings.
   * 
   * Examples:
   * {{{
   * "" parts ()
   * "/" parts ("/")
   * "/foo/bar" parts ("", "foo", "bar")
   * "foo/bar" parts ("foo", "bar")
   * }}}
   * 
   * @return a sequence containing the parts of [[path]]
   */
  def parts: Seq[String]

  /**
   * Resolves the given `path` relative to this path.
   * 
   * Path resolution works as follows:
   *  - if `path` is empty, return this [[path]]
   *  - if `path` starts with `/`, return `path`
   *  - if this [[path]] is empty, return `path`
   *  - otherwise, return this [[path]] + `/` + `path`
   * 
   * @param path the path to resolve against this [[path]]
   * @return a new path in which the given `path` is resolved relative to this [[path]]
   */
  def resolve(path: String): Path

  /**
   * Resolves the given `path` relative to this path.
   * 
   * @param path the path to resolve against this [[path]]
   * @return a new path in which the givne `path` is resolved relative to this [[path]]
   */
  def resolve(path: Path): Path

  /**
   * Returns the normalized form of this path.
   * 
   * The normalization process entails the removal of `.` and `..` parts where possible.
   * 
   * Examples:
   * {{{
   * "/.." normalizes to "/"
   * "/foo/.." normalizes to "/"
   * "/foo/../bar" normalizes to "/bar"
   * "./foo" normalizes to "foo"
   * "foo/." normalizes to "foo"
   * "foo/.." normalizes to ""
   * "foo/./bar" normalizes to "foo/bar"
   * "foo/../bar" normalizes to "bar"
   * }}}
   * 
   * @return the normalized form of this path
   */
  def normalize: Path

  /**
   * Returns `true` if the path is absolute.
   */
  def isAbsolute: Boolean
}

/**
 * Constructs and deconstructs [[Path]] values.
 */
object Path {
  /**
   * Constructs a new path using the given path string.
   * 
   * Path construction entails removal of extraneous `/` characters, including those at the end of `path` so long as `path`
   * itself is not equivalent to `"/"`.
   * 
   * @param path the path string
   * @return a new path with the given `path` string
   */
  def apply(path: String): Path = new Impl(compress(path))

  /**
   * Used in pattern matching to deconstruct a path.
   * 
   * @param path selector value
   * @return a `Some` containing the [[Path.parts parts]] of `path` or `None` if `path` is `null`
   */
  def unapplySeq(path: Path): Option[Seq[String]] =
    if (path == null) None else Some(path.parts)

  private def apply(parts: Seq[String]): Path = new Impl(parts mkString "/")

  private class Impl(val path: String) extends Path {
    lazy val name: String = parts.lastOption match {
      case Some(p) => p
      case _ => ""
    }

    lazy val parent: Path = parentOption match {
      case Some(p) => p
      case _ => throw new NoSuchElementException("no parent node")
    }

    lazy val parentOption: Option[Path] = {
      if (parts.size > 1) {
        val _parts = parts dropRight 1
        Some(Path(_parts.last match {
          case "" => "/"
          case _ => _parts mkString "/"
        }))
      } else
        None
    }

    lazy val parts: Seq[String] = parse(path)

    def resolve(path: String): Path = {
      Path(path.headOption match {
        case None => this.path
        case Some('/') => path
        case _ if this.path.isEmpty => path
        case _ => this.path + "/" + path
      })
    }

    def resolve(path: Path): Path = resolve(path.path)

    lazy val normalize: Path = {
      @tailrec def reduce(parts: Seq[String], stack: List[String]): List[String] = {
        parts.headOption match {
          case Some(part) =>
            reduce(parts.tail, part match {
              case ".." => stack.headOption match {
                case Some(top) => if (top == "") stack else if (top == "..") part :: stack else stack.tail
                case _ => part :: stack
              }
              case "." => stack
              case _ => part :: stack
            })
          case _ => stack
        }
      }
      val stack = reduce(parse(path), List()).reverse
      Path(stack.headOption match {
        case None => ""
        case Some("") => "/" + (stack.tail mkString "/")
        case _ => stack mkString "/"
      })
    }

    lazy val isAbsolute: Boolean = path.headOption == Some('/')

    override def toString: String = path

    override def equals(that: Any): Boolean = that match {
      case _that: Path => _that.path == path
      case _ => false
    }

    override def hashCode: Int = path.hashCode
  }

  private def compress(path: String): String = {
    @tailrec def munch(path: Seq[Char]): Seq[Char] = {
      path.headOption match {
        case Some('/') => munch(path.tail)
        case _ => path
      }
    }
    @tailrec def collapse(path: Seq[Char], to: StringBuilder): StringBuilder = {
      path.headOption match {
        case Some(c) => collapse(if (c == '/') munch(path.tail) else path.tail, to + c)
        case _ => to
      }
    }
    val to = collapse(path.seq, new StringBuilder)
    (if (to.size > 1 && to.last == '/') to.dropRight(1) else to).toString
  }

  private def parse(path: String): Seq[String] = {
    compress(path) match {
      case "" => Seq()
      case "/" => Seq("")
      case p => p split '/'
    }
  }
}
