package com.psanalyzer.util;
import com.psanalyzer.model.data.MetricsResult;
import com.psanalyzer.model.data.TraceEvent;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
public class TextExporter {

    private static final String DIVIDER  = "=".repeat(60);
    private static final String DIVIDER2 = "-".repeat(60);

    public static boolean exportFullReport(List<TraceEvent> trace,
                                            MetricsResult metrics,
                                            String filePath) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            printHeader(pw, "SIMULATION REPORT");
            pw.println();
            if (metrics != null) {
                pw.println("ALGORITHM : " + metrics.getAlgorithmName());
                pw.println();
                pw.println("--- PERFORMANCE METRICS ---");
                pw.println(metrics.getSummary());
                pw.println();
                pw.println("--- FAIRNESS ANALYSIS ---");
                pw.printf("Fairness Index : %.4f  →  %s%n",
                        metrics.getFairnessIndex(),
                        FairnessCalculator.interpret(metrics.getFairnessIndex()));
                pw.println();
            }
            pw.println("--- EXECUTION TRACE ---");
            pw.println(String.format("%-8s %-5s %-5s %-22s %s",
                    "Time", "PID", "RID", "EventType", "Description"));
            pw.println(DIVIDER2);
            for (TraceEvent e : trace) pw.println(e.toString());
            pw.println(DIVIDER2);
            pw.println("Total events: " + trace.size());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean exportMetricsSummary(MetricsResult metrics,
                                                String filePath) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            printHeader(pw, "METRICS SUMMARY");
            pw.println();
            pw.println(metrics.getSummary());
            pw.println();
            pw.printf("Fairness Rating: %s%n",
                    FairnessCalculator.interpret(metrics.getFairnessIndex()));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean exportComparisonReport(List<MetricsResult> results,
                                                   String filePath) {
        if (results == null || results.isEmpty()) return false;
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            printHeader(pw, "ALGORITHM COMPARISON REPORT");
            pw.println("Algorithms compared: " + results.size());
            pw.println();
            pw.println(String.format("%-32s %10s %10s %10s %9s %8s %10s",
                    "Algorithm", "Turnaround", "Waiting",
                    "Response", "CPU(%)", "Fairness", "Throughput"));
            pw.println(DIVIDER2);
            for (MetricsResult r : results) {
                pw.println(String.format("%-32s %10.2f %10.2f %10.2f %8.2f%% %8.4f %10.6f",
                        r.getAlgorithmName(),
                        r.getAvgTurnaroundTime(),
                        r.getAvgWaitingTime(),
                        r.getAvgResponseTime(),
                        r.getCpuUtilization(),   // stored as 0–100, rendered with %%
                        r.getFairnessIndex(),
                        r.getThroughput()));
            }
            pw.println(DIVIDER2);
            pw.println();
            pw.println("--- INDIVIDUAL SUMMARIES ---");
            pw.println();
            for (MetricsResult r : results) {
                pw.println(r.getSummary());
                pw.println(DIVIDER2);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean exportTraceOnly(List<TraceEvent> trace,
                                           String algorithmName,
                                           String filePath) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            printHeader(pw, "EXECUTION TRACE — " + algorithmName);
            pw.println("Total events: " + trace.size());
            pw.println();
            pw.println(String.format("%-8s %-5s %-5s %-22s %s",
                    "Time", "PID", "RID", "EventType", "Description"));
            pw.println(DIVIDER2);
            for (TraceEvent e : trace) pw.println(e.toString());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void printHeader(PrintWriter pw, String title) {
        pw.println(DIVIDER);
        pw.println("  PROCESS SYNCHRONIZATION ANALYZER");
        pw.println("  " + title);
        pw.println("  Generated: " + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        pw.println(DIVIDER);
    }
}