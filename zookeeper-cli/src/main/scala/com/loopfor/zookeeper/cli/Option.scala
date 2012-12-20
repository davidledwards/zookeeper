package com.loopfor.zookeeper.cli

object LongOption {
  def unapply(arg: String): Option[String] = if (arg startsWith "--") Some(arg drop 2) else None
}

object ShortOption {
  def unapply(arg: String): Option[String] = if (arg startsWith "-") Some(arg drop 1) else None
}

class OptionException(message: String, cause: Throwable) extends Exception(message, cause) {
  def this(message: String) = this(message, null)
}
