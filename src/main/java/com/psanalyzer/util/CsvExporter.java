package com.psanalyzer.util;
import com.psanalyzer.model.data.MetricsResult;
import com.psanalyzer.model.data.TraceEvent;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
public class CsvExporter {
    public static boolean exportMetrics(MetricsResult result, String filePath) {
        try (PrintWriter pw=new PrintWriter(new FileWriter(filePath))) {
            pw.println(result.toCsvHeader());
            pw.println(result.toCsvRow());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    public static boolean exportMultipleMetrics(List<MetricsResult> results,
                                                 String filePath) {
        if (results==null || results.isEmpty()) return false;
        try (PrintWriter pw=new PrintWriter(new FileWriter(filePath))) {
            pw.println(results.get(0).toCsvHeader());
            for (MetricsResult r : results) pw.println(r.toCsvRow());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    public static boolean exportTrace(List<TraceEvent> trace, String filePath) {
        try (PrintWriter pw=new PrintWriter(new FileWriter(filePath))) {
            pw.println("Timestamp,ProcessID,ResourceID,EventType,Description");
            for (TraceEvent e : trace) pw.println(e.toCsv());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    public static boolean exportComparisonMatrix(Map<String, double[]> matrix,
                                                  String[] headers,
                                                  String filePath) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            pw.println(String.join(",", headers));
            for (Map.Entry<String, double[]> entry : matrix.entrySet()) {
                StringBuilder sb = new StringBuilder();
                sb.append(entry.getKey());
                for (double v : entry.getValue())
                    sb.append(String.format(",%.4f", v));
                pw.println(sb);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    public static boolean exportResourceUtilization(MetricsResult result,
                                                      String filePath) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            pw.println("ResourceName,Utilization%");
            List<String> names = result.getResourceNames();
            List<double[]> utils = result.getResourceUtilization();
            for (int i = 0; i < names.size() && i < utils.size(); i++) {
                double util = utils.get(i).length > 0 ? utils.get(i)[0] : 0.0;
                pw.printf("%s,%.2f%n", names.get(i), util);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}