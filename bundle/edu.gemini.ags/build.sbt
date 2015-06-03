import OcsKeys._

// note: inter-project dependencies are declared at the top, in projects.sbt

name := "edu.gemini.ags"

// version set in ThisBuild

unmanagedJars in Compile ++= Seq(
  new File(baseDirectory.value, "../../lib/bundle/breeze_2.10-0.2.2.jar"),
  new File(baseDirectory.value, "../../lib/bundle/nom-tam-fits_2.10-0.99.3.jar"),
  new File(baseDirectory.value, "../../lib/bundle/org-apache-commons-httpclient_2.10-2.0.0.jar"),
  new File(baseDirectory.value, "../../lib/bundle/org-apache-commons-logging_2.10-1.1.0.jar")
)

osgiSettings

ocsBundleSettings

OsgiKeys.bundleActivator := None

OsgiKeys.bundleSymbolicName := name.value

OsgiKeys.dynamicImportPackage := Seq("")

OsgiKeys.exportPackage := Seq(
  "edu.gemini.ags.gems.*",
  "edu.gemini.ags.api.*",
  "edu.gemini.ags.conf.*")

OsgiKeys.privatePackage := Seq("edu.gemini.ags.impl.*")

parallelExecution in Test := false

