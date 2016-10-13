name := "free-form-api"

scalaVersion := "2.11.8"
organization := "com.goejl.v1"

val dockerRegistry = "hub.docker.com"

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8")


resolvers ++= Seq("spray repo" at "http://repo.spray.io/")

libraryDependencies ++= Seq(
  "io.spray" %% "spray-can" % "1.3.3",
  "io.spray" %% "spray-routing" % "1.3.3",
  "com.typesafe.akka" %% "akka-actor" % "2.4.8",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.4")

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*)
  .settings(packAutoSettings: _*)
  .enablePlugins(DockerPlugin)
  .enablePlugins(GitVersioning)
  .enablePlugins(BuildInfoPlugin)


// Make docker depend on the package task, which generates a jar file of the application code
buildInfoPackage := s"${organization.value}"
buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, git.gitHeadCommit)

git.useGitDescribe := true

docker <<= (docker dependsOn (packAndSplitJars))
// Define a Dockerfile
dockerfile in docker := {
  val jarFile = artifactPath.in(Compile, packageBin).value
  val classpath = (managedClasspath in Compile).value
  val mainclass = mainClass.in(Compile, packageBin).value.get
  val libs = "/app/libs"
  val jarTarget = "/app/" + jarFile.name

  new Dockerfile {
    // Use a base image that contain Java
    from("java:8u91-jre")
    // Expose port 8080
    expose(8080)

    // Copy all dependencies to 'libs' in the staging directory
    classpath.files.foreach { depFile =>
      val target = file(libs) / depFile.name
      stageFile(depFile, target)
    }
    // Add the libs dir from the
    addRaw(libs, libs)

    // Add the generated jar file
    add(jarFile, jarTarget)
    // The classpath is the 'libs' dir and the produced jar file
    val classpathString = s"$libs/*:$jarTarget"
    // Set the entry point to start the application using the main class
    cmd("java", "-cp", classpathString, mainclass)

    label(
      "version"  -> version.value,
      "git.hash" -> git.gitHeadCommit.value.get
    )
  }
}

imageNames in docker := Seq(
  ImageName(
    registry = Some(dockerRegistry),
    namespace = Some("kaaquist"),
    repository = name.value,
    tag = Some(version.value)
  )
)

lazy val packAndSplitJars = taskKey[Unit]("Runs pack and splits out the application jars from the external dependency jars")

packAndSplitJars <<= packAndSplitJars dependsOn pack

packAndSplitJars := {
  val scalaMajorVersion = scalaVersion.value.split('.').take(2).mkString(".")
  val mainJar = s"${name.value}_$scalaMajorVersion-${version.value}.jar"
  val libDir = pack.value / "lib"
  val appLibDir = pack.value / "app-lib"
  appLibDir.mkdirs()
  IO.move(libDir / mainJar, appLibDir / mainJar)
}
