package edu.gemini.sp.vcs2

import edu.gemini.sp.vcs.TestingEnvironment._
import edu.gemini.spModel.rich.pot.sp._

// Some more or less throw-away code to run a timing test on an impossibly large
// program difference.

object ProgramDiffTiming {
  def main(args: Array[String]) {
    withPiTestEnv { env =>
      import env._

      (1 to 10000).foreach { _ => central.addObservation() }
      cloned.copyFrom(central)

      central.sp.children.foreach { child =>
        child.title = "foo"
      }

      val start = System.currentTimeMillis()
      ProgramDiff.compare(central.sp, cloned.sp.getVersions, removedKeys(cloned.sp))
      val end   = System.currentTimeMillis()

      println("Time: " + (end - start))
    }
  }
}
