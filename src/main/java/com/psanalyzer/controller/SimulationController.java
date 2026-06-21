package com.psanalyzer.controller;
import com.psanalyzer.model.algorithms.SynchronizationAlgorithm;
import com.psanalyzer.model.data.MetricsResult;
import com.psanalyzer.model.data.SimConfig;
import com.psanalyzer.model.data.TraceEvent;
import com.psanalyzer.model.simulation.SimulationEngine;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
public class SimulationController {
    private final SimulationEngine engine;
    private final AlgorithmController algorithmController;
    private SimConfig currentConfig;
    public SimulationController(AlgorithmController algorithmController) {
        this.engine = new SimulationEngine();
        this.algorithmController = algorithmController;
    }
    public void addTraceListener(Consumer<TraceEvent> listener) {
        engine.addTraceListener(e -> {
            if (currentConfig != null && e.getTimestamp() > currentConfig.getSimulationSteps()) {
                return;
            }
            listener.accept(e);
        });
    }
    public void addMetricsListener(Consumer<MetricsResult> listener) {
        engine.addMetricsListener(listener);
    }
    public boolean startSimulation(SimConfig config, Runnable onComplete) {
        String algoName = config.getAlgorithmName();
        if (algoName == null || algoName.isEmpty()) return false;
        SynchronizationAlgorithm algo = algorithmController.getAlgorithm(algoName);
        if (algo == null) return false;
        
        currentConfig = config;
        engine.configure(algo, config);
        engine.runAsync(onComplete);
        return true;
    }
    public MetricsResult startSyncSimulation(SimConfig config) {
        String algoName = config.getAlgorithmName();
        if (algoName == null || algoName.isEmpty()) return null;
        SynchronizationAlgorithm algo = algorithmController.getAlgorithm(algoName);
        if (algo == null) return null;   
        currentConfig = config;
        engine.configure(algo, config);
        return engine.runSync();
    }
    public void pauseSimulation()  { engine.pause(); }
    public void resumeSimulation() { engine.resume(); }
    public void stopSimulation()   { engine.stop(); }
    public boolean isRunning() { return engine.isRunning(); }
    public boolean isPaused()  { return engine.isPaused(); }
    public List<TraceEvent> getTraceLog() {
        List<TraceEvent> rawLog = engine.getTraceLog();
        if (currentConfig == null) return rawLog;

        long maxSteps = currentConfig.getSimulationSteps();
        return rawLog.stream()
                .filter(e -> e.getTimestamp() <= maxSteps)
                .collect(Collectors.toList());
    }

    public MetricsResult getMetricsResult() { return engine.getMetricsResult(); }
    public SimConfig getCurrentConfig() { return currentConfig; }
    public void setStepDelay(int ms) { engine.setStepDelayMs(ms); }
}