package jsky.app.ot.gemini.igrins2

import edu.gemini.pot.sp.{ISPObsComponent, SPComponentType}
import edu.gemini.shared.gui.bean.TextFieldPropertyCtrl
import edu.gemini.spModel.core.{MagnitudeBand, Site}
import edu.gemini.spModel.gemini.igrins2.{Igrins2, Igrins2ScienceAreaGeometry}
import edu.gemini.spModel.telescope.IssPort
import jsky.app.ot.OTOptions
import jsky.app.ot.gemini.editor.ComponentEditor
import jsky.app.ot.gemini.parallacticangle.PositionAnglePanel
import squants.time.TimeConversions.TimeConversions

import java.beans.{PropertyChangeEvent, PropertyChangeListener}
import javax.swing.JPanel
import javax.swing.event.{DocumentEvent, DocumentListener}
import scala.swing.GridBagPanel.{Anchor, Fill}
import scala.swing.event.ButtonClicked
import scala.swing.{Alignment, ButtonGroup, Component, FlowPanel, GridBagPanel, Insets, Label, RadioButton, Separator, Swing}

class Igrins2Editor extends ComponentEditor[ISPObsComponent, Igrins2]{

  private object ui extends GridBagPanel {
    val updateParallacticAnglePCL: PropertyChangeListener = new PropertyChangeListener() {
      override def propertyChange(evt: PropertyChangeEvent): Unit = {
        posAnglePanel.updateParallacticControls()
      }
    }

    private var row = 0
    border = ComponentEditor.PANEL_BORDER

    layout(new Label("Science FOV: ")) = new Constraints() {
      anchor = Anchor.NorthWest
      gridx = 0
      gridy = row
      weightx = 1.0
      insets = new Insets(3, Igrins2Editor.LabelPadding, 0, 0)
    }

    layout(new Label("Wavelength Coverage: ")) = new Constraints() {
      anchor = Anchor.NorthWest
      gridx = 1
      gridy = row
      weightx = 1.0
      insets = new Insets(3, Igrins2Editor.LabelPadding, 0, 0)
    }
    row += 1

    /**
     * Science FOV (Read-only)
     */
    layout(new Label(f"${Igrins2ScienceAreaGeometry.ScienceFovHeight.toArcsecs}%.1f x ${Igrins2ScienceAreaGeometry.ScienceFovWidth.toArcsecs}%.1f arcsec" )) = new Constraints() {
      anchor = Anchor.NorthWest
      gridx = 0
      gridy = row
      weightx = 1.0
      insets = new Insets(3, Igrins2Editor.ControlPadding, 0, 0)
    }

    /**
     * Wavelength coverage (Read-only)
     */
    layout(new Label(s"${Igrins2.WavelengthCoverageLowerBound.toMicrons} - ${Igrins2.WavelengthCoverageUpperBound.toMicrons} μm" )) = new Constraints() {
      anchor = Anchor.NorthWest
      gridx = 1
      gridy = row
      weightx = 1.0
      insets = new Insets(3, Igrins2Editor.ControlPadding, 0, 0)
    }
    row += 1
    layout(new Separator()) = new Constraints() {
      anchor = Anchor.NorthWest
      fill = Fill.Horizontal
      gridx = 0
      gridy = row
      gridwidth = 3
      insets = new Insets(10, 0, 0, 0)
    }

    row += 1
    layout(new Label(s"Exposure Time (min: ${Igrins2.MinExposureTime.toSeconds}s):")) = new Constraints() {
      anchor = Anchor.NorthWest
      gridx = 0
      gridy = row
      weightx = 1.0
      insets = new Insets(3, Igrins2Editor.LabelPadding, 0, 0)
    }
    layout(new Label("Fowler Samples:")) = new Constraints() {
      anchor = Anchor.NorthWest
      gridx = 1
      gridy = row
      weightx = 1.0
      insets = new Insets(3, Igrins2Editor.LabelPadding, 0, 0)
    }
    row += 1

    /**
     * Exposure time
     */
    val expTimeCtrl: TextFieldPropertyCtrl[Igrins2, java.lang.Double] = TextFieldPropertyCtrl.createDoubleInstance(Igrins2.EXPOSURE_TIME_PROP, 1)
    expTimeCtrl.setColumns(10)
    expTimeCtrl.getTextField.getDocument.addDocumentListener(new DocumentListener {
      override def insertUpdate(e: DocumentEvent): Unit =
        updateFowlerSamples()

      override def removeUpdate(e: DocumentEvent): Unit =
        updateFowlerSamples()

      override def changedUpdate(e: DocumentEvent): Unit =
        updateFowlerSamples()
    })

    val expTimeUnits = new Label("sec")
    expTimeUnits.horizontalAlignment = Alignment.Left

    private val expTimePanel = new FlowPanel(Component.wrap(expTimeCtrl.getComponent), expTimeUnits)
    layout(expTimePanel) = new Constraints() {
      anchor = Anchor.NorthWest
      gridx = 0
      gridy = row
      insets = new Insets(3, Igrins2Editor.ControlPadding, 0, 0)
    }

    /**
     * Fowler Samples (Read-only)
     */
    val fowlerSamples = new Label("-")
    layout(fowlerSamples) = new Constraints() {
      anchor = Anchor.NorthWest
      gridx = 1
      gridy = row
      insets = new Insets(3, Igrins2Editor.ControlPadding, 0, 0)
    }
    row += 1
    layout(new Separator()) = new Constraints() {
      anchor = Anchor.NorthWest
      fill = Fill.Horizontal
      gridx = 0
      gridy = row
      gridwidth = 3
      insets = new Insets(10, 0, 0, 0)
    }
    row += 1

    /**
     * ISS Port
     */
    val upLookingButton = new RadioButton("Up-looking")
    val sideLookingButton = new RadioButton("Side-looking")

