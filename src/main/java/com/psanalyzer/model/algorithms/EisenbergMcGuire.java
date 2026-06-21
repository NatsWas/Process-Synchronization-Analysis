package com.psanalyzer.model.algorithms;
import com.psanalyzer.model.data.*;
import java.util.*;
import java.util.function.Consumer;
public class EisenbergMcGuire extends BaseAlgorithm {
    @Override public String getName() { return "Eisenberg & McGuire"; }
    @Override public String getDescription() {
        return "N-process mutual exclusion using intent array and a turn variable.";
    }
    private enum Flag { IDLE, WAITING, ACTIVE }
    private static final int MAX_SPIN = 500;
    @Override
    public MetricsResult simulate(SimConfig config, Consumer<TraceEvent> cb) {
        int n = config.getNumberOfProcesses();
        if (n < 2) n = 2;
        int maxCS = config.getExtraParamInt("criticalSectionCount", 3);
        List<ProcessModel> processes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ProcessModel p = new ProcessModel(i, 0, maxCS);
            p.setStartTime(0);
            p.setResponseTime(-1);
            processes.add(p);
        }
        Flag[] flag = new Flag[n];
        Arrays.fill(flag, Flag.IDLE);
        int turn=0;
        int[] csCount= new int[n];

        long[] rawWaiting=new long[n];
        long[] rawResponse=new long[n];
        long[] rawFinish=new long[n];
        Arrays.fill(rawResponse, -1L);
        int[] thinkTime = new int[n];
        for (int i = 0; i < n; i++) {
            thinkTime[i] = i * 2;
        }
        long time = 0, csTicks = 0;
        for (int i = 0; i < n; i++)
            emitEvent(cb, time, i, 0, TraceEvent.EventType.PROCESS_START,
                      "P" + i + " starts | flag=IDLE");
        boolean anyLeft = true;
        while (anyLeft) {
            anyLeft = false;
            for (int i = 0; i < n; i++) {
                if (csCount[i] >= maxCS) continue;
                anyLeft = true;
                if (thinkTime[i] > 0) {
                    rawWaiting[i] += thinkTime[i];
                    emitEvent(cb, time++, i, 0, TraceEvent.EventType.BUSY_WAIT,
                              "P" + i + " thinking (" + thinkTime[i] + " ticks before request)");
                    time += thinkTime[i] - 1; 
                }
                flag[i] = Flag.WAITING;
                emitEvent(cb, time++, i, 0, TraceEvent.EventType.ACQUIRE_REQUEST,
                          "P" + i + " flag=WAITING");
                boolean cleanScan = false;
                int spinCount = 0;
                while (!cleanScan) {
                    if (spinCount++ > MAX_SPIN) {
                        flag[i] = Flag.IDLE;
                        rawWaiting[i]++;
                        emitEvent(cb, time++, i, 0, TraceEvent.EventType.BUSY_WAIT,
                                  "P" + i + " spin-limit hit, backing off");
                        anyLeft = true;
                        break;
                    }
                    cleanScan = true;
                    int j = turn;
                    while (j != i) {
                        if (flag[j] != Flag.IDLE) {
                            cleanScan = false;
                            rawWaiting[i]++;
                            emitEvent(cb, time++, i, 0, TraceEvent.EventType.BUSY_WAIT,
                                      "P" + i + " waiting on P" + j + " (flag=" + flag[j] + ")");
                            j = turn;
                            break;
                        } else {
                            j = (j + 1) % n;
                        }
                    }
                }
                if (flag[i] == Flag.IDLE) continue;
                flag[i] = Flag.ACTIVE;
                emitEvent(cb, time++, i, 0, TraceEvent.EventType.ACQUIRE_REQUEST,
                          "P" + i + " flag=ACTIVE");
                int conflictWith = -1;
                for (int k = 0; k < n; k++) {
                    if (k != i && flag[k] == Flag.ACTIVE) { conflictWith = k; break; }
                }
                boolean turnOk = (turn == i) || (flag[turn] == Flag.IDLE);
                if (!(conflictWith == -1 && turnOk)) {
                    flag[i] = Flag.WAITING;
                    rawWaiting[i]++;
                    emitEvent(cb, time++, i, 0, TraceEvent.EventType.BUSY_WAIT,
                              conflictWith != -1
                              ? "P" + i + " conflict with P" + conflictWith + ", backs off"
                              : "P" + i + " turn not ok (turn=P" + turn + "), backs off");
                    continue;
                }
                turn = i;
                if (rawResponse[i] == -1L) rawResponse[i] = time;
                emitEvent(cb, time++, i, 0, TraceEvent.EventType.ACQUIRE_SUCCESS,
                          "P" + i + " ENTERS CS #" + (csCount[i] + 1) + " | turn=" + turn);
                csTicks++;
                time++;
                int next=(i + 1) % n;
                while (flag[next] == Flag.IDLE && next != i)
                    next=(next + 1) % n;
                turn=next;
                flag[i]=Flag.IDLE;
                csCount[i]++;
                if (csCount[i] == maxCS) rawFinish[i] = time;
                emitEvent(cb, time++, i, 0, TraceEvent.EventType.RELEASE,
                          "P" + i + " EXITS CS | flag=IDLE | next turn=P" + turn);
            }
        }
        final long totalRawTime = Math.max(time + 1, 1L);
        for (int i = 0; i < n; i++) {
            if (rawFinish[i]   == 0L)  rawFinish[i]   = totalRawTime;
            if (rawResponse[i] == -1L) rawResponse[i] = rawFinish[i];
        }
        for (int i = 0; i < n; i++)
            emitEvent(cb, totalRawTime, i, 0, TraceEvent.EventType.PROCESS_FINISH,
                      "P" + i + " finished all " + maxCS + " CS completions");
        final double scale = 100.0 / totalRawTime;
        for (int i = 0; i < n; i++) {
            ProcessModel p = processes.get(i);
            long arrival   = p.getArrivalTime();
            long normTurnaround=clamp100(Math.round((rawFinish[i] - arrival) * scale));
            long normFinish=clamp100(Math.round(rawFinish[i]*scale));
            long normResponse=clamp100(Math.round(rawResponse[i]*scale));
            p.setTurnaroundTime(normTurnaround);
            p.setFinishTime(normFinish);
            p.setWaitingTime(rawWaiting[i]);   
            p.setResponseTime(normResponse);
        }
        MetricsResult result = computeMetrics(getName(), processes, totalRawTime, csTicks);
        result.setThroughput(
            round4((double) result.getCompletedProcesses() / totalRawTime));
        return result;
    }
    private static long clamp100(long v) { return Math.max(0L, Math.min(100L, v)); }
    private static double round4(double v) { return Math.round(v * 10_000.0) / 10_000.0; }
}