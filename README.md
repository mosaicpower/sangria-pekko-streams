[Sangria](https://sangria-graphql.github.io/) [pekko-streams](https://pekko.apache.org/docs/pekko/current/stream/) integration.

This is a fork of [sangria-akka-streams](https://github.com/sangria-graphql/sangria-akka-streams) that replaces the Akka dependency with Pekko, and adds Scala 3 support.

SBT Configuration:

```scala
libraryDependencies += "com.mosaicpower" %% "sangria-pekko-streams" % "<latest version>"
```

## License

**sangria-pekko-streams** is licensed under [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