    new ButtonGroup(upLookingButton, sideLookingButton)

    listenTo(upLookingButton, sideLookingButton)
    reactions += {
      case ButtonClicked(`upLookingButton`) =>
        changeIssPort(IssPort.UP_LOOKING)
      case ButtonClicked(`sideLookingButton`) =>
        changeIssPort(IssPort.SIDE_LOOKING)
    }

    private val portPanel = new FlowPanel(upLookingButton, sideLookingButton)
    layout(new Label("ISS Port:")) = new Constraints() {
      anchor = Anchor.NorthWest
      gridx = 0
      gridy = row
      insets = new Insets(3, Igrins2Editor.LabelPadding, 0, 0)
    }
    val readNoiseLabel: Label = new Label("Read Noise:")
    readNoiseLabel.horizontalAlignment = Alignment.Left
    layout(readNoiseLabel) = new Constraints() {
      anchor = Anchor.NorthWest
      gridx = 1
      gridy = row
      insets = new Insets(3, Igrins2Editor.LabelPadding, 0, 0)
    }

    row += 1
    layout(portPanel) = new Constraints() {
      anchor = Anchor.NorthWest
      gridx = 0
      gridy = row
      insets = new Insets(3, Igrins2Editor.ControlPadding, 0, 0)
    }

    val readNoiseH: Label = new Label("-")
    readNoiseH.horizontalAlignment = Alignment.Left
    layout(readNoiseH) = new Constraints() {
      anchor = Anchor.NorthWest
      gridx = 1
      gridy = row
      insets = new Insets(3, Igrins2Editor.ControlPadding, 0, 0)
    }
    row += 1
    val readNoiseK: Label = new Label("-")
    readNoiseK.horizontalAlignment = Alignment.Left
    layout(readNoiseK) = new Constraints() {
      anchor = Anchor.NorthWest
      gridx = 1
      gridy = row
      insets = new Insets(3, Igrins2Editor.ControlPadding, 0, 0)
    }
    row += 1
    layout(new Separator()) = new Constraints() {
      anchor = Anchor.NorthWest
      fill = Fill.Horizontal
      gridx = 0
      gridy = row
      gridwidth = 3
      insets = new Insets(10, 0, 0, 0)
    }
    row += 1

    /**
     * Position angle components.
     **/
    val posAngleLabel: Label = new Label("Position Angle:")
    posAngleLabel.horizontalAlignment = Alignment.Left
    layout(posAngleLabel) = new Constraints() {
      anchor = Anchor.NorthWest
      gridx = 0
      gridy = row
      insets = new Insets(3, Igrins2Editor.LabelPadding, 0, 0)
    }

    row += 1
    val posAnglePanel: PositionAnglePanel[Igrins2, Igrins2Editor] = PositionAnglePanel.apply[Igrins2, Igrins2Editor](SPComponentType.INSTRUMENT_IGRINS2)
    layout(posAnglePanel) = new Constraints() {
      anchor = Anchor.NorthWest
      gridx = 0
      gridy = row
      insets = new Insets(3, Igrins2Editor.ControlPadding, 0, 0)
    }

    row += 1
    // empty but it pushes the rest of the controls to the top
    layout(new Label()) = new Constraints() {
      anchor = Anchor.NorthWest
      gridx = 0
      gridy = row
      weighty = 1
    }
  }

  private def updateFowlerSamples(): Unit = {
    val expTime = ui.expTimeCtrl.getBean.getExposureTime.seconds
    val fowlerSamples = Igrins2.fowlerSamples(expTime)

    def readNoiseAt(band: MagnitudeBand) =
      Igrins2.readNoise(expTime)
        .find(_._1 == band)
        .map(a => f"${band.name}: ${a._2}%.1f e-")
        .getOrElse("")
    ui.readNoiseH.text = readNoiseAt(MagnitudeBand.H)
    ui.readNoiseK.text = readNoiseAt(MagnitudeBand.K)
    ui.fowlerSamples.text = fowlerSamples.toString
  }

  def changeIssPort(port: IssPort): Unit = Swing.onEDT {
    getDataObject.setIssPort(port)
  }

  // Display the current ISS port settings
  def updateIssPort(): Unit = {
    val port = getDataObject.getIssPort
    if (port == IssPort.SIDE_LOOKING) ui.sideLookingButton.selected = true
    else if (port == IssPort.UP_LOOKING) ui.upLookingButton.selected = true
  }

  /**
   * Return the window containing the editor.
   */
  override def getWindow: JPanel = ui.peer

  override def handlePostDataObjectUpdate(inst: Igrins2): Unit = Swing.onEDT {
    ui.posAnglePanel.init(this, Site.GN)
    val editable = OTOptions.areRootAndCurrentObsIfAnyEditable(getProgram, getContextObservation)
    ui.posAnglePanel.updateEnabledState(editable)
    // REL-4400 Only staff can change port
    val isStaff = OTOptions.isStaff(getProgram)
    ui.sideLookingButton.enabled = isStaff
    ui.upLookingButton.enabled = isStaff

    inst.addPropertyChangeListener(Igrins2.POS_ANGLE_CONSTRAINT_PROP.getName, ui.updateParallacticAnglePCL)
    ui.expTimeCtrl.setBean(inst)
    updateFowlerSamples()
    updateIssPort()
  }

  override def handlePreDataObjectUpdate (inst: Igrins2): Unit = {
    Option(inst).foreach {inst =>
      inst.removePropertyChangeListener(Igrins2.POS_ANGLE_CONSTRAINT_PROP.getName, ui.updateParallacticAnglePCL)
    }
  }
}

object Igrins2Editor {
  val LabelPadding = 5
  val ControlPadding = 15
}
