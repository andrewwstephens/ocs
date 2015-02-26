package jsky.app.ot.testlauncher

import edu.gemini.ags.conf.ProbeLimitsTable
import edu.gemini.qv.plugin.{QvTool, ShowQvToolAction}
import jsky.app.ot.viewer.plugin.PluginRegistry

import scalaz._, Scalaz._
import edu.gemini.pot.spdb.DBLocalDatabase
import edu.gemini.util.trpc.auth.TrpcKeyChain
import edu.gemini.util.security.auth.keychain.Action._
import java.io.File
import edu.gemini.sp.vcs.reg.impl.VcsRegistrarImpl
import edu.gemini.pot.client.SPDB
import jsky.catalog.skycat.SkycatConfigFile
import jsky.app.ot.OT
import jsky.app.ot.viewer.ViewerService
import jsky.app.ot.vcs.VcsGui
import jsky.app.ot.visitlog.ShowVisitLogAction
import edu.gemini.spModel.core._
import edu.gemini.util.security.auth.ui.{PasswordDialog, AuthDialog}

object TestLauncher extends App {

  // Store everything in /tmp; clean it up by hand as needed.
  val dir   = new File("/tmp/ot-testlauncher") <| (_.mkdirs)
  val peers = List(new Peer("localhost", 8443, Site.GS))
  val keys  = TrpcKeyChain(new File(dir, "keys.ser"), peers).unsafeRunAndThrow
  val reg   = new VcsRegistrarImpl(new File(dir, "vcs-reg.xml"))
  val odb   = DBLocalDatabase.create(new File(dir, "spdb")) <| SPDB.init

  // Irritation, then open the OT
  AuthDialog.showDatabaseTab = true
  SkycatConfigFile.setConfigFile(classOf[SkycatConfigFile].getResource("/jsky/catalog/osgi/skycat.cfg"))
  ViewerService.instance = Some(new ViewerService(odb, reg))
  VcsGui.registrar = Some(reg)

  // Register Plugins
  PluginRegistry.add(new ShowVisitLogAction())

  // QV expects a defaultsFile variable to be initialized by the activator so
  // set it to something here.
  QvTool.defaultsFile = File.createTempFile("qvDefaults", "xml")
  PluginRegistry.add(new ShowQvToolAction())

  // Run!
  if (keys.isLocked.unsafeRunAndThrow) {
    PasswordDialog.unlock(keys, null)
    if (keys.isLocked.unsafeRunAndThrow) System.exit(0)
  }
  OT.open(keys, ProbeLimitsTable.loadOrThrow(), reg, new File(dir, "ot-storage"))

}
