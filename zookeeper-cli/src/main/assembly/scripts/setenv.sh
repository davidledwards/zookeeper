#!/bin/bash

if [[ -z "$SCALA_HOME" ]]; then
   __SCALA=$(which scala)
   if [[ -z $__SCALA ]]; then
      echo "SCALA_HOME environment variable not defined"
      exit 1
   fi
else
   __SCALA=$SCALA_HOME/bin/scala
fi

if [[ ! (-f "$__SCALA" && -x "$__SCALA") ]]; then
   echo "$__SCALA not a suitable program"
   exit 1
fi
