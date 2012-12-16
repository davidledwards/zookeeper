package com.loopfor.zookeeper

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language._
import scala.util._

object ZookeeperTest {
  def main(args: Array[String]) {
    println("press enter to start...")
    readLine()
    var cred: Credential = null
    val config: Configuration = Configuration {
      ("localhost", 2181) :: Nil
    } withTimeout {
      5 seconds
    } withWatcher { (event, session) =>
      println("event: " + event + ", session: " + session)
      // unsafe, but just need to capture for testing
      cred = session.credential
    }
    val zk = SynchronousZookeeper(config)
    try {
      val result = Try {
        zk.create("/test_", Array(), ACL.AnyoneAll, PersistentSequential)
      }
      result match {
        case Success(path) =>
          println("path is: " + path)
          val f = zk.async get path
          f onComplete {
            case Success((data, status)) => println("yay!!! data: " + data + ", status: " + status)
            case Failure(e) => println("oops: " + e)
          }
        case Failure(e) => println("oops: " + e)
      }
      result match {
        case Success(path) =>
          val (_, status) = zk watch {
            case e: StateEvent => println("watch fired: state change: " + e)
            case e: NodeEvent => println("watch fired: " + e)
          } get path
          println("status: " + status)
        case _ =>
      }
      zk children "/alpha" foreach { println _ }
      val (acl, status) = zk getACL "/"
      println("ACL for " + status + " is " + acl)
      println("press enter to create second instance...")
      readLine()
      println("using credential: " + cred)
      val zk2 = Zookeeper(config, cred)
      println("press enter to close second instance...")
      readLine()
      zk2.close()
      println("press enter to stop...")
      readLine()
    } finally {
      zk.close()
    }
  }
}