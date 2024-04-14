# Scala API and CLI for ZooKeeper

> [!NOTE]
>
> This repository has been split into two repositories to manage the API and CLI projects separately. Both repositories were cloned at commit [6ad2c34](https://github.com/davidledwards/zookeeper/commit/6ad2c34d799d4373aa077d89eecfde6e7e8e1612). No further changes will be made to this repository.
>
> * [zookeeper-client](https://github.com/davidledwards/zookeeper-client)
> * [zookeeper-cli](https://github.com/davidledwards/zookeeper-cli)

A collection of Scala artifacts that make working with ZooKeeper enjoyable.

## Projects

* [zookeeper-client](zookeeper-client/) - a functional API layered over the ZooKeeper client
* [zookeeper-cli](zookeeper-cli/) - a much nicer command line program that replaces `zkCli.sh`

See individual subprojects for additional information. Note that `zookeeper-cli` depends on `zookeeper-client`.

## License

Copyright 2015 David Edwards

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<https://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
