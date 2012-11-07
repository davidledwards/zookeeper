package com.nullinsight.zookeeper

import scala.annotation.tailrec
import scala.collection.mutable.StringBuilder
import scala.language._
import scala.collection.immutable.Stack

trait Path {
  def parent: Path
  def elements: Seq[String]
  def resolve(path: String*): Path
}

object Path {
  def apply(path: String): Path = {
    // compress path first
    null
  }

  def compress(path: String): String = {
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
    (to.lastOption match {
      case Some('/') => to.dropRight(1)
      case _ => to
    }).toString
  }

  def parse(path: String): Seq[String] = {
    compress(path) split '/'
  }

  def normalize(path: String): String = {
    @tailrec def process(parts: Seq[String], stack: Stack[String]): Stack[String] = {
      parts.headOption match {
        case Some(part) =>
          process(parts.tail, part match {
            case ".." => stack.headOption match {
              case Some(top) => if (top == "") stack else if (top == "..") stack.push(part) else stack.pop
              case _ => stack.push(part)
            }
            case "." => stack
            case _ => stack.push(part)
          })
        case _ => stack
      }
    }
    val stack = process(parse(path), Stack()).reverse
    stack.head match {
      case "" => "/" + (stack.tail mkString "/")
      case _ => stack mkString "/"
    }
  }
}

object PathTest {
  def main(args: Array[String]) {
    fix("")
    fix("/foo")
    fix("foo/bar")
    fix("foo//bar")
    fix("//foo///bar//")
    parse("/foo/bar/baz/")
    parse("foo//bar//")
    norm("foo/../bar/./baz")
    norm("foo/../bar/./../baz")
    norm("foo/../bar/./../../baz")
    norm("/foo/../bar/./../../baz")
    norm("../../../")
    norm("/../../../")
    norm("")
    norm("foo")
  }

  private def fix(path: String) {
    import Path._
    println(path + " --> " + compress(path))
  }

  private def parse(path: String) {
    println(path + " ==> " + Path.parse(path))
  }

  private def norm(path: String) {
    import Path._
    println(path + " ~~> " + normalize(path))
  }
}