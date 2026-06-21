package com.psanalyzer.view.panels;
import com.psanalyzer.model.data.TraceEvent;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
public class TracePanel extends JPanel {
    private DefaultTableModel tableModel;
    private JTable table;
    private JLabel eventCountLabel;
    private JTextField filterField;
    private JCheckBox autoScrollBox;
    public TracePanel() {
        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Execution Trace"));
        initComponents();
    }
    private void initComponents() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.add(new JLabel("Filter:"));
        filterField = new JTextField(14);
        filterField.setToolTipText("Filter by keyword");
        filterField.addActionListener(e -> applyFilter());
        toolbar.add(filterField);
        JButton filterBtn = new JButton("Apply");
        filterBtn.addActionListener(e -> applyFilter());
        toolbar.add(filterBtn);
        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> clearTrace());
        toolbar.add(clearBtn);
        autoScrollBox = new JCheckBox("Auto-scroll", true);
        toolbar.add(autoScrollBox);
        eventCountLabel = new JLabel("Events: 0");
        eventCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        toolbar.add(eventCountLabel);
        add(toolbar, BorderLayout.NORTH);
        String[] cols = {"Time", "PID", "Event Type", "Description"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        table.setRowHeight(20);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getColumnModel().getColumn(0).setPreferredWidth(55);   // Time
        table.getColumnModel().getColumn(1).setPreferredWidth(40);   // PID
        table.getColumnModel().getColumn(2).setPreferredWidth(160);  // Event Type (Was index 3)
        table.getColumnModel().getColumn(3).setPreferredWidth(420);  // Description (Was index 4)

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(
                        t, val, sel, foc, row, col);
                if (!sel) {
                    String type = (String) t.getValueAt(row, 2);
                    c.setBackground(colorForType(type));
                    c.setForeground(Color.BLACK);
                }
                return c;
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        add(scroll, BorderLayout.CENTER);

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        legend.add(colorLegend("Acquire OK", new Color(198, 239, 206)));
        legend.add(colorLegend("Acquire Fail/Wait", new Color(255, 235, 156)));
        legend.add(colorLegend("Busy Wait", new Color(255, 199, 206)));
        legend.add(colorLegend("Release/Signal", new Color(204, 229, 255)));
        legend.add(colorLegend("Start/Finish", new Color(230, 224, 245)));
        add(legend, BorderLayout.SOUTH);
    }

    private Color colorForType(String type) {
        if (type == null) return Color.WHITE;
        return switch (type) {
            case "ACQUIRE_SUCCESS"              -> new Color(198, 239, 206);
            case "ACQUIRE_FAIL", "WAIT", "BLOCK"-> new Color(255, 235, 156);
            case "BUSY_WAIT"                    -> new Color(255, 199, 206);
            case "RELEASE", "SIGNAL", "UNBLOCK" -> new Color(204, 229, 255);
            case "PROCESS_START","PROCESS_FINISH",
                 "PROCESS_ARRIVE"               -> new Color(230, 224, 245);
            default                             -> Color.WHITE;
        };
    }

    private JPanel colorLegend(String label, Color color) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        JPanel box = new JPanel();
        box.setBackground(color);
        box.setPreferredSize(new Dimension(14, 14));
        box.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        p.add(box);
        p.add(new JLabel(label));
        return p;
    }
    public void addEvent(TraceEvent event) {
        SwingUtilities.invokeLater(() -> {
            tableModel.addRow(new Object[]{
                event.getTimestamp(),
                "P" + event.getProcessId(),
                event.getType().name(),
                event.getDescription()
            });
            eventCountLabel.setText("Events: " + tableModel.getRowCount());
            if (autoScrollBox.isSelected()) {
                int last = table.getRowCount() - 1;
                if (last >= 0)
                    table.scrollRectToVisible(table.getCellRect(last, 0, true));
            }
        });
    }

    public void clearTrace() {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            eventCountLabel.setText("Events: 0");
        });
    }

    private void applyFilter() {
        String keyword = filterField.getText().trim().toLowerCase();
        if (keyword.isEmpty()) return;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String desc = String.valueOf(tableModel.getValueAt(i, 3)).toLowerCase();
            if (desc.contains(keyword)) {
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
                table.setRowSelectionInterval(i, i);
                break;
            }
        }
    }

    public int getEventCount() { return tableModel.getRowCount(); }
}