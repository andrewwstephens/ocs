package jsky.app.ot.gemini.editor.targetComponent.details

import javax.swing.JPanel
import edu.gemini.pot.sp.ISPNode
import edu.gemini.shared.util.immutable.{ Option => GOption }
import edu.gemini.spModel.obs.context.ObsContext
import edu.gemini.spModel.target.SPTarget
import edu.gemini.spModel.target.system.ITarget.Tag
import jsky.app.ot.gemini.editor.targetComponent.TelescopePosEditor

abstract class TargetDetailEditor(val getTag: Tag) extends JPanel with TelescopePosEditor {
  def edit(ctx: GOption[ObsContext], spTarget: SPTarget, node: ISPNode): Unit = {
    require(ctx      != null, "obsContext should never be null")
    require(spTarget != null, "spTarget should never be null")
    val tag = spTarget.getTarget.getTag
    require(tag == getTag, "target tag should always be " + getTag + ", received " + tag)
  }
}
