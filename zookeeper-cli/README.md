# ZooKeeper CLI
A new command line program designed to replace `zkCli.sh`, which comes with the ZooKeeper distribution. This program is much
cleaner and easier to use.

## Build Instructions
In order to build the corresponding artifacts, you must install [Java 1.7](http://www.java.com/en/download/) or
higher and [sbt 0.13.2](http://www.scala-sbt.org/0.13.2/docs/Getting-Started/Setup.html).

Note that this project depends on `zookeeper-client`, so it must be built before proceeding.

In the root directory `zookeeper-cli`, the following command will build the project and install in your local Ivy repository:
```
sbt publishLocal
```

## Installing the CLI
A local build will install an assembly of the CLI in your local Ivy repository, complete with all requisite dependencies.
The only exception is Java 1.7, which must be separately installed on the target machine. The Scala runtime is included in the
assembly, so there is no need for explicit installation.

The location of the assembly is `~/.ivy2/local/com.loopfor.zookeeper/zookeeper-cli/1.2/tars/`.
* `zookeeper-cli.tar.gz`

Alternatively, these artifacts can be downloaded from the
[Sonatype Repository](https://oss.sonatype.org/content/groups/public/com/loopfor/zookeeper/zookeeper-cli/1.2/).

Unpacking this assembly will produce the following output:
```
zookeeper-cli-1.2/
+ bin/
  + zk
  + zk.bat
  + ...
+ lib/
  + ...
```

For convenience, you might place `zookeeper-cli-1.2/bin/zk` in your PATH or create an alias.

## Helpful Tips
The `zk` program uses [JLine](https://github.com/jline/jline2) for console input similar to what you might expect in
modern shells. For example, `^p` and `^n` can be used to scroll back and forth through the command history.

It also supports TAB auto-completion as follows:
* Pressing TAB on the first argument attempts to auto-complete as though the user were entering a command. Therefore, pressing
TAB on an empty line produces a list of all available commands.
* Pressing TAB on the second, and all subsequent, arguments attempts to auto-complete as though the user were entering a
ZooKeeper node path. This feature was designed to very closely mimic file system auto-completion in the shell. It is
sensitive to the current path context, so you only see relevant subpaths.

## Node Paths
Unlike `zkCli.sh`, the `zk` program supports relative node paths and the ability to essentially _cd_ to a path, thus setting
a _current working path_. In all commands requiring paths, both absolute and relative forms may be given. An absolute path
starts with `/`, so the _current working path_ is ignored. Otherwise, the relative path is resolved in the context of the
_current working path_.

Both `.` and `..` can be used in path expressions.

## Invoking `zk`
Show `zk` usage information.
```
$ zk
```

Connect to a ZooKeeper cluster by specifying at least one of its servers.
```
$ zk localhost:2181
```

Specify a root path, akin to `chroot`.
```
$ zk -p /foo localhost:2181
```

Allow connection to a ZooKeeper server even if a quorum has not been established.
```
$ zk -r localhost:2181
```

Execute a command without entering the `zk` shell.
```
$ zk -c "get foo/bar" localhost:2181
```

The port number may be omitted if ZooKeeper servers are listening on port `2181`.
```
$ zk foo.com bar.com
```

## Using `zk`
### Getting help
Get list of all commands.
```
zk> help
```

Get help for a specific command.
```
zk> help ls
```

### Working with node paths
Change the current working path.
```
zk> cd foo/bar
```

Change the current working path to the parent.
```
zk> cd ..
```

Return to previous working path.
```
zk> cd -
```

Change the current working path to `/`.
```
zk> cd
```

Display the current working path.
```
zk> pwd
```

### Listing nodes
Show child nodes of the current working path.
```
zk> ls
```

Show child nodes for paths `foo` and `../bar`
```
zk> ls foo ../bar
```

Recursively show child nodes of entire ZooKeeper node space.
```
zk> ls -r /
```

Show child nodes in long format, which appends node names with `/` if it contains children or `*` if ephemeral, and in all
cases, the node version.
```
zk> ls -l
```

### Examining nodes
Display hex/ASCII output of data associated with node at current working path.
```
zk> get
```

Display data of node `/foo/bar` as string encoded as `ISO-8859-1`.
```
zk> get -s -e iso-8859-1 /foo/bar
```

Show internal ZooKeeper state of nodes `this` and `that`.
```
zk> stat this that
```

Show ACL for node `/zookeeper`.
```
zk> getacl /zookeeper
```

### Creating nodes
Create node `foo` with no attached data.
```
zk> mk foo
```

Create node `/foo/bar/baz` and all intermediate nodes, if necessary.
```
zk> mk -r /foo/bar/baz
```

Create ephemeral node `lock_` with monotonically-increasing sequence appended to name.
```
zk> mk -E -S lock_
```

Create node `bar` with data `hello world` encoded as `UTF-16`.
```
zk> mk -e utf-16 "hello world"
```

### Modifying nodes
Set data of node `/foo/bar` as `goodbye world` whose current version in ZooKeeper is `19`.
```
zk> set -v 19 /foo/bar "goodbye world"
```

Set data of node `lock/master` with data from file `/tmp/master.bin` and version `31`.
```
zk> set -v 31 lock/master @/tmp/master.bin
```

Forcefully clear the data of node `this/that` without specifying the version.
```
zk> set -f this/that
```

Replace ACL of node `foo/bar` with `world:*` (all permissions) and version `17`.
```
zk> setacl -v 17 foo/bar world:*
```

Add ACL `world:rw` (read/write) to node `foo/bar`, ignoring version.
```
zk> setacl -a -f foo/bar world:rw
```

Remove ACL `world:rwcd` (read/write/create/delete) to node `foo/bar` with version `23`.
```
zk> setacl -r -v 23 foo/bar world:rwcd
```

### Deleting nodes
Delete node `/lock/master` whose current version in ZooKeeper is `7`.
```
zk> rm -v 7 /lock/master
```

Recursively delete node `instances` and its children, forcefully doing so without specifying the version.
```
zk> rm -r -f instances
```

### Other useful commands
Show configuration of `zk` connection to a ZooKeeper cluster, including the session state.
```
zk> config
```

Quit the program.
```
zk> quit
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
