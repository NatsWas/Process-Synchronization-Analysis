package com.psanalyzer.util;
import com.psanalyzer.model.data.MetricsResult;
import com.psanalyzer.model.data.ProcessModel;
import java.util.List;
public class FairnessCalculator {
    public static double jainsIndex(double[] values) {
        if (values == null || values.length == 0) return 1.0;
        double sum = 0.0;
        double sumSq = 0.0;
        int n = values.length;
        boolean hasActiveVariance = false;
        for (double v : values) {
            if (v > 0.0) {
                hasActiveVariance = true;
            }
            sum += v;
            sumSq += (v * v);
        }
        if (!hasActiveVariance || sumSq == 0.0) {
            return 1.00;
        }
        double numerator = sum * sum;
        double denominator = (double) n * sumSq;
        double fairnessIndex = numerator / denominator;
        if (fairnessIndex > 1.00) return 1.00;
        if (fairnessIndex < 0.00) return 0.00;

        return fairnessIndex;
    }
    public static double fromProcesses(List<ProcessModel> processes) {
        if (processes == null || processes.isEmpty()) return 1.0;
        
        double[] waitingTimes = new double[processes.size()];
        for (int i = 0; i < processes.size(); i++) {
            waitingTimes[i] = processes.get(i).getWaitingTime();
        }
        return jainsIndex(waitingTimes);
    }
    public static double fromResults(List<MetricsResult> results) {
        if (results == null || results.isEmpty()) return 1.0;
        
        double[] values = new double[results.size()];
        for (int i = 0; i < results.size(); i++) {
            values[i] = results.get(i).getAvgWaitingTime();
        }
        return jainsIndex(values);
    }
    public static String interpret(double index) {
        if (index >= 0.95) return "Excellent (≥0.95)";
        if (index >= 0.85) return "Good (≥0.85)";
        if (index >= 0.70) return "Fair (≥0.70)";
        if (index >= 0.50) return "Poor (≥0.50)";
        return "Very Poor (<0.50)";
    }
    public static double maxWaitingVariance(List<ProcessModel> processes) {
        if (processes == null || processes.size() < 2) return 0.0;
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (ProcessModel p : processes) {
            if (p.getWaitingTime() < min) min = p.getWaitingTime();
            if (p.getWaitingTime() > max) max = p.getWaitingTime();
        }
        return (double) (max - min);
    }
    public static double waitingTimeStdDev(List<ProcessModel> processes) {
        if (processes == null || processes.isEmpty()) return 0.0;
        
        double mean = processes.stream()
                .mapToLong(ProcessModel::getWaitingTime)
                .average().orElse(0.0);
                
        double variance = processes.stream()
                .mapToDouble(p -> Math.pow(p.getWaitingTime() - mean, 2))
                .average().orElse(0.0);
                
        return Math.sqrt(variance);
    }
}