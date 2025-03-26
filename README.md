<a href="https://github.com/encalmo/scala-aws-lambda-multihandler">![GitHub](https://img.shields.io/badge/github-%23121011.svg?style=for-the-badge&logo=github&logoColor=white)</a> <a href="https://central.sonatype.com/artifact/org.encalmo/scala-aws-lambda-multihandler_3" target="_blank">![Maven Central Version](https://img.shields.io/maven-central/v/org.encalmo/scala-aws-lambda-multihandler_3?style=for-the-badge)</a> <a href="https://encalmo.github.io/scala-aws-lambda-multihandler/scaladoc/org/encalmo/lambda.html" target="_blank"><img alt="Scaladoc" src="https://img.shields.io/badge/docs-scaladoc-red?style=for-the-badge"></a>

# scala-aws-lambda-multihandler

Multiple handlers support for lambda written using scala-aws-lambda-runtime.

## Table of contents

- [Dependencies](#dependencies)
- [Usage](#usage)
- [Examples](#examples)
- [Project content](#project-content)

## Dependencies

   - [Scala](https://www.scala-lang.org) >= 3.3.5
   - org.encalmo [**scala-aws-lambda-runtime** 0.9.6](https://central.sonatype.com/artifact/org.encalmo/scala-aws-lambda-runtime_3) | [**scala-aws-lambda-utils** 0.9.2](https://central.sonatype.com/artifact/org.encalmo/scala-aws-lambda-utils_3)

## Usage

Use with SBT

    libraryDependencies += "org.encalmo" %% "scala-aws-lambda-multihandler" % "0.9.4"

or with SCALA-CLI

    //> using dep org.encalmo::scala-aws-lambda-multihandler:0.9.4

## Examples

TBD


## Project content

```
├── .github
│   └── workflows
│       ├── pages.yaml
│       ├── release.yaml
│       └── test.yaml
│
├── .gitignore
├── .scalafmt.conf
├── ApiGatewayRequestHandler.scala
├── GenericEvent.scala
├── GenericEventHandler.scala
├── LICENSE
├── MultipleHandlersSupport.scala
├── MultipleHandlersSupport.test.scala
├── project.scala
├── README.md
├── SqsEventHandler.scala
└── test.sh
```

