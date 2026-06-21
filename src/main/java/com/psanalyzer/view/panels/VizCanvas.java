package com.psanalyzer.view.panels;
import com.psanalyzer.model.data.MetricsResult;
import com.psanalyzer.model.data.TraceEvent;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
public class VizCanvas extends JPanel {

    private MetricsResult currentResult;
    private final List<TraceEvent> events = new ArrayList<>();
    private final Map<Integer, List<String>> processActivity = new LinkedHashMap<>();
    private JComboBox<String> chartTypeCombo;
    private ChartPanel chartPanel;

    public VizCanvas() {
        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Resource Utilization & Visualization",
                TitledBorder.LEFT, TitledBorder.TOP));
        initComponents();
    }

    private void initComponents() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.add(new JLabel("Chart Type:"));
        chartTypeCombo = new JComboBox<>(new String[]{
            "Resource Utilization Bar",
            "Metrics Radar",
            "Process Activity",
            "Event Distribution"
        });
        chartTypeCombo.addActionListener(e -> chartPanel.repaint());
        toolbar.add(chartTypeCombo);
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> chartPanel.repaint());
        toolbar.add(refreshBtn);
        add(toolbar, BorderLayout.NORTH);
        chartPanel = new ChartPanel();
        chartPanel.setPreferredSize(new Dimension(500, 320));
        add(new JScrollPane(chartPanel), BorderLayout.CENTER);
    }

    public void updateVisualization(MetricsResult result, List<TraceEvent> trace) {
        this.currentResult = result;
        this.events.clear();
        if (trace != null) this.events.addAll(trace);
        buildProcessActivity();
        SwingUtilities.invokeLater(() -> chartPanel.repaint());
    }

    private void buildProcessActivity() {
        processActivity.clear();
        for (TraceEvent e : events) {
            processActivity
                .computeIfAbsent(e.getProcessId(), k -> new ArrayList<>())
                .add(e.getType().name());
        }
    }

    public void reset() {
        currentResult = null;
        events.clear();
        processActivity.clear();
        SwingUtilities.invokeLater(() -> chartPanel.repaint());
    }
    private class ChartPanel extends JPanel {

        ChartPanel() { setBackground(Color.WHITE); }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            String selected = (String) chartTypeCombo.getSelectedItem();
            if (selected == null || currentResult == null) {
                drawPlaceholder(g2);
                return;
            }
            switch (selected) {
                case "Resource Utilization Bar" -> drawResourceBar(g2);
                case "Metrics Radar"            -> drawMetricsBar(g2);
                case "Process Activity"         -> drawProcessActivity(g2);
                case "Event Distribution"       -> drawEventDistribution(g2);
                default                         -> drawPlaceholder(g2);
            }
        }
        //Placeholder
        private void drawPlaceholder(Graphics2D g2) {
            g2.setColor(Color.LIGHT_GRAY);
            g2.setFont(new Font("Segoe UI", Font.ITALIC, 14));
            g2.drawString("Run a simulation to see visualizations",
                    getWidth() / 2 - 160, getHeight() / 2);
        }
        private void drawResourceBar(Graphics2D g2) {
            String[] labels = {
                "CPU Util%",
                "Fairness%",
                "Throughput%",
                "Completed"
            };
            double[] values = {
                currentResult.getCpuUtilization(),          // [0,100] already
                currentResult.getFairnessIndex() * 100.0,  // [0,1]  → [0,100]
                currentResult.getThroughput()    * 100.0,  // ratio  → [0,100]
                currentResult.getCompletedProcesses()       // integer count
            };
            Color[] colors = {
                new Color(70, 130, 180),
                new Color(60, 179, 113),
                new Color(255, 165, 0),
                new Color(147, 112, 219)
            };
            drawBarChart(g2, labels, values, colors,
                    "Resource & Performance Metrics");
        }
        private void drawMetricsBar(Graphics2D g2) {
            String[] labels = { "Turnaround", "Waiting", "Response" };
            double[] values = {
                currentResult.getAvgTurnaroundTime(),   // already [0,100]
                currentResult.getAvgWaitingTime(),      // already [0,100]
                currentResult.getAvgResponseTime()      // already [0,100]
            };
            Color[] colors = {
                new Color(220, 80, 60),
                new Color(255, 165, 0),
                new Color(60, 179, 113)
            };
            drawBarChart(g2, labels, values, colors, "Time Metrics (normalized)");
        }

        //Process Activity
        private void drawProcessActivity(Graphics2D g2) {
            if (processActivity.isEmpty()) { drawPlaceholder(g2); return; }
            int margin = 50, barH = 24, gap = 8;
            int maxCount = processActivity.values().stream()
                    .mapToInt(List::size).max().orElse(1);
            int chartW = getWidth() - margin * 2;
            g2.setColor(new Color(240, 240, 240));
            g2.fillRect(margin, 30, chartW,
                    processActivity.size() * (barH + gap) + 10);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
            g2.setColor(Color.DARK_GRAY);
            g2.drawString("Process Event Count", margin, 22);
            int y = 40;
            for (Map.Entry<Integer, List<String>> entry
                    : processActivity.entrySet()) {
                int pid = entry.getKey();
                int cnt = entry.getValue().size();
                int barW = (int)((double) cnt / maxCount * chartW * 0.85);
                g2.setColor(new Color(70 + pid * 30, 130, 200 - pid * 15));
                g2.fillRoundRect(margin, y, barW, barH, 6, 6);
                g2.setColor(Color.DARK_GRAY);
                g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
                g2.drawString("P" + pid + " (" + cnt + " events)",
                        margin + barW + 6, y + barH / 2 + 4);
                y += barH + gap;
            }
        }

        //Event Distribution 
        private void drawEventDistribution(Graphics2D g2) {
            if (events.isEmpty()) { drawPlaceholder(g2); return; }
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (TraceEvent e : events) {
                String t = e.getType().name();
                counts.merge(t, 1, Integer::sum);
            }
            String[] labels = counts.keySet().toArray(new String[0]);
            double[] values = counts.values().stream()
                    .mapToDouble(Integer::doubleValue).toArray();
            Color[] colors = generateColors(labels.length);
            drawBarChart(g2, labels, values, colors, "Event Type Distribution");
        }
        private void drawBarChart(Graphics2D g2, String[] labels,
                                   double[] values, Color[] colors, String title) {
            int margin   = 50;
            int topMargin = 40;
            int chartW   = getWidth()  - margin * 2;
            int chartH   = getHeight() - topMargin - 60;

            // Determine Y-axis max from actual data; floor at 1 to avoid /0
            double maxVal = 1.0;
            for (double v : values) if (v > maxVal) maxVal = v;

            // Title
            g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
            g2.setColor(Color.DARK_GRAY);
            g2.drawString(title, margin, 22);

            // Axes
            g2.setColor(Color.GRAY);
            g2.drawLine(margin, topMargin, margin, topMargin + chartH);
            g2.drawLine(margin, topMargin + chartH,
                        margin + chartW, topMargin + chartH);

            // Y-axis grid lines and labels
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            for (int i = 0; i <= 5; i++) {
                int yPos = topMargin + chartH - (i * chartH / 5);
                g2.setColor(new Color(220, 220, 220));
                g2.drawLine(margin, yPos, margin + chartW, yPos);
                g2.setColor(Color.GRAY);
                g2.drawString(String.format("%.1f", maxVal * i / 5.0), 2, yPos + 4);
            }

            // Bars
            int barW   = Math.max(8, chartW / (labels.length * 2));
            int spacing = chartW / labels.length;

            for (int i = 0; i < labels.length; i++) {
                int barH = (int)(values[i] / maxVal * chartH);
                int x    = margin + i * spacing + spacing / 4;
                int y    = topMargin + chartH - barH;

                // Bar fill and border
                g2.setColor(colors[i % colors.length]);
                g2.fillRoundRect(x, y, barW, barH, 4, 4);
                g2.setColor(colors[i % colors.length].darker());
                g2.drawRoundRect(x, y, barW, barH, 4, 4);

                // Value label above bar
                g2.setColor(Color.DARK_GRAY);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                g2.drawString(String.format("%.1f", values[i]), x, y - 3);

                // Rotated category label below X-axis
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 9));
                Graphics2D g2r = (Graphics2D) g2.create();
                g2r.translate(x + barW / 2, topMargin + chartH + 10);
                g2r.rotate(Math.toRadians(-35));
                g2r.setColor(Color.DARK_GRAY);
                g2r.drawString(labels[i].length() > 12
                        ? labels[i].substring(0, 11) + ".."
                        : labels[i], 0, 0);
                g2r.dispose();
            }
        }
        private Color[] generateColors(int n) {
            Color[] palette = {
                new Color(70, 130, 180), new Color(60, 179, 113),
                new Color(255, 165, 0),  new Color(220, 80, 60),
                new Color(147, 112, 219),new Color(0, 188, 212),
                new Color(255, 87, 34),  new Color(76, 175, 80)
            };
            Color[] result = new Color[n];
            for (int i = 0; i < n; i++) result[i] = palette[i % palette.length];
            return result;
        }
    }
}