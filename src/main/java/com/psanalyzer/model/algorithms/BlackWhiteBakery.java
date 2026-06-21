package com.psanalyzer.model.algorithms;
import com.psanalyzer.model.data.*;
import java.util.*;
import java.util.function.Consumer;
public class BlackWhiteBakery extends BaseAlgorithm {
    @Override
    public String getName() { return "Black-White Bakery Algorithm"; }
    @Override
    public String getDescription() {
        return "Taubenfeld's extension of Lamport's Bakery. Uses two colors " +
               "(Black/White) alternating each entry to bound ticket numbers. " +
               "Solves unbounded ticket number problem of original Bakery.";
    }
    private enum Phase { DOORWAY, WAITING, CS, EXITING, DONE }
    enum Color { BLACK, WHITE }
    private static final int MAX_TICKS = 200_000;
    @Override
    public MetricsResult simulate(SimConfig config, Consumer<TraceEvent> cb) {
        int n=Math.max(config.getNumberOfProcesses(), 2);
        int maxCS=config.getExtraParamInt("criticalSectionCount", 4);
        boolean[] flag=new boolean[n];   
        int[] number=new int[n];        
        Color[] myColor=new Color[n];
        Color[] globalColor={ Color.WHITE };
        Arrays.fill(flag,   false);
        Arrays.fill(number, 0);
        ProcessModel[] pm= new ProcessModel[n];
        Phase[] phase= new Phase[n];
        int[]  csCount= new int[n];
        int[]  waitJ= new int[n];   
        long[] arrivalTick= new long[n];
        long[] firstCS= new long[n];  
        long[] finishTick = new long[n]; 
        Arrays.fill(firstCS,-1L);
        Arrays.fill(finishTick,-1L);
        for (int i = 0; i < n; i++) {
            pm[i]=new ProcessModel(i, i, maxCS);
            arrivalTick[i]=i;
            phase[i]=Phase.DOORWAY;
        }
        long   time=0;
        long   busyTicks=0;
        for (int i = 0; i < n; i++)
            emitEvent(cb, time, i, 0, TraceEvent.EventType.PROCESS_START,
                    "P" + i + " starts | color=WHITE number=0");
        emitEvent(cb, time, -1, 0, TraceEvent.EventType.CUSTOM,
                "Black-White Bakery | n=" + n + " maxCS=" + maxCS);
        while (time < MAX_TICKS) {
            boolean anyActive = false;
            for (int i = 0; i < n; i++) {
                if (phase[i] == Phase.DONE) continue;
                if (time < arrivalTick[i]) { anyActive = true; continue; }
                anyActive = true;
                switch (phase[i]) {
                    case DOORWAY: {
                        flag[i]    = true;
                        myColor[i] = globalColor[0];
                        int maxNum = 0;
                        for (int j = 0; j < n; j++) {
                            if (j == i) continue;
                            if (myColor[j] == myColor[i] && number[j] > maxNum)
                                maxNum = number[j];
                        }
                        number[i] = maxNum + 1;
                        flag[i]   = false;
                        emitEvent(cb, time, i, 0,
                                TraceEvent.EventType.ACQUIRE_REQUEST,
                                "P" + i + " doorway: color=" + myColor[i] +
                                " ticket=#" + number[i]);
                        waitJ[i] = 0;          
                        phase[i] = Phase.WAITING;
                        break;
                    }
                    case WAITING: {
                        int j = waitJ[i];
                        if (j == i) { waitJ[i]++; break; }
                        if (j >= n) { phase[i] = Phase.CS; break; }
                        if (flag[j]) {
                            emitEvent(cb, time, i, 0,
                                    TraceEvent.EventType.BUSY_WAIT,
                                    "P" + i + " waits for P" + j + " to exit doorway");
                            pm[i].addWaitingTime(1);   
                            break;                      
                        }
                        boolean mustWait = false;
                        if (myColor[j] == myColor[i]) {
                            if (number[j] != 0 &&
                                hasPriority(j, number[j], i, number[i])) {
                                mustWait = true;
                                emitEvent(cb, time, i, 0,
                                        TraceEvent.EventType.BUSY_WAIT,
                                        "P" + i + " waits SAME-COLOR P" + j +
                                        " (ticket=" + number[j] + ")");
                            }
                        } else {
                            if (number[j] != 0
                                    && myColor[i] == globalColor[0]
                                    && myColor[j] != myColor[i]) {
                                mustWait = true;
                                emitEvent(cb, time, i, 0,
                                        TraceEvent.EventType.BUSY_WAIT,
                                        "P" + i + " waits DIFF-COLOR P" + j +
                                        " (ticket=" + number[j] + ")");
                            }
                        }
                        if (mustWait) {
                            pm[i].addWaitingTime(1);   
                        } else {
                            waitJ[i]++;                
                        }
                        break;
                    }
                    case CS: {
                        if (firstCS[i] < 0) {
                            firstCS[i] = time;
                            pm[i].setResponseTime(time - arrivalTick[i]);
                        }
                        emitEvent(cb, time, i, 0,
                                TraceEvent.EventType.ACQUIRE_SUCCESS,
                                "P" + i + " enters CS #" + (csCount[i] + 1) +
                                " | color=" + myColor[i] +
                                " ticket=#" + number[i]);
                        busyTicks += 2;
                        phase[i] = Phase.EXITING;
                        break;
                    }
                    case EXITING: {
                        number[i] = 0;
                        boolean lastOfColor = true;
                        for (int j = 0; j < n; j++) {
                            if (j == i) continue;
                            if (myColor[j] == myColor[i] && number[j] != 0) {
                                lastOfColor = false;
                                break;
                            }
                        }
                        if (lastOfColor) {
                            globalColor[0] = (myColor[i] == Color.BLACK)
                                             ? Color.WHITE : Color.BLACK;
                        }
                        csCount[i]++;
                        emitEvent(cb, time, i, 0,
                                TraceEvent.EventType.RELEASE,
                                "P" + i + " exits CS #" + csCount[i] +
                                " | releases ticket, color=" + myColor[i]);

                        if (csCount[i] >= maxCS) {
                            finishTick[i] = time;
                            pm[i].setFinishTime(time);
                            phase[i] = Phase.DONE;
                            emitEvent(cb, time, i, 0,
                                    TraceEvent.EventType.PROCESS_FINISH,
                                    "P" + i + " finished all " + maxCS + " CS entries");
                        } else {
                            phase[i] = Phase.DOORWAY;   
                        }
                        break;
                    }
                    default: break;
                }
            }
            time++;
            if (!anyActive) break;
        }
        long simTime = time;
        for (int i = 0; i < n; i++) {
            if (pm[i].getFinishTime() <= 0) pm[i].setFinishTime(simTime);
            long rawWait     = pm[i].getWaitingTime();
            long rawFinish   = pm[i].getFinishTime();
            long rawResponse = pm[i].getResponseTime(); 
            long normWait     = simTime > 0 ? (rawWait     * 100L) / simTime : 0;
            long normFinish   = simTime > 0 ? (rawFinish   * 100L) / simTime : 0;
            long normArrival  = simTime > 0 ? (arrivalTick[i] * 100L) / simTime : 0;
            long normResponse = (rawResponse >= 0 && simTime > 0)
                                ? (rawResponse * 100L) / simTime : -1;
            long normTurnaround = normFinish - normArrival;
            pm[i].setWaitingTime(normWait);
            pm[i].setFinishTime(normFinish);
            pm[i].setResponseTime(normResponse);
            pm[i].setTurnaroundTime(normTurnaround);
        }

        return computeMetrics(getName(), Arrays.asList(pm), simTime, busyTicks);
    }
    private boolean hasPriority(int j, int numJ, int i, int numI) {
        if (numJ < numI) return true;
        if (numJ > numI) return false;
        return j < i;
    }
}