# ZooKeeper CLI

A new command line program designed to replace `zkCli.sh`, which comes with the ZooKeeper distribution. This program is much cleaner and easier to use.

## Installing

The CLI program can be downloaded from the Sonatype Repository in both [tar](https://search.maven.org/artifact/com.loopfor.zookeeper/zookeeper-cli/1.5/tar.gz) and [zip](https://search.maven.org/artifact/com.loopfor.zookeeper/zookeeper-cli/1.5/zip) formats.

Unpacking the assembly will produce the following output:

```shell
zookeeper-cli-1.5/
+ bin/
  + zk
  + zk.bat
  + ...
+ lib/
  + ...
```

For convenience, you might place `zookeeper-cli-1.5/bin/zk` in your `PATH` or create an alias.

## Helpful Tips

The `zk` program uses [JLine](https://github.com/jline/jline2) for console input similar to what you might expect in modern shells. For example, `^p` and `^n` can be used to scroll back and forth through the command history.

It also supports TAB auto-completion as follows:

* Pressing TAB on the first argument attempts to auto-complete as though the user were entering a command. Therefore, pressing TAB on an empty line produces a list of all available commands.
* Pressing TAB on the second, and all subsequent, arguments attempts to auto-complete as though the user were entering a ZooKeeper node path. This feature was designed to very closely mimic file system auto-completion in the shell. It is sensitive to the current path context, so you only see relevant subpaths.

## Node Paths

Unlike `zkCli.sh`, the `zk` program supports relative node paths and the ability to essentially _cd_ to a path, thus setting a _current working path_. In all commands requiring paths, both absolute and relative forms may be given. An absolute path starts with `/`, so the _current working path_ is ignored. Otherwise, the relative path is resolved in the context of the _current working path_.

Both `.` and `..` can be used in path expressions.

## Invoking `zk`

### Getting help from the command line

Show `zk` usage information.

```shell
zk
zk -?
zk --help
```

Show list of commands.

```shell
zk --help help
zk -? help
```

Display usage information for the `ls` command.

```shell
zk --help ls
zk -? ls
```

Print version information.

```shell
zk --version
```

### Connecting to a cluster

Connect to a ZooKeeper cluster by specifying at least one of its servers.

```shell
zk server1.com:2181
```

The port number may be omitted if ZooKeeper servers are listening on port `2181`.

```shell
zk server1.com server2.com
```

Specify a root path, akin to `chroot`.

```shell
zk -p /foo server1.com
```

Allow connection to a ZooKeeper server even if a quorum has not been established.

```shell
zk -r server1.com
```

### Scripting commands

Execute a command without entering the `zk` shell.

```shell
zk -c "get foo/bar" server1.com
```

Execute commands from an external file whose character encoding is presumed to be `UTF-8`.

```shell
zk -f my.commands server1.com
```

Specify the character encoding that applies to an external command file.

```shell
zk -f my.commands -e iso-8859-1 server1.com
```

Execute multiple commands without entering the `zk` shell.

```shell
zk -f /dev/tty server1.com
ls -l /hbase
ls -r /hadoop
^D
```

### Working with quoted strings

In some cases, it becomes necessary to provide arguments, such as commands or values, as quoted strings from either the OS shell or the `zk` shell. The following examples illustrate the correct format, particularly for escaping embedded quotes.

From command shells, such as `zsh` or `bash`.

```shell
zk -c "set -f /foo \"bar: 7\"" server1.com
```

or

```shell
zk -c 'set -f /foo "bar: 7"' server1.com
```

The `-c` argument is parsed by the command shell and presented to `zk` as a single argument in the following form.

```shell
set -f /foo "bar: 7"
```

The `zk` shell will then split this string into distinct tokens similar to the manner done by `zsh` or `bash`, leading to the following tokens.

```shell
set
-f
/foo
bar: 7
```

The following depicts a more complex scenario in which quoted strings tokenized by `zk` contain embedded quotes. Consider a slightly more complicated form of the previous example.

```shell
zk -c "set -f /foo \"bar: \\\"7\\\"\"" server1.com
```

or

```shell
zk -c 'set -f /foo "bar: \"7\""' server1.com
```

The `-c` argument sent to `zk` by the command shell appears as follows.

```shell
set -f /foo "bar: \"7\""
```

Which is then tokenized by `zk`.

```shell
set
-f
/foo
bar: "7"
```

### Logging

Send ZooKeeper log messages to a file other than the default of `$HOME/zk.log`.

```shell
zk --log /tmp/zk.log server1.com
```

Captures all ZooKeeper log messages rather than the default severity level of `warn`.

```shell
zk --level all server1.com
```

Disable ZooKeeper log messages.

```shell
zk --nolog server1.com
```

## Using `zk`

### Getting help

Get list of all commands.

```shell
zk> help
```

Get help for a specific command.

```shell
zk> help ls
```

### Working with node paths

Change the current working path.

```shell
zk> cd foo/bar
```

Change the current working path to the parent.

```shell
zk> cd ..
```

Return to previous working path.

```shell
zk> cd -
```

Change the current working path to `/`.

```shell
zk> cd
```

Display the current working path.

```shell
zk> pwd
```

### Listing nodes

Show child nodes of the current working path.

```shell
zk> ls
```

Show child nodes for paths `foo` and `../bar`

```shell
zk> ls foo ../bar
```

Recursively show child nodes of entire ZooKeeper node space.

```shell
zk> ls -r /
```

Show child nodes in long format, which appends node names with `/` if it contains children or `*` if ephemeral, and in all cases, the node version.

```shell
zk> ls -l
```

### Examining nodes

Display hex/ASCII output of data associated with node at current working path.

```shell
zk> get
```

Display data of node `/foo/bar` as string encoded as `ISO-8859-1`.

```shell
zk> get -s -e iso-8859-1 /foo/bar
```

Show internal ZooKeeper state of nodes `this` and `that`.

```shell
zk> stat this that
```

Show state of node `/foo` in compact format.

```shell
zk> stat -c /foo
```

Show ACL for node `/zookeeper`.

```shell
zk> getacl /zookeeper
```

### Creating nodes

Create node `foo` with no attached data.

```shell
zk> mk foo
```

Create node `/foo/bar/baz` and all intermediate nodes, if necessary.

```shell
zk> mk -r /foo/bar/baz
```

Create ephemeral node `lock_` with monotonically-increasing sequence appended to name.

```shell
zk> mk -E -S lock_
```

Create node `bar` with data `hello world` encoded as `UTF-16`.

```shell
zk> mk -e utf-16 "hello world"
```

Create node `foo` with time-to-live of `10` seconds.

```shell
zk> mk -T 10000 foo
```

### Modifying nodes

Set data of node `/foo/bar` as `goodbye world` whose current version in ZooKeeper is `19`.

```shell
zk> set -v 19 /foo/bar "goodbye world"
```

Set data of node `lock/master` with data from file `/tmp/master.bin` and version `31`.

```shell
zk> set -v 31 lock/master @/tmp/master.bin
```

Forcefully clear the data of node `this/that` without specifying the version.

```shell
zk> set -f this/that
```

Replace ACL of node `foo/bar` with `world:anyone=*` (all permissions) and version `17`.

```shell
zk> setacl -v 17 foo/bar world:anyone=*
```

Add ACL `world:anyone=rw` (read/write) to node `foo/bar`, ignoring version.

```shell
zk> setacl -a -f foo/bar world:anyone=rw
```

Remove ACL `world:anyone=rwcd` (read/write/create/delete) from node `foo/bar` with version `23`.

```shell
zk> setacl -r -v 23 foo/bar world:anyone=rwcd
```

### Deleting nodes

Delete node `/lock/master` whose current version in ZooKeeper is `7`.

```shell
zk> rm -v 7 /lock/master
```

Recursively delete node `instances` and its children, forcefully doing so without specifying the version.

```shell
zk> rm -r -f instances
```

### Finding nodes

The `find` command can be very useful, but also quite destructive when applying mutating operations. A general rule of thumb is to verify nodes that are expected to match the regular expression before applying commands that modify state. All patterns are strictly regular expressions as defined by Java 6 (<http://bit.ly/zk-regex>).

Find and print all nodes matching the regular expression `locks` relative to node `foo`. Note in the first example that the `print` operation is assumed if omitted.

```shell
zk> find locks foo
zk> find locks foo --exec print
```

Recursively find and print all nodes matching the regular expression `lock_.*` relative to node `foo`.

```shell
zk> find -r lock_.* foo
```

Recursively find all nodes matching `locks` relative to node `foo` and create a child node with the prefix `lock_` whose suffix is a monotonically-increasing sequence and whose associated data is `this is a lock`.

```shell
zk> find -r locks foo --exec mk -S lock_ "this is a lock"
```

Display data in string format for all nodes relative to `foo/locks` matching the regular expression `lock_\d+`.

```shell
zk> find lock_\d+ foo/locks --exec get -s
```

Set data for all nodes relative to `foo/locks` matching the regular expression `lock_\d+` to the value `this was a lock`.

```shell
zk> find lock_\d+ foo/locks --exec set "this was a lock"
```

Find all nodes relative to `foo/locks` matching the regular expression `lock_\d+` and recursively delete those nodes and their children.

```shell
zk> find lock_\d+ foo/locks --exec rm -r
```

Tell `find` to suppress display of matching nodes.

```shell
zk> find --quiet ...
zk> find -q ...
```

Tell `find` to stop executing the given command on error (default is to continue to the next node).

```shell
zk> find --halt ...
zk> find -h ...
```

### Other useful commands

Show configuration of `zk` connection to a ZooKeeper cluster, including the session state and location of the log file.

```shell
zk> config
```

Quit the program.

```shell
zk> quit
```

## License

Copyright 2020 David Edwards

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
