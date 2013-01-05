# Scala API/CLI for ZooKeeper
A collection of Scala artifacts that make working with ZooKeeper enjoyable.

## Projects
* `zookeeper-client` - a functional API layered over the ZooKeeper client
* `zookeeper-cli` - a much better command line interface (CLI) that replaces `zkCli.sh`

## Build Instructions
In order to build the corresponding artifacts, you must install [Java 1.6](http://www.java.com/en/download/index.jsp) or
higher and [Maven 3.0](http://maven.apache.org/download.cgi) or higher. There is no need to install Scala, as the compiler and
runtime are automatically downloaded by Maven.

In the root directory `zookeeper`, the following will command will build each of the subprojects and install them in your
local Maven repository:
```
mvn install
```

You may also build specific subprojects by simply navigating to the appropriate subdirectory and running `mvn install`.

## Command Line Interface (CLI)
Included in this project is a new command line interface designed to replace `zkCli.sh`, which comes with the
ZooKeeper distribution. This program is much cleaner and easier to use.

### Installing the CLI
The build will install an assembly of the CLI in your local Maven repository, complete with all requisite dependencies. The
only exception is Java 1.6, which must be separately installed on the target machine. The Scala runtime is included in the
assembly, so there is no need for explicit installation.

The location of the assembly is `M2_REPO/com/loopfor/zookeeper/zookeeper-cli/1.0-SNAPSHOT`. There are two identical versions
of the assembly:
* `zookeeper-cli-1.0-SNAPSHOT-bin.tar.gz`
* `zookeeper-cli-1.0-SNAPSHOT-bin.zip`

Unzipping either of these assemblies will produce the following output:
```
zookeeper-cli-1.0-SNAPSHOT/
+ bin/
  + zk
  + ...
+ lib/
  + ...
```

For convenience, you might place `zookeeper-cli-1.0-SNAPSHOT/bin/zk` in your PATH or create an alias.

### Helpful Tips
The `zk` program uses [JLine](https://github.com/jline/jline2) for console input similar to what you might expect in
modern shells. For example, `^p` and `^n` can be used to scroll back and forth through the command history.

It also supports TAB auto-completion as follows:
* Pressing TAB on the first argument attempts to auto-complete as though the user were entering a command. Therefore, pressing
TAB on an empty line produces a list of all available commands.
* Pressing TAB on the second, and all subsequent, arguments attempts to auto-complete as though the user were entering a
ZooKeeper node path. This feature was designed to very closely mimic file system auto-completion in the shell. It is
sensitive to the current path context, so you only see relevant subpaths.

### Node Paths
Unlike `zkCli.sh`, the `zk` program supports relative node paths and the ability to essentially _cd_ to a path, thus setting
a _current working path_. In all commands requiring paths, both absolute and relative forms may be given. An absolute path
starts with `/`, so the _current working path_ is ignored. Otherwise, the relative path is resolved in the context of the
_current working path_. Both `.` and `..` can be used in path expressions.

### Examples
```
zk
```
Shows usage information.
