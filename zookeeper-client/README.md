# Scala API for ZooKeeper

A functional API layered over the ZooKeeper client.

## Documentation

API documentation can be found [here](https://davidedwards.io/zookeeper/api/1.7/com/loopfor/zookeeper/index.html).

## Dependency Information

This library is published in the Maven Central Repository.

* [Scala 2.13](https://repo1.maven.org/maven2/com/loopfor/zookeeper/zookeeper-client_2.13/1.7/)
* [Scala 3](https://repo1.maven.org/maven2/com/loopfor/zookeeper/zookeeper-client_3/1.7/)

## Release

The following steps are required for publishing a new release either locally or to the Maven Central Repository. It is important to note that these instructions should be carried out for both supported versions of Scala. Switching between Scala versions in `sbt` is accomplished by using the `++` command.

```shell
sbt> ++ 2.13
...
sbt> ++ 3.2
...
```

### Building

Ensure that the version number in [build.sbt](build.sbt) is updated. Snapshots should not be published to the Maven Central Repository even though this is perfectly fine when publishing locally.

Compile and test, ensuring that all tests are successful. All compiler warnings should be resolved.

```shell
sbt> compile
...
sbt> test
...
```

Generate API documentation. These files will be used later when updating the [gh-pages](https://github.com/davidledwards/zookeeper/tree/gh-pages) branch. Since the API documentation is identical for both Scala versions, only the `2.13` version needs to be generated.

```shell
sbt> doc
```

### Publishing

Publishing artifacts to the Maven Central Repository can only be done by the owner of this project, as this action requires credentials. Credentials should be placed in `$HOME/.sbt/1.0/sonatype.sbt` as follows.

```scala
credentials += Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", "<user>", "<password>")
```

Sonatype requires that artifacts be signed before publishing. The [sbt-pgp](https://github.com/sbt/sbt-pgp) site provides instructions for generating a GPG key pair.

Consider publishing the signed artifacts locally before releasing to Sonatype.

```shell
sbt> publishLocalSigned  # sign and publish to local repo
sbt> publishSigned       # sign and publish to Sonatype
```

### Sonatype

Login to <https://oss.sonatype.org>. Click on `Staging Repositories` in the left panel, select the staged repository, and click `Close`. After a few minutes, refresh the page and click `Release` assuming all checks passed. The staged repository can always be dropped by clicking `Drop`.

Once released, it could take several hours before the artifacts appear in the [Maven Central Repository](https://central.sonatype.com).

### Finalizing

Once the signed artifacts have been uploaded to and released on Sonatype, merge `develop` into `master`, ideally using a fast-forward merge.

```shell
$ git checkout master
$ git merge --ff-only develop
```

Switch to `master` and create a new tag using the convention `release-<version>`.

```shell
$ git tag -a release-<version> -m "release version <version>"
```

Finally, push all changes to GitHub.

```shell
$ git push --all origin
$ git push --tags origin
```

### Documentation

Switch to the `gh-pages` branch where the API documentation is kept.

```shell
$ git switch gh-pages
```

Create a new folder under `api/` whose name matches the new version number. Then, copy the contents of the API documentation generated earlier under this new folder.

```shell
$ mkdir api/<version>
$ cp -r target/scala-2.13/api/* api/<version>
```

Add a link to the new version of documentation in `index.html`. It should be intuitively obvious how to do this. When finished, commit and push changes.

```shell
$ git push origin gh-pages
```



## License

Copyright 2020 David Edwards

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<https://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
