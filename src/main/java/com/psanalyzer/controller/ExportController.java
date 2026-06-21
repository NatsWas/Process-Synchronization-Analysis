package com.psanalyzer.controller;
import com.psanalyzer.model.data.MetricsResult;
import com.psanalyzer.model.data.TraceEvent;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
public class ExportController {
    public boolean exportMetricsCsv(MetricsResult result, String filePath) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            pw.println(result.toCsvHeader());
            pw.println(result.toCsvRow());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    public boolean exportTraceCsv(List<TraceEvent> trace, String filePath) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            pw.println("Timestamp,ProcessID,ResourceID,EventType,Description");
            for (TraceEvent e : trace) pw.println(e.toCsv());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    public boolean exportTraceText(List<TraceEvent> trace,
                                    MetricsResult metrics,
                                    String filePath) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            pw.println("========================================");
            pw.println("  PROCESS SYNCHRONIZATION ANALYZER");
            pw.println("  Export: " + LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            pw.println("========================================");
            pw.println();
            if (metrics != null) {
                pw.println("--- METRICS SUMMARY ---");
                pw.println(metrics.getSummary());
                pw.println();
            }
            pw.println("--- EXECUTION TRACE ---");
            pw.println(String.format("%-8s %-6s %-6s %-20s %s",
                    "Time", "PID", "RID", "Event", "Description"));
            pw.println("-".repeat(72));
            for (TraceEvent e : trace) pw.println(e.toString());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    public boolean exportComparisonCsv(List<MetricsResult> results, String filePath) {
        if (results == null || results.isEmpty()) return false;
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            pw.println(results.get(0).toCsvHeader());
            for (MetricsResult r : results) pw.println(r.toCsvRow());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    public boolean exportComparisonText(List<MetricsResult> results, String filePath) {
        if (results == null || results.isEmpty()) return false;
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            pw.println("========================================");
            pw.println("  ALGORITHM COMPARISON REPORT");
            pw.println("  Export: " + LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            pw.println("========================================\n");
            for (MetricsResult r : results) {
                pw.println(r.getSummary());
                pw.println("-".repeat(48));
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    public String generateDefaultFileName(String algorithmName, String type) {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String safeName = algorithmName.replaceAll("[^a-zA-Z0-9]", "_");
        return safeName + "_" + type + "_" + timestamp;
    }
}