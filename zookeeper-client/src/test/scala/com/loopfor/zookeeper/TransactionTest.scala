package com.loopfor.zookeeper

import scala.concurrent.duration._
import scala.language._

object TransactionTest {
  def main(args: Array[String]) {
    val config = Configuration(("localhost", 2181) :: Nil) withTimeout (5 seconds) withWatcher { (e, s) =>
      println("event: " + e)
    }
    val zk = SynchronousZookeeper(config)
    try {
      val ops = CreateOperation("/tr", Array(), ACL.EveryoneAll, PersistentSequential) ::
                CheckOperation("/foo", None) ::
                CheckOperation("/foobar", None) ::
                CreateOperation("/tr", Array(), ACL.EveryoneAll, PersistentSequential) ::
                Nil
      zk transact ops match {
        case Right(rs) =>
          println("okay")
          rs foreach { println _ }
        case Left(es) =>
          println("error")
          es foreach { e => println(e) }
      }
    } finally {
      zk.close()
    }
  }
}