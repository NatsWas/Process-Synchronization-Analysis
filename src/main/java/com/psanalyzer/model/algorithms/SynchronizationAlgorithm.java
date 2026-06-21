package com.psanalyzer.model.algorithms;
import com.psanalyzer.model.data.MetricsResult;
import com.psanalyzer.model.data.SimConfig;
import com.psanalyzer.model.data.TraceEvent;
import java.util.function.Consumer;
public interface SynchronizationAlgorithm {
    MetricsResult simulate(SimConfig config, Consumer<TraceEvent> eventCallback);
    String getName();
    String getDescription();
}