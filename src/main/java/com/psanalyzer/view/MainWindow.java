package com.psanalyzer.view;
import com.psanalyzer.controller.*;
import com.psanalyzer.model.data.MetricsResult;
import com.psanalyzer.model.data.SimConfig;
import com.psanalyzer.model.data.TraceEvent;
import com.psanalyzer.view.compare.ComparativeView;
import com.psanalyzer.view.panels.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
public class MainWindow extends JFrame {
    private final AlgorithmController algorithmController;
    private final SimulationController simulationController;
    private final MetricsController metricsController;
    private final CompareController compareController;
    private final ExportController exportController;
    private AlgorithmPanel algorithmPanel;
    private TracePanel tracePanel;
    private MetricsPanel metricsPanel;
    private VizCanvas vizCanvas;
    private ComparativeView comparativeView;
    private JButton runBtn, pauseBtn, stopBtn;
    private JLabel statusLabel;
    private JSpinner speedSpinner;
    public MainWindow() {
        algorithmController=new AlgorithmController();
        simulationController=new SimulationController(algorithmController);
        metricsController=new MetricsController();
        compareController=new CompareController(algorithmController);
        exportController=new ExportController();
        initWindow();
        initPanels();
        wireListeners();
        setVisible(true);
    }
    private void initWindow() {
        setTitle("Process Synchronization Analyzer");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1280, 820);
        setMinimumSize(new Dimension(1000, 680));
        setLocationRelativeTo(null);
        try {
            UIManager.setLookAndFeel(
                    UIManager.getSystemLookAndFeelClassName());
            SwingUtilities.updateComponentTreeUI(this);
        } catch (Exception ignored) {}
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (simulationController.isRunning()) {
                    int opt = JOptionPane.showConfirmDialog(
                            MainWindow.this,
                            "Simulation is running. Stop and exit?",
                            "Confirm Exit",
                            JOptionPane.YES_NO_OPTION);
                    if (opt != JOptionPane.YES_OPTION) return;
                    simulationController.stopSimulation();
                }
                dispose();
                System.exit(0);
            }
        });
    }
    private void initPanels() {
        setJMenuBar(buildMenuBar());
        JPanel root=new JPanel(new BorderLayout(6, 6));
        root.setBorder(new EmptyBorder(8, 8, 8, 8));
        setContentPane(root);
        root.add(buildToolbar(), BorderLayout.NORTH);
        algorithmPanel = new AlgorithmPanel(algorithmController);
        algorithmPanel.setPreferredSize(new Dimension(310, 0));
        JTabbedPane centerTabs = new JTabbedPane();
        centerTabs.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tracePanel=new TracePanel();
        metricsPanel=new MetricsPanel();
        vizCanvas=new VizCanvas();
        centerTabs.addTab("Execution Trace",     tracePanel);
        centerTabs.addTab("Performance Metrics", metricsPanel);
        centerTabs.addTab("Visualizations",      vizCanvas);
        comparativeView = new ComparativeView(
                algorithmController, compareController, exportController);
        centerTabs.addTab("Compare Algorithms", comparativeView);
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                algorithmPanel, centerTabs);
        splitPane.setDividerLocation(315);
        splitPane.setResizeWeight(0.0);
        root.add(splitPane, BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
    }
    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu=new JMenu("File");
        JMenuItem exportMetricsCsv=new JMenuItem("Export Metrics CSV");
        JMenuItem exportTraceCsv=new JMenuItem("Export Trace CSV");
        JMenuItem exportFullReport=new JMenuItem("Export Full Report (.txt)");
        JMenuItem exitItem=new JMenuItem("Exit");
        exportMetricsCsv.addActionListener(e -> doExportMetricsCsv());
        exportTraceCsv.addActionListener(e   -> doExportTraceCsv());
        exportFullReport.addActionListener(e -> doExportFullReport());
        exitItem.addActionListener(e -> dispatchEvent(
                new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));
        fileMenu.add(exportMetricsCsv);
        fileMenu.add(exportTraceCsv);
        fileMenu.add(exportFullReport);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        JMenu simMenu=new JMenu("Simulation");
        JMenuItem runItem=new JMenuItem("Run");
        JMenuItem pauseItem=new JMenuItem("Pause/Resume");
        JMenuItem stopItem=new JMenuItem("Stop");
        JMenuItem clearItem=new JMenuItem("Clear Results");
        runItem.addActionListener(e   -> runSimulation());
        pauseItem.addActionListener(e -> togglePause());
        stopItem.addActionListener(e  -> stopSimulation());
        clearItem.addActionListener(e -> clearAll());
        simMenu.add(runItem);
        simMenu.add(pauseItem);
        simMenu.add(stopItem);
        simMenu.addSeparator();
        simMenu.add(clearItem);
        menuBar.add(simMenu);
        JMenu helpMenu = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Process Synchronization Analyzer\n" +
                "Version 1.0\n\n" +
                "12 Classic Synchronization Algorithms\n" +
                "Discrete-Event Simulation Engine\n" +
                "Performance Metrics & Visualization\n\n" +
                "Built with Java Swing (MVC Architecture)",
                "About", JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(aboutItem);
        menuBar.add(helpMenu);
        return menuBar;
    }
    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.setBorder(BorderFactory.createEtchedBorder());
        toolbar.setBackground(new Color(245, 247, 252));
        runBtn=makeButton("Run",   new Color(46, 139, 87));
        pauseBtn=makeButton("Pause", new Color(70, 130, 180));
        stopBtn=makeButton("Stop",  new Color(178, 34, 34));
        pauseBtn.setEnabled(false);
        stopBtn.setEnabled(false);
        runBtn.addActionListener(e   -> runSimulation());
        pauseBtn.addActionListener(e -> togglePause());
        stopBtn.addActionListener(e  -> stopSimulation());
        toolbar.add(runBtn);
        toolbar.add(pauseBtn);
        toolbar.add(stopBtn);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(new JLabel("Speed (ms/step):"));
        speedSpinner = new JSpinner(
                new SpinnerNumberModel(30, 0, 500, 10));
        speedSpinner.setPreferredSize(new Dimension(72, 28));
        toolbar.add(speedSpinner);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> clearAll());
        toolbar.add(clearBtn);
        return toolbar;
    }
    private JButton makeButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(6, 0));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(),
                new EmptyBorder(2, 8, 2, 8)));
        bar.setBackground(new Color(240, 242, 248));
        statusLabel = new JLabel(
                "Ready — select an algorithm and click Run");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        bar.add(statusLabel, BorderLayout.WEST);
        JLabel versionLabel = new JLabel("PS Analyzer v1.0");
        versionLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        versionLabel.setForeground(Color.GRAY);
        bar.add(versionLabel, BorderLayout.EAST);
        return bar;
    }
    private void wireListeners() {
        simulationController.addTraceListener(event ->
                SwingUtilities.invokeLater(
                        () -> tracePanel.addEvent(event)));
        simulationController.addMetricsListener(result ->
                SwingUtilities.invokeLater(() -> {
                    metricsController.onNewResult(result);
                    metricsPanel.updateMetrics(result);
                    vizCanvas.updateVisualization(result,
                            simulationController.getTraceLog());
                    setStatus("Simulation complete — " +
                            result.getAlgorithmName());
                    runBtn.setEnabled(true);
                    pauseBtn.setEnabled(false);
                    stopBtn.setEnabled(false);
                }));
    }
    private void runSimulation() {
        if (simulationController.isRunning()) return;
        SimConfig cfg = algorithmPanel.buildConfig();
        if (cfg.getAlgorithmName() == null) {
            JOptionPane.showMessageDialog(this,
                    "Please select an algorithm first.");
            return;
        }
        tracePanel.clearTrace();
        metricsPanel.reset();
        vizCanvas.reset();
        int delay = (Integer) speedSpinner.getValue();
        simulationController.setStepDelay(delay);
        setStatus("Running: " + cfg.getAlgorithmName() + "...");
        runBtn.setEnabled(false);
        pauseBtn.setEnabled(true);
        stopBtn.setEnabled(true);
        boolean started = simulationController.startSimulation(
                cfg, () -> SwingUtilities.invokeLater(() -> {
                    runBtn.setEnabled(true);
                    pauseBtn.setEnabled(false);
                    stopBtn.setEnabled(false);
                }));
        if (!started) {
            setStatus("Failed to start simulation.");
            runBtn.setEnabled(true);
            pauseBtn.setEnabled(false);
            stopBtn.setEnabled(false);
        }
    }
    private void togglePause() {
        if (simulationController.isPaused()) {
            simulationController.resumeSimulation();
            pauseBtn.setText("Pause");
            setStatus("Simulation resumed...");
        } else {
            simulationController.pauseSimulation();
            pauseBtn.setText("Resume");
            setStatus("Simulation paused.");
        }
    }
    private void stopSimulation() {
        simulationController.stopSimulation();
        runBtn.setEnabled(true);
        pauseBtn.setEnabled(false);
        pauseBtn.setText("Pause");
        stopBtn.setEnabled(false);
        setStatus("Simulation stopped.");
    }
    private void clearAll() {
        stopSimulation();
        tracePanel.clearTrace();
        metricsPanel.reset();
        vizCanvas.reset();
        metricsController.clearHistory();
        setStatus("Cleared — ready for new simulation.");
    }
    private void doExportMetricsCsv() {
        MetricsResult r = metricsController.getCurrent();
        if (r == null) {
            JOptionPane.showMessageDialog(this,
                    "No metrics to export yet.");
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File(
                exportController.generateDefaultFileName(
                        r.getAlgorithmName(), "metrics") + ".csv"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            boolean ok = exportController.exportMetricsCsv(
                    r, fc.getSelectedFile().getAbsolutePath());
            JOptionPane.showMessageDialog(this,
                    ok ? "Metrics exported!" : "Export failed.");
        }
    }
    private void doExportTraceCsv() {
        List<TraceEvent> trace = simulationController.getTraceLog();
        if (trace == null || trace.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No trace data to export.");
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File("trace_export.csv"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            boolean ok = exportController.exportTraceCsv(
                    trace, fc.getSelectedFile().getAbsolutePath());
            JOptionPane.showMessageDialog(this,
                    ok ? "Trace exported!" : "Export failed.");
        }
    }
    private void doExportFullReport() {
        List<TraceEvent> trace = simulationController.getTraceLog();
        MetricsResult r = metricsController.getCurrent();
        if (trace == null || trace.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No data to export yet.");
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File("full_report.txt"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            boolean ok = exportController.exportTraceText(
                    trace, r, fc.getSelectedFile().getAbsolutePath());
            JOptionPane.showMessageDialog(this,
                    ok ? "Report exported!" : "Export failed.");
        }
    }
    private void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
    }
}
