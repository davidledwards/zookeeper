package com.nullinsight.zookeeper

import scala.annotation.tailrec
import scala.collection.mutable.StringBuilder
import scala.language._
import scala.collection.immutable.Stack

trait Path {
  def path: String
  def parent: Path
  def parentOption: Option[Path]
  def parts: Seq[String]
  def resolve(path: String): Path
  def resolve(path: Path): Path
  def normalize: Path
  def isAbsolute: Boolean
}

object Path {
  def apply(path: String): Path = {
    new Impl(compress(path))
  }

  def unapply(path: Path): Option[String] = {
    if (path == null) None else Some(path.path)
  }

  private def apply(parts: Seq[String]): Path = {
    new Impl(parts mkString "/")
  }

  private class Impl(val path: String) extends Path {
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
      Path(stack.headOption match {
        case None => ""
        case Some("") => "/" + (stack.tail mkString "/")
        case _ => stack mkString "/"
      })
    }

    lazy val isAbsolute: Boolean = path.headOption == Some('/')

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
