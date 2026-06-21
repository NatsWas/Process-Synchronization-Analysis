package com.psanalyzer.view.panels;
import com.psanalyzer.model.data.MetricsResult;
import com.psanalyzer.util.FairnessCalculator;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
public class MetricsPanel extends JPanel {
    private JLabel[] valueLabels;
    private JLabel fairnessRatingLabel;
    private JProgressBar cpuBar;
    private JProgressBar fairnessBar;
    private JTextArea summaryArea;

    private static final String[] METRIC_NAMES = {
        "Avg Turnaround Time",
        "Avg Waiting Time",
        "Avg Response Time",
        "CPU Utilization (%)",   // "%" lives in the label — value shows plain number + % sign
        "Fairness Index",
        "Throughput (proc/tick)"
    };

    public MetricsPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Performance Metrics",
                TitledBorder.LEFT, TitledBorder.TOP));
        initComponents();
    }

    private void initComponents() {
        JPanel gridPanel = new JPanel(new GridLayout(METRIC_NAMES.length, 2, 6, 6));
        gridPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        valueLabels = new JLabel[METRIC_NAMES.length];
        for (int i = 0; i < METRIC_NAMES.length; i++) {
            JLabel nameLbl = new JLabel(METRIC_NAMES[i] + ":");
            nameLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
            valueLabels[i] = new JLabel("—");
            valueLabels[i].setFont(new Font("Segoe UI", Font.PLAIN, 12));
            valueLabels[i].setForeground(new Color(30, 100, 180));
            gridPanel.add(nameLbl);
            gridPanel.add(valueLabels[i]);
        }

        JPanel barsPanel = new JPanel(new GridLayout(4, 1, 4, 4));
        barsPanel.setBorder(BorderFactory.createTitledBorder("Visual Indicators"));
        barsPanel.add(new JLabel("CPU Utilization:"));
        cpuBar = new JProgressBar(0, 100);
        cpuBar.setStringPainted(true);
        cpuBar.setForeground(new Color(70, 130, 180));
        barsPanel.add(cpuBar);
        barsPanel.add(new JLabel("Fairness Index:"));
        fairnessBar = new JProgressBar(0, 100);
        fairnessBar.setStringPainted(true);
        fairnessBar.setForeground(new Color(60, 179, 113));
        barsPanel.add(fairnessBar);

        fairnessRatingLabel = new JLabel("Rating: —");
        fairnessRatingLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        fairnessRatingLabel.setHorizontalAlignment(SwingConstants.CENTER);

        summaryArea = new JTextArea(6, 28);
        summaryArea.setEditable(false);
        summaryArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        summaryArea.setBackground(new Color(245, 248, 255));
        JScrollPane summaryScroll = new JScrollPane(summaryArea);
        summaryScroll.setBorder(BorderFactory.createTitledBorder("Summary"));
        JPanel topPanel = new JPanel(new BorderLayout(6, 6));
        topPanel.add(gridPanel, BorderLayout.CENTER);
        topPanel.add(barsPanel, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);
        add(fairnessRatingLabel, BorderLayout.CENTER);
        add(summaryScroll, BorderLayout.SOUTH);
    }
    public void updateMetrics(MetricsResult result) {
        if (result == null) return;
        SwingUtilities.invokeLater(() -> {
            double[] vals = {
                result.getAvgTurnaroundTime(),   // 0
                result.getAvgWaitingTime(),       // 1
                result.getAvgResponseTime(),      // 2
                result.getCpuUtilization(),       // 3
                result.getFairnessIndex(),        // 4
                result.getThroughput()            // 5
            };
            for (int i = 0; i < valueLabels.length; i++) {
                valueLabels[i].setText(formatValue(i, vals[i]));
                valueLabels[i].setForeground(colorForMetric(i, vals[i]));
            }
            int cpuPct = (int) Math.min(100, result.getCpuUtilization());
            cpuBar.setValue(cpuPct);
            cpuBar.setString(String.format("%.1f%%", result.getCpuUtilization()));
            cpuBar.setForeground(cpuPct > 80
                    ? new Color(220, 80, 60)
                    : new Color(70, 130, 180));
            int fairPct = (int) Math.round(result.getFairnessIndex() * 100);
            fairnessBar.setValue(fairPct);
            fairnessBar.setString(fairPct + "%");
            fairnessBar.setForeground(fairPct > 85
                    ? new Color(60, 179, 113)
                    : new Color(220, 160, 60));

            fairnessRatingLabel.setText("Fairness Rating: " +
                    FairnessCalculator.interpret(result.getFairnessIndex()));

            summaryArea.setText(result.getSummary());
            summaryArea.setCaretPosition(0);
        });
    }
    private String formatValue(int index, double val) {
        return switch (index) {
            case 0, 1, 2 -> {
                String s = String.format("%.2f", val);
                yield s.replaceAll("0+$", "").replaceAll("\\.$", "");
            }
            case 3 -> String.format("%.2f%%", val);
            case 4 -> {
                String s = String.format("%.2f", val);
                yield s.replaceAll("0+$", "").replaceAll("\\.$", "");
            }
            case 5 -> String.format("%.6f", val);
            default -> String.valueOf(val);
        };
    }

    public void reset() {
        SwingUtilities.invokeLater(() -> {
            for (JLabel lbl : valueLabels) {
                lbl.setText("—");
                lbl.setForeground(new Color(30, 100, 180));
            }
            cpuBar.setValue(0);
            cpuBar.setString("");
            fairnessBar.setValue(0);
            fairnessBar.setString("");
            fairnessRatingLabel.setText("Rating: —");
            summaryArea.setText("");
        });
    }

    private Color colorForMetric(int index, double val) {
        return switch (index) {
            case 3 -> val > 80
                    ? new Color(200, 60, 60)
                    : new Color(30, 140, 70);
            case 4 -> val > 0.85
                    ? new Color(30, 140, 70)
                    : new Color(200, 120, 30);
            default -> new Color(30, 100, 180);
        };
    }
}