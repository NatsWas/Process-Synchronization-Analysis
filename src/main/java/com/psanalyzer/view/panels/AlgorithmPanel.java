package com.psanalyzer.view.panels;
import com.psanalyzer.controller.AlgorithmController;
import com.psanalyzer.model.data.SimConfig;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
public class AlgorithmPanel extends JPanel {
    private final AlgorithmController algorithmController;
    private JComboBox<String> algoCombo;
    private JTextArea descArea;
    private JPanel dynamicInputPanel;
    private final Map<String, JComponent> inputFields = new LinkedHashMap<>();
    public AlgorithmPanel(AlgorithmController algorithmController) {
        this.algorithmController = algorithmController;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Algorithm Selection & Parameters",
                TitledBorder.LEFT, TitledBorder.TOP));
        initComponents();
    }
    private void initComponents() {
        JPanel topPanel = new JPanel(new BorderLayout(6, 6));
        topPanel.add(new JLabel("Algorithm:"), BorderLayout.WEST);
        algoCombo = new JComboBox<>(
                algorithmController.getAlgorithmNames().toArray(new String[0]));
        algoCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        topPanel.add(algoCombo, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);
        descArea = new JTextArea(3, 30);
        descArea.setEditable(false);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        descArea.setBackground(new Color(245, 245, 250));
        JScrollPane descScroll = new JScrollPane(descArea);
        descScroll.setBorder(BorderFactory.createTitledBorder("Description"));
        add(descScroll, BorderLayout.CENTER);
        dynamicInputPanel = new JPanel();
        dynamicInputPanel.setLayout(new BoxLayout(dynamicInputPanel, BoxLayout.Y_AXIS));
        dynamicInputPanel.setBorder(BorderFactory.createTitledBorder("Parameters"));
        JScrollPane inputScroll = new JScrollPane(dynamicInputPanel);
        inputScroll.setPreferredSize(new Dimension(300, 220));
        add(inputScroll, BorderLayout.SOUTH);
        algoCombo.addActionListener(e -> refreshForAlgorithm(
                (String) algoCombo.getSelectedItem()));
        refreshForAlgorithm((String) algoCombo.getSelectedItem());
    }
    private void refreshForAlgorithm(String algoName) {
        if (algoName == null) return;
        var algo = algorithmController.getAlgorithm(algoName);
        if (algo != null) descArea.setText(algo.getDescription());
        dynamicInputPanel.removeAll();
        inputFields.clear();
        SimConfig defaults = algorithmController.buildDefaultConfig(algoName);
        addIntField("Number of Processes", "numberOfProcesses",
                defaults.getNumberOfProcesses());
        addIntField("Simulation Steps", "simulationSteps",
                defaults.getSimulationSteps());
        addIntField("Step Delay (ms)", "stepDelay", 30);
        switch (algoName) {
            case "Peterson's Algorithm":
            case "Dekker's Algorithm":
                addIntField("Critical Section Count", "criticalSectionCount",
                        defaults.getExtraParamInt("criticalSectionCount", 5));
                break;
            case "Lamport's Fast Mutex":
                addIntField("Critical Section Count", "criticalSectionCount",
                        defaults.getExtraParamInt("criticalSectionCount", 4));
                break;
            case "Monitor Producer-Consumer":
                addIntField("Buffer Size", "bufferSize",
                        defaults.getExtraParamInt("bufferSize", 5));
                addIntField("Producers", "producers",
                        defaults.getExtraParamInt("producers", 2));
                addIntField("Consumers", "consumers",
                        defaults.getExtraParamInt("consumers", 2));
                addIntField("Items Each", "itemsEach",
                        defaults.getExtraParamInt("itemsEach", 4));
                break;
            case "Dining Philosophers":
                addIntField("Meals Per Philosopher", "mealsPerPhilosopher",
                        defaults.getExtraParamInt("mealsPerPhilosopher", 3));
                break;
            case "Readers-Writers (Writer Priority)":
                addIntField("Readers", "readers",
                        defaults.getExtraParamInt("readers", 3));
                addIntField("Writers", "writers",
                        defaults.getExtraParamInt("writers", 2));
                addIntField("Operations Each", "operationsEach",
                        defaults.getExtraParamInt("operationsEach", 4));
                break;
            case "Sleeping Barber":
                addIntField("Waiting Chairs", "waitingChairs",
                        defaults.getExtraParamInt("waitingChairs", 3));
                break;
            case "Filter Lock":
            case "Eisenberg & McGuire":
            case "Black-White Bakery Algorithm":
            case "Yang-Anderson Algorithm":
                addIntField("Critical Section Count", "criticalSectionCount",
                        defaults.getExtraParamInt("criticalSectionCount", 3));
                break;
            case "Cigarette Smokers":
                addIntField("Rounds", "rounds",
                        defaults.getExtraParamInt("rounds", 6));
                break;
        }
        dynamicInputPanel.revalidate();
        dynamicInputPanel.repaint();
    }
    private void addIntField(String label, String key, int defaultValue) {
        JPanel row = new JPanel(new BorderLayout(6, 4));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JLabel lbl = new JLabel(label + ":");
        lbl.setPreferredSize(new Dimension(180, 26));
        JTextField tf = new JTextField(String.valueOf(defaultValue), 6);
        tf.setName(key);
        row.add(lbl, BorderLayout.WEST);
        row.add(tf, BorderLayout.CENTER);
        inputFields.put(key, tf);
        dynamicInputPanel.add(row);
        dynamicInputPanel.add(Box.createVerticalStrut(2));
    }
    public SimConfig buildConfig() {
        String algoName = (String) algoCombo.getSelectedItem();
        SimConfig cfg = algorithmController.buildDefaultConfig(algoName);
        cfg.setAlgorithmName(algoName);
        cfg.setNumberOfProcesses(getInt("numberOfProcesses",
                cfg.getNumberOfProcesses()));
        cfg.setSimulationSteps(getInt("simulationSteps",
                cfg.getSimulationSteps()));
        for (String key : inputFields.keySet()) {
            if (!key.equals("numberOfProcesses")
                    && !key.equals("simulationSteps")
                    && !key.equals("stepDelay")) {
                cfg.setExtraParam(key, getInt(key, 0));
            }
        }
        return cfg;
    }
    public int getStepDelay() {
        return getInt("stepDelay", 30);
    }
    private int getInt(String key, int fallback) {
        JComponent comp = inputFields.get(key);
        if (comp instanceof JTextField tf) {
            try { return Integer.parseInt(tf.getText().trim()); }
            catch (NumberFormatException e) { return fallback; }
        }
        return fallback;
    }
    public String getSelectedAlgorithm() {
        return (String) algoCombo.getSelectedItem();
    }
}