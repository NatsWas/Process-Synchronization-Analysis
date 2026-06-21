package com.psanalyzer.controller;
import com.psanalyzer.model.algorithms.SynchronizationAlgorithm;
import com.psanalyzer.model.data.MetricsResult;
import com.psanalyzer.model.data.SimConfig;
import com.psanalyzer.model.simulation.SimulationEngine;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
public class CompareController {
    private final AlgorithmController algorithmController;
    private List<MetricsResult> lastCompareResults = new ArrayList<>();
    public CompareController(AlgorithmController algorithmController) {
        this.algorithmController = algorithmController;
    }
    public List<MetricsResult> compareAlgorithms(
            List<String> algoNames,
            Consumer<String> statusCallback) {
        return compareAlgorithms(algoNames, new SimConfig(), statusCallback);
    }
    public List<MetricsResult> compareAlgorithms(
            List<String> algoNames,
            SimConfig baseConfig, 
            Consumer<String> statusCallback) {
        lastCompareResults.clear();
        List<MetricsResult> results = new ArrayList<>();
        for (String name : algoNames) {
            SynchronizationAlgorithm algo = algorithmController.getAlgorithm(name);
            if (algo == null) continue;
            SimConfig cfg = algorithmController.buildDefaultConfig(name);
            if (baseConfig != null) {
                if (baseConfig.getSimulationSteps() > 0)
                    cfg.setSimulationSteps(baseConfig.getSimulationSteps());
                if (baseConfig.getNumberOfProcesses() > 0)
                    cfg.setNumberOfProcesses(baseConfig.getNumberOfProcesses());
                if (baseConfig.getNumberOfResources() > 0)
                    cfg.setNumberOfResources(baseConfig.getNumberOfResources());
                if (baseConfig.getTimeQuantum() > 0)
                    cfg.setTimeQuantum(baseConfig.getTimeQuantum());
                if (baseConfig.getExtraParams() != null) {
                    baseConfig.getExtraParams().forEach(cfg::setExtraParam);
                }
            }

            if (statusCallback != null) {
                statusCallback.accept("Running: " + name);
            }

            SimulationEngine eng = new SimulationEngine();
            eng.configure(algo, cfg);
            try {
                MetricsResult r = eng.runSync();
                if (r != null) results.add(r);
            } catch (Exception e) {
                e.printStackTrace();
                if (statusCallback != null)
                    statusCallback.accept("Error running " + name + ": " + e.getMessage());
            }
        }
        
        lastCompareResults = results;
        return results;
    }

    public Map<String, double[]> buildComparisonMatrix(List<MetricsResult> results) {
        Map<String, double[]> matrix = new LinkedHashMap<>();
        for (MetricsResult r : results) {
            matrix.put(r.getAlgorithmName(), new double[]{
                r.getAvgTurnaroundTime(),
                r.getAvgWaitingTime(),
                r.getAvgResponseTime(),
                r.getCpuUtilization(),
                r.getFairnessIndex(),
                r.getThroughput()
            });
        }
        return matrix;
    }

    public String[] getComparisonHeaders() {
        return new String[]{
            "Algorithm", "Avg Turnaround", "Avg Waiting", 
            "Avg Response", "CPU Util%", "Fairness", "Throughput"
        };
    }

    public MetricsResult getBestBy(List<MetricsResult> results, String metric) {
        if (results == null || results.isEmpty()) return null;
        return switch (metric) {
            case "turnaround" -> results.stream().min((a, b) -> Double.compare(a.getAvgTurnaroundTime(), b.getAvgTurnaroundTime())).orElse(null);
            case "waiting" -> results.stream().min((a, b) -> Double.compare(a.getAvgWaitingTime(), b.getAvgWaitingTime())).orElse(null);
            case "fairness" -> results.stream().max((a, b) -> Double.compare(a.getFairnessIndex(), b.getFairnessIndex())).orElse(null);
            case "cpu" -> results.stream().max((a, b) -> Double.compare(a.getCpuUtilization(), b.getCpuUtilization())).orElse(null);
            case "throughput" -> results.stream().max((a, b) -> Double.compare(a.getThroughput(), b.getThroughput())).orElse(null);
            default -> null;
        };
    }

    public List<MetricsResult> getLastCompareResults() {
        return lastCompareResults;
    }
}