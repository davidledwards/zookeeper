package com.nullinsight.zookeeper

import scala.language._

object ClientFixture {
  def apply(port: Int): Zookeeper = {
    val config = Configuration(("localhost", port) :: Nil)
    Zookeeper(config)
  }
}
