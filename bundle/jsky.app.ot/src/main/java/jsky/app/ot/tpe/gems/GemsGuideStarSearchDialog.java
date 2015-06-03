package jsky.app.ot.tpe.gems;

import edu.gemini.ags.gems.GemsUtils4Java;
import edu.gemini.ags.gems.GemsGuideStarSearchOptions.*;
import edu.gemini.ags.gems.GemsGuideStars;
import edu.gemini.pot.ModelConverters;
import edu.gemini.spModel.core.Target;
import edu.gemini.spModel.target.env.TargetEnvironment;
import edu.gemini.spModel.target.obsComp.TargetObsComp;
import jsky.app.ot.tpe.GemsGuideStarWorker;
import jsky.app.ot.tpe.TpeImageWidget;
import jsky.app.ot.tpe.TpeManager;
import jsky.catalog.Catalog;
import jsky.catalog.gui.TablePlotter;
import jsky.catalog.skycat.SkycatConfigFile;
import jsky.util.Preferences;
import jsky.util.gui.SwingWorker;
import jsky.util.gui.DialogUtil;
import jsky.util.gui.StatusPanel;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;

/**
 * OT-111: GeMS Guide Star Search Dialog
 */
public class GemsGuideStarSearchDialog extends JFrame {

    // Represents the current state of the dialog
    enum State {
        PRE_QUERY("Review search options and click Query to begin", 0),
        QUERY("Querying guide star catalog ...", 0),
        PRE_ANALYZE("Review candidate guide stars and press Analyze to find asterisms.", 0),
        ANALYZE("Searching candidate guide stars for asterisms ...", 0),
        SELECTION("Select asterisms to add to the target list.", 1);

        // Message to display at top
        private String _message;

        // Index of tab to show in tabbed pane
        private int _tabIndex;

        State(String message, int tabIndex) {
            _message = message;
            _tabIndex = tabIndex;
        }

        public String getMessage() {
            return _message;
        }

        public int getTabIndex() {
            return _tabIndex;
        }
    }

    private AbstractAction _useDefaultsAction = new AbstractAction("Use Defaults") {
        {
            setEnabled(false);
        }

        public void actionPerformed(ActionEvent evt) {
            useDefaults();
            setEnabled(false);
        }
    };

    private AbstractAction _queryAction = new AbstractAction("Query") {
        public void actionPerformed(ActionEvent evt) {
            try {
                query();
            } catch (Exception e) {
                DialogUtil.error(GemsGuideStarSearchDialog.this, e);
            }
        }
    };

    private AbstractAction _cancelAction = new AbstractAction("Cancel") {
        public void actionPerformed(ActionEvent evt) {
            cancel();
        }
    };

    private AbstractAction _stopAction = new AbstractAction("Stop") {
        public void actionPerformed(ActionEvent evt) {
            stop();
        }
    };

    private AbstractAction _analyzeAction = new AbstractAction("Analyze") {
        public void actionPerformed(ActionEvent evt) {
            try {
                analyze();
            } catch (Exception e) {
                DialogUtil.error(GemsGuideStarSearchDialog.this, e);
            }
        }
    };

    private AbstractAction _addAction = new AbstractAction("Add") {
        public void actionPerformed(ActionEvent evt) {
            try {
                add();
            } catch (Exception e) {
                DialogUtil.error(GemsGuideStarSearchDialog.this, e);
            }
        }
    };

    protected static NumberFormat NF = NumberFormat.getInstance(Locale.US);

    static {
        NF.setMaximumFractionDigits(1);
    }

    private State _state;
    private GemsGuideStarSearchModel _model;
    private GemsGuideStarSearchController _controller;

    // Button for Query, Analyze, Add
    private JButton _actionButton;

    // Context dependent message (title)
    private JLabel _actionLabel;

    private JComboBox<CatalogChoice> _catalogComboBox;
    private JComboBox<NirBandChoice> _nirBandComboBox;
    private JCheckBox _reviewCandidatesCheckBox;
    private JComboBox<AnalyseChoice> _analyseComboBox;
    private JCheckBox _allowPosAngleChangesCheckBox;
    private JTabbedPane _tabbedPane;
    private JPanel _candidateGuideStarsPanel;

    private CandidateGuideStarsTable _candidateGuideStarsTable;
    private CandidateAsterismsTreeTable _candidateAsterismsTreeTable;
    private JLabel _paLabel;

    private GemsGuideStarWorker _worker;
    private StatusPanel _statusPanel;

