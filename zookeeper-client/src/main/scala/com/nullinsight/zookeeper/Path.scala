package com.nullinsight.zookeeper

import scala.annotation.tailrec
import scala.collection.mutable.StringBuilder
import scala.language._
import scala.collection.immutable.Stack

trait Path {
  def parent: Path
  def parts: Seq[String]
  def resolve(path: String): Path
  def normalize: Path
}

object Path {
  def apply(path: String): Path = {
    new Impl(compress(path))
  }

  private def apply(parts: Seq[String]): Path = {
    new Impl(parts mkString "/")
  }

  private class Impl(path: String) extends Path {
    lazy val parent: Path = {
      parts match {
        case Seq() => throw new NoSuchElementException("no parent node")
        case _ => Path(parts dropRight 1)
      }
    }

    lazy val parts: Seq[String] = parse(path)

    def resolve(path: String): Path = {
      Path(path.headOption match {
        case Some('/') => path
        case _ => this.path + "/" + path
      })
    }

    def normalize: Path = {
      @tailrec def reduce(parts: Seq[String], stack: Stack[String]): Stack[String] = {
        parts.headOption match {
          case Some(part) =>
            reduce(parts.tail, part match {
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
      val stack = reduce(parse(path), Stack()).reverse
      Path(stack.head match {
        case "" => "/" + (stack.tail mkString "/")
        case _ => stack mkString "/"
      })
    }

    override def toString: String = path
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
    (to.lastOption match {
      case Some('/') => to.dropRight(1)
      case _ => to
    }).toString
  }

  private def parse(path: String): Seq[String] = compress(path) split '/'
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
    parse("")
    parse("foo/bar")
    parse("/foo/bar")
    norm("foo/../bar/./baz")
    norm("foo/../bar/./../baz")
    norm("foo/../bar/./../../baz")
    norm("/foo/../bar/./../../baz")
    norm("../../../")
    norm("/../../../")
    norm("")
    norm("foo")
    println("--- resolve ---")
    resolve("foo/bar", "baz")
    resolve("foo/bar", "/baz")
    resolve("foo/bar", "../baz")
  }

  private def fix(path: String) {
    println(path + " --> " + Path(path))
  }

  private def parse(path: String) {
    val parts = Path(path).parts
    println(path + " ==> " + parts + ", size=" + parts.size)
  }

  private def norm(path: String) {
    println(path + " ~~> " + Path(path).normalize)
  }

  private def resolve(a: String, b: String) {
    println(a + " + " + b + " --> " + Path(a).resolve(b))
  }
}