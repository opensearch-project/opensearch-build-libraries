- [Developer Guide](#developer-guide)
  - [Forking and Cloning](#forking-and-cloning)
  - [Jenkins Pipelines and Shared Libraries](#jenkins-pipelines-and-shared-libraries)
    - [Install Prerequisites](#install-prerequisites-1)
      - [Java](#java)
    - [Run Tests](#run-tests-1)
      - [Regression Tests](#regression-tests)

# Developer Guide

## Forking and Cloning

Fork this repository on GitHub, and clone locally with `git clone`.

## Jenkins Pipelines and Shared Libraries

This project contains [Jenkins pipelines](jenkins) and [Jenkins shared libraries](src/jenkins) that execute the tools that build OpenSearch and OpenSearch Dashboards.

### Install Prerequisites

#### Java

Use Java 11 for Jenkins jobs CI. This means you must have a JDK 11 installed with the environment variable `JAVA_HOME` referencing the path to Java home for your JDK installation, e.g. `JAVA_HOME=/usr/lib/jvm/jdk-11`. Download Java 11 from [here](https://adoptium.net/releases.html?variant=openjdk11).

### Run Tests

This project uses [JenkinsPipelineUnit](https://github.com/jenkinsci/JenkinsPipelineUnit) to unit test Jenkins pipelines and shared libraries. See [tests/jenkins](tests/jenkins).

```
$ ./gradlew test

> Task :test
BUILD SUCCESSFUL in 7s
3 actionable tasks: 1 executed, 2 up-to-date
```

#### Regression Tests

Jenkins workflow regression tests typically output a .txt file into [tests/jenkins/jobs](tests/jenkins/jobs).
For example, [TestHello.groovy](tests/jenkins/TestHello.groovy) executes [Hello_Jenkinsfile](tests/jenkins/jobs/Hello_Jenkinsfile)
and outputs [Hello_Jenkinsfile.txt](tests/jenkins/jobs/Hello_Jenkinsfile.txt). If the job execution changes, the regression test will fail.

- To update the recorded .txt file run `./gradlew test -info -Dpipeline.stack.write=true` or update its value in [gradle.properties](gradle.properties).

- To run a specific test case, run `./gradlew test -info --tests=TestCaseClassName`

#### Tests for jenkins libraries

##### Lib Tester
Each jenkins library should have a lib tester associated with it. Eg: [SignArtifactsLibTester](tests/jenkins/lib-testers/SignArtifactsLibTester.groovy)
- Library tester should extend [LibFunctionTester.groovy](tests/jenkins/LibFunctionTester.groovy)
- implement `void configure(helper, bindings)` method which sets up all the variables used in the library
  - Note: This will not include the variables set using function arguments
- implement `void libFunctionName()`. This function will contain the name of function.
- implement `void parameterInvariantsAssertions()`. This function will contain assertions verifying the type and 
accepted values for the function parameters
- implement `void expectedParametersMatcher()`. This function will match args called in the job to expected values from 
the test

##### Library Test Case
Each jenkins library should have a test case associated with it. Eg: [TestSignArtifacts](tests/jenkins/TestSignArtifacts.groovy) <br>
- Jenkins' library test should extend [BuildPipelineTest.groovy](tests/jenkins/BuildPipelineTest.groovy)
- Create a dummy job such as [Hello_Jenkinsfile](tests/jenkins/jobs/Hello_Jenkinsfile) to call and test the function
  and output [Hello_Jenkinsfile.txt](tests/jenkins/jobs/Hello_Jenkinsfile.txt)
