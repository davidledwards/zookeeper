#!/bin/bash
#
# Copyright 2013 David Edwards
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

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
