package com.psanalyzer.view.compare;
import com.psanalyzer.controller.AlgorithmController;
import com.psanalyzer.controller.CompareController;
import com.psanalyzer.controller.ExportController;
import com.psanalyzer.model.data.MetricsResult;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
public class ComparativeView extends JPanel {
    private static final int MAX_ALGORITHMS = 2;
    private static final double TIME_SCALE = 100.0;
    private final CompareController compareController;
    private final ExportController exportController;
    private final AlgorithmController algorithmController;
    private JList<String> algoList;
    private DefaultTableModel tableModel;
    private JTable resultTable;
    private JTextArea summaryArea;
    private JLabel statusLabel;
    private CompareChartPanel chartPanel;
    private List<MetricsResult> lastResults = new ArrayList<>();
    public ComparativeView(AlgorithmController algorithmController,
                           CompareController compareController,
                           ExportController exportController) {
        this.algorithmController = algorithmController;
        this.compareController   = compareController;
        this.exportController    = exportController;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Algorithm Comparison Mode",
                TitledBorder.LEFT, TitledBorder.TOP));
        initComponents();
    }
    private void initComponents() {
        JPanel leftPanel = new JPanel(new BorderLayout(6, 6));
        leftPanel.setPreferredSize(new Dimension(220, 0));
        leftPanel.setBorder(BorderFactory.createTitledBorder(
                "Select Algorithms (max " + MAX_ALGORITHMS + ")"));
        DefaultListModel<String> listModel = new DefaultListModel<>();
        algorithmController.getAlgorithmNames().forEach(listModel::addElement);
        algoList = new JList<>(listModel);
        algoList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        algoList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        algoList.setToolTipText("Ctrl+click to select multiple (max " + MAX_ALGORITHMS + ")");
        algoList.addListSelectionListener(e -> {
           if (!e.getValueIsAdjusting()) {
            List<String> selected = algoList.getSelectedValuesList();
            if (selected.size() > MAX_ALGORITHMS) {
              java.awt.event.ComponentListener[] listeners = algoList.getListeners(java.awt.event.ComponentListener.class);
              int[] indices = algoList.getSelectedIndices();
              int[] capped  = new int[MAX_ALGORITHMS];
              System.arraycopy(indices, 0, capped, 0, MAX_ALGORITHMS);
              var currentListener = algoList.getListSelectionListeners()[0];
              algoList.removeListSelectionListener(currentListener);
              algoList.setSelectedIndices(capped);
              algoList.addListSelectionListener(currentListener); 
            
              JOptionPane.showMessageDialog(this,
                 "You can compare at most " + MAX_ALGORITHMS + " algorithms at once.\n"
                  + "Only the first " + MAX_ALGORITHMS + " selections have been kept.",
                  "Selection Limit", JOptionPane.INFORMATION_MESSAGE);
             }
            }
          });

        leftPanel.add(new JScrollPane(algoList), BorderLayout.CENTER);

        JButton compareBtn = new JButton("▶  Run Comparison");
        compareBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        compareBtn.setBackground(new Color(70, 130, 180));
        compareBtn.setForeground(Color.WHITE);
        compareBtn.setFocusPainted(false);
        compareBtn.addActionListener(e -> runComparison());
        leftPanel.add(compareBtn, BorderLayout.SOUTH);
        add(leftPanel, BorderLayout.WEST);
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        String[] cols = {
            "Algorithm", "Avg Turnaround", "Avg Waiting",
            "Avg Response", "CPU Util%", "Fairness", "Throughput"
        };
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        resultTable = new JTable(tableModel);
        resultTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        resultTable.setRowHeight(22);
        resultTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        resultTable.setDefaultRenderer(Object.class, new BestValueRenderer());
        tabs.addTab("Results Table", new JScrollPane(resultTable));

        chartPanel = new CompareChartPanel();
        chartPanel.setPreferredSize(new Dimension(600, 350));
        tabs.addTab("Bar Chart", new JScrollPane(chartPanel));

        summaryArea = new JTextArea();
        summaryArea.setEditable(false);
        summaryArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        summaryArea.setBackground(new Color(245, 248, 255));
        tabs.addTab("Text Summary", new JScrollPane(summaryArea));
        add(tabs, BorderLayout.CENTER);

        // --- Bottom: status + export ---
        JPanel bottomPanel = new JPanel(new BorderLayout(6, 4));
        statusLabel = new JLabel("Select algorithms and click Run Comparison");
        statusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        bottomPanel.add(statusLabel, BorderLayout.CENTER);

        JPanel exportPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        JButton exportCsvBtn = new JButton("Export CSV");
        JButton exportTxtBtn = new JButton("Export Text");
        exportCsvBtn.addActionListener(e -> exportCsv());
        exportTxtBtn.addActionListener(e -> exportText());
        exportPanel.add(exportCsvBtn);
        exportPanel.add(exportTxtBtn);
        bottomPanel.add(exportPanel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private static double scaleTime(double rawValue) {
        return rawValue / TIME_SCALE;
    }

    private static double displayTurnaround(MetricsResult r) {
        return scaleTime(r.getAvgTurnaroundTime());
    }

    private static double displayWaiting(MetricsResult r) {
        return scaleTime(r.getAvgWaitingTime());
    }

    private static double displayResponse(MetricsResult r) {
        return scaleTime(r.getAvgResponseTime());
    }

    private void runComparison() {
        List<String> selected = algoList.getSelectedValuesList();

        if (selected.size() < 2) {
            JOptionPane.showMessageDialog(this,
                    "Please select at least 2 algorithms to compare.",
                    "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (selected.size() > MAX_ALGORITHMS) {
            JOptionPane.showMessageDialog(this,
                    "Please select at most " + MAX_ALGORITHMS + " algorithms.\n"
                    + "Currently selected: " + selected.size(),
                    "Too Many Algorithms", JOptionPane.WARNING_MESSAGE);
            return;
        }

        statusLabel.setText("Running comparison…");
        tableModel.setRowCount(0);
        summaryArea.setText("");
        lastResults.clear();

        SwingWorker<List<MetricsResult>, String> worker = new SwingWorker<>() {
            @Override
            protected List<MetricsResult> doInBackground() {
                return compareController.compareAlgorithms(selected, this::publish);
            }
            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty())
                    statusLabel.setText(chunks.get(chunks.size() - 1));
            }
            @Override
            protected void done() {
                try {
                    lastResults = get();
                    populateResults(lastResults);
                    statusLabel.setText(
                            "Comparison complete — " + lastResults.size() + " algorithms compared.");
                } catch (Exception ex) {
                    statusLabel.setText("Error: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void populateResults(List<MetricsResult> results) {
        tableModel.setRowCount(0);
        StringBuilder sb = new StringBuilder();

        for (MetricsResult r : results) {
            tableModel.addRow(new Object[]{
                r.getAlgorithmName(),
                String.format("%.2f", displayTurnaround(r)),
                String.format("%.2f", displayWaiting(r)),
                String.format("%.2f", displayResponse(r)),
                String.format("%.2f", r.getCpuUtilization()),
                String.format("%.4f", r.getFairnessIndex()),
                String.format("%.6f", r.getThroughput())
            });

            sb.append(buildScaledSummary(r))
              .append("\n").append("-".repeat(52)).append("\n\n");
        }

        summaryArea.setText(sb.toString());
        summaryArea.setCaretPosition(0);
        chartPanel.setResults(results);
        chartPanel.repaint();
        highlightBest(results);
    }

    private String buildScaledSummary(MetricsResult r) {
        return String.format(
            "Algorithm    : %s%n" +
            "Turnaround   : %.2f%n" +
            "Waiting      : %.2f%n" +
            "Response     : %.2f%n" +
            "CPU Util%%   : %.2f%n" +
            "Fairness     : %.4f%n" +
            "Throughput   : %.6f",
            r.getAlgorithmName(),
            displayTurnaround(r),
            displayWaiting(r),
            displayResponse(r),
            r.getCpuUtilization(),
            r.getFairnessIndex(),
            r.getThroughput()
        );
    }

    private void highlightBest(List<MetricsResult> results) {
        if (results.isEmpty()) return;
        MetricsResult bestFair = compareController.getBestBy(results, "fairness");
        MetricsResult bestCpu  = compareController.getBestBy(results, "cpu");
        if (bestFair != null)
            statusLabel.setText("Best Fairness: " + bestFair.getAlgorithmName() +
                    " | Best CPU: " + (bestCpu != null ? bestCpu.getAlgorithmName() : "N/A"));
    }

    private void exportCsv() {
        if (lastResults.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No results to export.");
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File("comparison_results.csv"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            boolean ok = exportController.exportComparisonCsv(
                    lastResults, fc.getSelectedFile().getAbsolutePath());
            JOptionPane.showMessageDialog(this, ok ? "Exported successfully!" : "Export failed.");
        }
    }

    private void exportText() {
        if (lastResults.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No results to export.");
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File("comparison_report.txt"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            boolean ok = exportController.exportComparisonText(
                    lastResults, fc.getSelectedFile().getAbsolutePath());
            JOptionPane.showMessageDialog(this, ok ? "Exported successfully!" : "Export failed.");
        }
    }

    private class BestValueRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int col) {
            Component c = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, col);
            if (!isSelected) {
                c.setBackground(Color.WHITE);
                c.setForeground(Color.BLACK);
                ((JLabel) c).setFont(((JLabel) c).getFont().deriveFont(Font.PLAIN));

                if (col >= 1 && col <= 6 && tableModel.getRowCount() > 1) {
                    try {
                        double val = Double.parseDouble(value.toString().replace("%", ""));
                        if (isBestInColumn(col, val)) {
                            c.setBackground(new Color(198, 239, 206));
                            c.setForeground(new Color(0, 100, 0));
                            ((JLabel) c).setFont(((JLabel) c).getFont().deriveFont(Font.BOLD));
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
            return c;
        }

        private boolean isBestInColumn(int col, double val) {
            boolean lowerBetter = (col <= 3);
            double best = lowerBetter ? Double.MAX_VALUE : -Double.MAX_VALUE;
            for (int r = 0; r < tableModel.getRowCount(); r++) {
                try {
                    String cleaned = tableModel.getValueAt(r, col).toString().replace("%", "");
                    double v = Double.parseDouble(cleaned);
                    if (lowerBetter ? v < best : v > best) best = v;
                } catch (NumberFormatException ignored) {}
            }
            return Math.abs(val - best) < 0.0001;
        }
    }

    private class CompareChartPanel extends JPanel {
        private List<MetricsResult> results = new ArrayList<>();
        CompareChartPanel() { setBackground(Color.WHITE); }
        void setResults(List<MetricsResult> r) { this.results = r; }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (results.isEmpty()) {
                g.setColor(Color.LIGHT_GRAY);
                g.setFont(new Font("Segoe UI", Font.ITALIC, 13));
                g.drawString("No comparison data yet", getWidth() / 2 - 90, getHeight() / 2);
                return;
            }
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int margin = 60, topM = 40;
            int chartW = getWidth()  - margin * 2;
            int chartH = getHeight() - topM - 70;
            int n      = results.size();
            int groupW = chartW / n;
            int barW   = Math.max(6, groupW / 3);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
            g2.setColor(Color.DARK_GRAY);
            g2.drawString("Algorithm Comparison — Key Metrics", margin, 26);
            g2.setColor(Color.GRAY);
            g2.drawLine(margin, topM, margin, topM + chartH);
            g2.drawLine(margin, topM + chartH, margin + chartW, topM + chartH);

            double maxT = results.stream().mapToDouble(r -> displayTurnaround(r)).max().orElse(1);
            double maxW = results.stream().mapToDouble(r -> displayWaiting(r)).max().orElse(1);
            double maxC = results.stream().mapToDouble(MetricsResult::getCpuUtilization).max().orElse(1);

            if (maxT <= 0) maxT = 1;
            if (maxW <= 0) maxW = 1;
            if (maxC <= 0) maxC = 1;

            Color[] barColors = {
                new Color(70, 130, 180),
                new Color(255, 165, 0),
                new Color(60, 179, 113)
            };
            String[] legendLabels = { "Turnaround (norm)", "Waiting (norm)", "CPU Util%" };

            for (int i = 0; i < 3; i++) {
                g2.setColor(barColors[i]);
                g2.fillRect(margin + i * 140, topM + chartH + 30, 12, 12);
                g2.setColor(Color.DARK_GRAY);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                g2.drawString(legendLabels[i], margin + i * 140 + 16, topM + chartH + 41);
            }

            for (int i = 0; i < n; i++) {
                MetricsResult r = results.get(i);
                int gx = margin + i * groupW + groupW / 6;

                double[] normVals = {
                    displayTurnaround(r) / maxT * chartH * 0.85,
                    displayWaiting(r)    / maxW * chartH * 0.85,
                    r.getCpuUtilization()/ maxC * chartH * 0.85
                };

                for (int b = 0; b < 3; b++) {
                    int bh = Math.max(1, (int) normVals[b]);
                    int bx = gx + b * (barW + 2);
                    int by = topM + chartH - bh;
                    g2.setColor(barColors[b]);
                    g2.fillRoundRect(bx, by, barW, bh, 4, 4);
                    g2.setColor(barColors[b].darker());
                    g2.drawRoundRect(bx, by, barW, bh, 4, 4);
                }

                String name = r.getAlgorithmName();
                if (name.length() > 14) name = name.substring(0, 13) + "..";
                Graphics2D g2r = (Graphics2D) g2.create();
                g2r.setFont(new Font("Segoe UI", Font.PLAIN, 9));
                g2r.setColor(Color.DARK_GRAY);
                g2r.translate(gx + barW, topM + chartH + 6);
                g2r.rotate(Math.toRadians(-40));
                g2r.drawString(name, 0, 0);
                g2r.dispose();
            }
        }
    }
}