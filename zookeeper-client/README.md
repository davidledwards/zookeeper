# Scala API for ZooKeeper
A functional API layered over the ZooKeeper client.

## Build Instructions
In order to build the corresponding artifacts, you must install [Java 1.7](http://www.java.com/en/download/) or
higher and [sbt 0.13.2](http://www.scala-sbt.org/0.13.2/docs/Getting-Started/Setup.html).

In the root directory `zookeeper-client`, the following command will build the project and install in your local Ivy
repository:
```
sbt publishLocal
```

API documentation is automatically generated and deployed with `publishLocal`, but may also be generated via:
```
sbt doc
```

### Including as Dependency
`zookeeper-client` is built against Scala 2.10.4.

#### sbt
```
libraryDependencies += "com.loopfor.zookeeper" %% "zookeeper-client" % "1.2"
```

#### Maven
```
<dependency>
   <groupId>com.loopfor.zookeeper</groupId>
   <artifactId>zookeeper-client_2.10</artifactId>
   <version>1.2</version>
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
