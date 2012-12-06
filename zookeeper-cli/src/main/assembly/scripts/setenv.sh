#!/bin/bash

if [[ -z "$JAVA_HOME" && -z "$JRE_HOME" ]]; then
   __JAVA=$(which java)
   if [[ -z "$__JAVA" ]]; then
      echo "neither JAVA_HOME nor JRE_HOME environment variable defined"
      exit 1
   fi
else
   __JAVA_HOME=${JRE_HOME:-"${JAVA_HOME}"}
   __JAVA=$__JAVA_HOME/bin/java
fi

if [[ ! (-f "$__JAVA" && -x "$__JAVA") ]]; then
    echo "$__JAVA not a suitable program"
    exit 1
fi
