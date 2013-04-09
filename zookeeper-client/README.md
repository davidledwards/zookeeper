# Scala API for ZooKeeper
A functional API layered over the ZooKeeper client.

## Build Instructions
In order to build the corresponding artifacts, you must install [Java 1.6](http://www.java.com/en/download/index.jsp) or
higher and [Maven 3.0](http://maven.apache.org/download.cgi) or higher.

In the root directory `zookeeper-client`, the following command will build the project and install in your local Maven
repository:
```
mvn install
```

API documentation can also be generated via:
```
mvn scala:doc
```

## Including as Maven Dependency
```
<dependency>
   <groupId>com.loopfor.zookeeper</groupId>
   <artifactId>zookeeper-client</artifactId>
</dependency>
```

## License
Copyright 2013 David Edwards

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