    // Set to true in selection handling
    private boolean _ignoreSelection;

    private TpeImageWidget _tpe;
    private TablePlotter _plotter;

    private TargetEnvironment _savedTargetEnv;

    private static TargetEnvironment getEnvironment(TpeImageWidget tpe) {
        return tpe.getContext().targets().envOrDefault();
    }

    public GemsGuideStarSearchDialog() {
        super("GeMS Guide Star Search");
        _tpe = TpeManager.create().getImageWidget();
        _plotter = _tpe.getNavigator().getPlotter();
        // TPE REFACTOR -- i suppose we're assuming this isn't created from
        // scratch, in which case we'd have no environment yet
        _savedTargetEnv = getEnvironment(_tpe);

        getContentPane().add(makeMainPanel(), BorderLayout.CENTER);
        getContentPane().add(makeBottomPanel(), BorderLayout.SOUTH);

        _model = new GemsGuideStarSearchModel();
        _worker = new GemsGuideStarWorker(_statusPanel);
        _statusPanel.setText("");

        _controller = new GemsGuideStarSearchController(_model, _worker, this);
        _candidateAsterismsTreeTable.setController(_controller);

        setState(State.PRE_QUERY);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        Preferences.manageLocation(this);
        pack();
        setVisible(true);
    }

    private JPanel makeMainPanel() {
        JPanel panel = new JPanel(new GridBagLayout());

        final Insets labelInsets = new Insets(11, 11, 0, 0);
        final Insets valueInsets = new Insets(11, 3, 0, 0);
        _actionLabel = new JLabel();
        panel.add(_actionLabel, new GridBagConstraints() {{
            gridx = 0;
            gridy = 0;
            gridwidth = 4;
            anchor = WEST;
            insets = labelInsets;
        }});

        panel.add(new JLabel("Catalog"), new GridBagConstraints() {{
            gridx = 0;
            gridy = 1;
            insets = labelInsets;
            anchor = EAST;
        }});

        JPanel catalogPanel = new JPanel();
        _catalogComboBox = new JComboBox<>(CatalogChoice.values());
        catalogPanel.add(_catalogComboBox);
        panel.add(catalogPanel, new GridBagConstraints() {{
            gridx = 1;
            gridy = 1;
            insets = valueInsets;
            anchor = WEST;
        }});

        panel.add(new JLabel("NIR"), new GridBagConstraints() {{
            gridx = 2;
            gridy = 1;
            insets = labelInsets;
            anchor = EAST;
        }});

        _nirBandComboBox = new JComboBox<>(NirBandChoice.values());
        panel.add(_nirBandComboBox, new GridBagConstraints() {{
            gridx = 3;
            gridy = 1;
            anchor = WEST;
            insets = valueInsets;
        }});

        _reviewCandidatesCheckBox = new JCheckBox("Review candidates before asterism search", true);
        panel.add(_reviewCandidatesCheckBox, new GridBagConstraints() {{
            gridx = 1;
            gridy = 2;
            gridwidth = 3;
            anchor = WEST;
            insets = valueInsets;
        }});

        panel.add(new JLabel("Asterism"), new GridBagConstraints() {{
            gridx = 0;
            gridy = 4;
            insets = labelInsets;
            anchor = EAST;
        }});

        _analyseComboBox = new JComboBox<>(AnalyseChoice.values());
        panel.add(_analyseComboBox, new GridBagConstraints() {{
            gridx = 1;
            gridy = 4;
            insets = valueInsets;
            anchor = WEST;
        }});

        _allowPosAngleChangesCheckBox = new JCheckBox("Allow position angle adjustments", true);
        panel.add(_allowPosAngleChangesCheckBox, new GridBagConstraints() {{
            gridx = 1;
            gridy = 5;
            gridwidth = 3;
            anchor = WEST;
            insets = valueInsets;
        }});

        _tabbedPane = new JTabbedPane();
        _tabbedPane.add(_candidateGuideStarsPanel = makeCandidateGuideStarsPanel());
        _tabbedPane.add(makeCandidateAsterismsPanel());
        panel.add(_tabbedPane, new GridBagConstraints() {{
            gridx = 0;
            gridy = 6;
            gridwidth = 4;
            weightx = 1;
            weighty = 1;
            fill = BOTH;
            insets = new Insets(11, 11, 11, 11);
        }});

        useDefaults();
        addListeners();
        Preferences.manageSize(this, new Dimension(700, 500));
        return panel;
    }

    private JPanel makeBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(makeButtonPanel(), BorderLayout.NORTH);

        _statusPanel = new StatusPanel();
        panel.add(_statusPanel, BorderLayout.SOUTH);

        return panel;
    }

    // Add listeners for changes in the parameter components
    private void addListeners() {
        // In Pre-Analyze State:
        // If the user modifies the catalog search parameters, the tool transitions
        // back to the Pre- Query state. If the user presses “Analyze”, the tool moves to Analyzing.
        //
        // In Selection State:
        // If the user edits catalog search options, the tool throws away its results and moves back
        // to Pre-Query. On the other hand, if he changes Asterism parameters or changes candidate
        // guide stars, the tool moves back to Pre-Analyze.
        final List<JComponent> searchParams = new ArrayList<>();
        searchParams.add(_catalogComboBox);
        searchParams.add(_nirBandComboBox);
        searchParams.add(_reviewCandidatesCheckBox);

        // These 2 are not defined as search params in the pdf spec, but do influence the search parameters
        searchParams.add(_analyseComboBox);
        searchParams.add(_allowPosAngleChangesCheckBox);

        ActionListener a = e -> {
            _useDefaultsAction.setEnabled(!usingDefaults());
            if (_state == State.PRE_ANALYZE) {
                if (searchParams.contains(e.getSource())) {
                    setState(State.PRE_QUERY);
                }
            } else if (_state == State.SELECTION) {
                if (searchParams.contains(e.getSource())) {
                    setState(State.PRE_QUERY);
                } else {
                    setState(State.PRE_ANALYZE);
                }
            }
        };
        _catalogComboBox.addActionListener(a);
        _nirBandComboBox.addActionListener(a);
        _reviewCandidatesCheckBox.addActionListener(a);
        _analyseComboBox.addActionListener(a);
        _allowPosAngleChangesCheckBox.addActionListener(a);

        // If unchecked, the “Candidate Guide Stars” tab should be removed.
        _reviewCandidatesCheckBox.addActionListener(e -> {
            if (_reviewCandidatesCheckBox.isSelected()) {
                _tabbedPane.add(_candidateGuideStarsPanel, 0);
            } else {
                _tabbedPane.remove(_candidateGuideStarsPanel);
            }
        });

        // Selecting a group will cause it to be shown in the TPE
        _candidateAsterismsTreeTable.getTreeSelectionModel().addTreeSelectionListener(e -> {
            if (!_ignoreSelection) {
                _ignoreSelection = true;
                try {
                    _candidateAsterismsTreeTable.selectRelatedRows();
                } finally {
                    _ignoreSelection = false;
                }
            }
        });
    }

    // Sets the current dialog state
    void setState(State state) {
        _state = state;
        _actionLabel.setText(state.getMessage());
        if (_tabbedPane.getTabCount() > state.getTabIndex()) {
            _tabbedPane.setSelectedIndex(state.getTabIndex());
        }
        switch (state) {
            case PRE_QUERY:
                _actionButton.setAction(_queryAction);
                _model.setGemsCatalogSearchResults(null);
                _model.setGemsGuideStars(null);
                _candidateGuideStarsTable.clear();
                _candidateAsterismsTreeTable.clear();
                setEnabledStates(true, false, false);
                _useDefaultsAction.setEnabled(!usingDefaults());
                break;
            case QUERY:
                _stopAction.setEnabled(true);
                _actionButton.setAction(_stopAction);
                setEnabledStates(false, false, false);
                _useDefaultsAction.setEnabled(false);
                break;
            case PRE_ANALYZE:
                _actionButton.setAction(_analyzeAction);
                _model.setGemsGuideStars(null);
                _candidateAsterismsTreeTable.clear();
                setEnabledStates(true, true, false);
                _useDefaultsAction.setEnabled(!usingDefaults());
                break;
            case ANALYZE:
                _stopAction.setEnabled(true);
                _actionButton.setAction(_stopAction);
                setEnabledStates(false, false, false);
                _useDefaultsAction.setEnabled(false);
                break;
            case SELECTION:
                _actionButton.setAction(_addAction);
                setEnabledStates(true, true, true);
                _useDefaultsAction.setEnabled(!usingDefaults());
                break;
        }
    }

    private void setEnabledStates(boolean enabled, boolean guideStarTabState, boolean asterismTabState) {
        _catalogComboBox.setEnabled(enabled);
        _nirBandComboBox.setEnabled(enabled);
        _reviewCandidatesCheckBox.setEnabled(enabled);
        _analyseComboBox.setEnabled(enabled);
        _allowPosAngleChangesCheckBox.setEnabled(enabled);
        _tabbedPane.setEnabled(enabled);
        _candidateGuideStarsTable.setEnabled(enabled);
        _candidateAsterismsTreeTable.setEnabled(enabled);

        _tabbedPane.setEnabledAt(0, guideStarTabState);
        if (_tabbedPane.getTabCount() > 1) {
            _tabbedPane.setEnabledAt(1, asterismTabState);
        }
    }


    private JPanel makeCandidateGuideStarsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setName("Candidate Guide Stars");
        _candidateGuideStarsTable = new CandidateGuideStarsTable(_plotter);
        panel.add(_candidateGuideStarsTable, BorderLayout.CENTER);
        panel.add(new JLabel("Select to view in position editor. Uncheck to exclude from asterism search."),
                BorderLayout.SOUTH);
        return panel;
    }

    private JPanel makeCandidateAsterismsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setName("Candidate Asterisms");
        _candidateAsterismsTreeTable = new CandidateAsterismsTreeTable();
        panel.add(new JScrollPane(_candidateAsterismsTreeTable), BorderLayout.CENTER);

        // Display label at left, PA at right
        JPanel panel2 = new JPanel(new BorderLayout(0, 5));
        panel.add(panel2, BorderLayout.SOUTH);
        panel2.add(new JLabel("Check to include in target list, double-click to set as primary and view in position editor."),
                BorderLayout.WEST);
        _paLabel = new JLabel();
        panel2.add(_paLabel, BorderLayout.EAST);
        return panel;
    }

    private JPanel makeButtonPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel left = new JPanel();
        JPanel right = new JPanel();
        panel.add(left, BorderLayout.WEST);
        panel.add(right, BorderLayout.EAST);
        left.add(new JButton(_useDefaultsAction));
        right.add(new JButton(_cancelAction));
        _actionButton = new JButton(_queryAction);
        right.add(_actionButton);
        return panel;
    }

    private void useDefaults() {
        _catalogComboBox.setSelectedItem(CatalogChoice.DEFAULT);
        _nirBandComboBox.setSelectedItem(NirBandChoice.DEFAULT);
        _analyseComboBox.setSelectedItem(AnalyseChoice.DEFAULT);
        _reviewCandidatesCheckBox.setSelected(true);
        _allowPosAngleChangesCheckBox.setSelected(true);

        _tabbedPane.remove(_candidateGuideStarsPanel);
        _tabbedPane.add(_candidateGuideStarsPanel, 0);
    }

    // Returns true if using default settings
    private boolean usingDefaults() {
        return _catalogComboBox.getSelectedItem() == CatalogChoice.DEFAULT
                && _nirBandComboBox.getSelectedItem() == NirBandChoice.DEFAULT
                && _analyseComboBox.getSelectedItem() == AnalyseChoice.DEFAULT
                && _reviewCandidatesCheckBox.isSelected()
                && _allowPosAngleChangesCheckBox.isSelected();
    }

    private void query() throws Exception {
        setState(State.QUERY);
        CatalogChoice catalogChoice = (CatalogChoice) _catalogComboBox.getSelectedItem();
        _model.setCatalog(catalogChoice);
        _model.setBand((NirBandChoice) _nirBandComboBox.getSelectedItem());
        _model.setAnalyseChoice((AnalyseChoice) _analyseComboBox.getSelectedItem());
        _model.setReviewCandidatesBeforeSearch(_reviewCandidatesCheckBox.isSelected());
        _model.setAllowPosAngleAdjustments(_allowPosAngleChangesCheckBox.isSelected());
        _model.setGemsCatalogSearchResults(null);
        _model.setGemsGuideStars(null);

        new SwingWorker() {

            @Override
            public Object construct() {
                try {
                    _controller.query();
                    return null;
                } catch (Exception e) {
                    return e;
                }
            }

            public void finished() {
                _statusPanel.stop();
                Object o = getValue();
                if (o instanceof Exception) {
                    if (o instanceof CancellationException) {
                        DialogUtil.message(GemsGuideStarSearchDialog.this, ((Exception) o).getMessage());
                    } else {
                        DialogUtil.error(GemsGuideStarSearchDialog.this, (Exception) o);
                    }
                    setState(State.PRE_QUERY);
                } else {
                    queryDone();
                }
            }

        }.start();
    }

    private void queryDone() {
        Catalog cat = SkycatConfigFile.getConfigFile().getCatalog(_model.getCatalog().catalogName());
        _candidateGuideStarsTable.setCatalog(cat);

        CandidateGuideStarsTableModel tableModel = new CandidateGuideStarsTableModel(_model);
        try {
            _candidateGuideStarsTable.setTableModel(tableModel);
        } catch (Exception e) {
            DialogUtil.error(e);
        }

        if (tableModel.getRowCount() == 0) {
            setState(State.PRE_QUERY);
        } else {
            _candidateGuideStarsTable.resize(); // fix column widths
            if (_model.isReviewCandidatesBeforeSearch()) {
                setState(State.PRE_ANALYZE);
                // Any changes in the checked candidates causes the state to change to PRE_ANALYZE
                _candidateGuideStarsTable.getTable().getModel().addTableModelListener(e -> setState(State.PRE_ANALYZE));
            } else {
                analyzeDone();
            }
        }
    }


    private void analyze() {
        setState(State.ANALYZE);
        _model.setGemsGuideStars(null);
        _model.setAnalyseChoice((AnalyseChoice) _analyseComboBox.getSelectedItem());
        _model.setAllowPosAngleAdjustments(_allowPosAngleChangesCheckBox.isSelected());

        final List<Target.SiderealTarget> excludeCandidates = _candidateGuideStarsTable.getTableModel().getCandidates(false);

        new SwingWorker() {

            @Override
            public Object construct() {
                try {
                    _controller.analyze(excludeCandidates);
                    return null;
                } catch (Exception e) {
                    return e;
                }
            }

            public void finished() {
                _statusPanel.stop();
                Object o = getValue();
                if (o instanceof Exception) {
                    if (o instanceof GemsGuideStarWorker.NoStarsException || o instanceof CancellationException) {
                        DialogUtil.message(GemsGuideStarSearchDialog.this, ((Exception) o).getMessage());
                        setState(State.PRE_ANALYZE);
                    } else {
                        DialogUtil.error(GemsGuideStarSearchDialog.this, (Exception) o);
                        setState(State.PRE_QUERY);
                    }
                } else {
                    analyzeDone();
                }
            }

        }.start();
    }

    private void analyzeDone() {
        CandidateAsterismsTreeTableModel treeTableModel = new CandidateAsterismsTreeTableModel(
                _model.getGemsGuideStars(), ModelConverters.toOldBand(_model.getBand().getBand()));
        _candidateAsterismsTreeTable.setTreeTableModel(treeTableModel);
        _candidateAsterismsTreeTable.expandAll();
        _candidateAsterismsTreeTable.packAll();
        _candidateAsterismsTreeTable.getColumn(CandidateAsterismsTreeTableModel.Col.PRIMARY.ordinal()).setWidth(5);
        setState(State.SELECTION);
        _candidateAsterismsTreeTable.addCheckedAsterisms();
        _paLabel.setText(getPosAngleStr());
    }

    // Returns a String containing the position angle of the first asterism in the tree table, or empty if the
    // table is empty
    private String getPosAngleStr() {
        List<GemsGuideStars> list = _model.getGemsGuideStars();
        if (list.size() == 0) {
            return "";
        }
        return "Pos Angle: " + NF.format(list.get(0).pa().toDegrees());
    }


    // Interrupt the current background task
    private void stop() {
        _stopAction.setEnabled(false);
        _worker.setInterrupted();
    }

    // Cancel the dialog and revert any changes
    private void cancel() {
        if (_state == State.QUERY || _state == State.ANALYZE) {
            DialogUtil.error(this, "Please press the Stop button first or wait for the current search to complete.");
            return;
        }
        _candidateGuideStarsTable.unplot();
        setVisible(false);

        TargetObsComp targetObsComp = _tpe.getContext().targets().orNull();
        if (targetObsComp != null) {
            targetObsComp.setTargetEnvironment(_savedTargetEnv);
        }
    }


    // Adds the selected asterism groups to the target env
    private void add() {
        _candidateAsterismsTreeTable.addCheckedAsterisms();
        _candidateGuideStarsTable.unplot();
        setVisible(false);
    }

    public void reset() {
        setState(State.PRE_QUERY);
        _candidateGuideStarsTable.unplot();
        _savedTargetEnv = getEnvironment(_tpe);
    }
}
