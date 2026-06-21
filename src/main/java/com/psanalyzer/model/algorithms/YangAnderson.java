package com.psanalyzer.model.algorithms;
import com.psanalyzer.model.data.*;
import java.util.*;
import java.util.function.Consumer;
public class YangAnderson extends BaseAlgorithm {
    private static final int EMPTY = -1;
    private static final int P_INITIAL        = 0; // not yet signaled
    private static final int P_TIEBREAKER_SET = 1; // rival wrote tie-breaker
    private static final int P_CS_RELEASED    = 2; // rival exited CS
    private static final int SPIN_CAP = Integer.MAX_VALUE / 2;
    private static final long ENTRY_MIN_TICKS = 3L;

    @Override
    public String getName() {
        return "Yang-Anderson Algorithm";
    }

    @Override
    public String getDescription() {
        return "Tournament-tree mutual exclusion by Yang & Anderson (1995). "
             + "Processes compete pairwise up a binary tree using C[]/T[]/P[] "
             + "shared variables. O(log N) remote references, starvation-free, "
             + "requires only atomic read/write operations.";
    }
    @Override
    public MetricsResult simulate(SimConfig config, Consumer<TraceEvent> cb) {
        //Configuration
        int n     = Math.max(config.getNumberOfProcesses(), 2);
        int maxCS = config.getExtraParamInt("criticalSectionCount", 3);
        // Round n up to the next power of 2 so the binary tree is complete.
        int treeSize = nextPowerOfTwo(n);
        // 2. Process models
        List<ProcessModel> processes = buildProcessModels(n, maxCS);

        // Shared tree state 
        int slots = treeSize + 1;

        int[][] C = new int[2][slots];
        for (int[] row : C) Arrays.fill(row, EMPTY);

        int[] T = new int[slots];
        Arrays.fill(T, EMPTY);

        // P[i]: per-process local spin variable — Java default is 0 = P_INITIAL.
        int[] P = new int[n];

        //Simulation bookkeeping
        int[]   csCount        = new int[n];
        boolean[] firstCsDone  = new boolean[n];   
        long time              = 0;
        long busyTicks         = 0;                
        emitEvent(cb, time, -1, 0, TraceEvent.EventType.CUSTOM,
                  "Yang-Anderson | n=" + n
                  + " treeSize=" + treeSize
                  + " internalNodes=" + (treeSize - 1));

        for (int i = 0; i < n; i++) {
            emitEvent(cb, time, i, 0, TraceEvent.EventType.PROCESS_START,
                      "P" + i + " → leaf " + (treeSize + i));
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) order.add(i);

        boolean anyLeft = true;
        while (anyLeft) {
            anyLeft = false;
            Collections.shuffle(order);

            for (int i : order) {
                if (csCount[i] >= maxCS) continue;
                anyLeft = true;

                List<Integer> path = pathToRoot(i, treeSize);

                emitEvent(cb, time++, i, 0, TraceEvent.EventType.ACQUIRE_REQUEST,
                          "P" + i + " starts climbing | "
                          + formatPath(path, i, treeSize));
                for (int node : path) {
                    long timeBefore = time;
                    long[] result   = yaEntry(i, node, treeSize, C, T, P,
                                             processes, cb, time);
                    time = result[0];
                    long climbTicks = time - timeBefore;
                    long waitTicks  = Math.max(0L, climbTicks - ENTRY_MIN_TICKS);
                    if (waitTicks > 0) {
                        processes.get(i).addWaitingTime(waitTicks);
                    }
                    else {
                        processes.get(i).addWaitingTime(1);
                    }
                }

                // CRITICAL SECTION
                if (!firstCsDone[i]) {
                    long responseTime = time - processes.get(i).getArrivalTime();
                    processes.get(i).setResponseTime(responseTime);
                    firstCsDone[i] = true;
                }

                emitEvent(cb, time++, i, 1, TraceEvent.EventType.ACQUIRE_SUCCESS,
                          "P" + i + " → CS #" + (csCount[i] + 1)
                          + " (levels=" + path.size() + ")");
                time++;        // CS execution tick
                busyTicks++;   // one tick of actual CPU work in the CS

                //EXIT: descend root → leaf 
                List<Integer> releasePath = reversed(path);
                for (int node : releasePath) {
                    time = yaExit(i, node, treeSize, C, T, P, cb, time);
                }

                csCount[i]++;
                emitEvent(cb, time++, i, 0, TraceEvent.EventType.RELEASE,
                          "P" + i + " exits CS #" + csCount[i]);
                if (csCount[i] >= maxCS) {
                    processes.get(i).setFinishTime(time);
                    processes.get(i).setState(ProcessModel.State.TERMINATED);

                    // Set turnaround explicitly so getTurnaroundTime() uses
                    // the per-process finish time, not a shared final tick.
                    long tat = processes.get(i).getFinishTime()
                             - processes.get(i).getArrivalTime();
                    processes.get(i).setTurnaroundTime(tat);

                    emitEvent(cb, time, i, 0, TraceEvent.EventType.PROCESS_FINISH,
                              "P" + i + " finished all " + maxCS + " CS");
                }
            }
        }
        time++;
        for (int i = 0; i < n; i++) {
            if (processes.get(i).getFinishTime() <= 0) {
                processes.get(i).setFinishTime(time);
                long tat = time - processes.get(i).getArrivalTime();
                processes.get(i).setTurnaroundTime(tat);
                emitEvent(cb, time, i, 0, TraceEvent.EventType.PROCESS_FINISH,
                          "P" + i + " finished (fallback)");
            }
        }

        return computeMetrics(getName(), processes, time, busyTicks);
    }
    private long[] yaEntry(
            int processId,
            int node,
            int treeSize,
            int[][] C,
            int[] T,
            int[] P,
            List<ProcessModel> processes,
            Consumer<TraceEvent> cb,
            long time) {

        int side  = sideOf(processId, node, treeSize); // 0=left, 1=right
        int other = 1 - side;
        long busyW = 0;

        emitEvent(cb, time++, processId, node,
                  TraceEvent.EventType.ACQUIRE_REQUEST,
                  "P" + processId + " at node " + node
                  + " side=" + side + " | stmt 4-6");

        // Statement 4: C[side(i)] := i
        C[side][node] = processId;

        // Statement 5: T := i
        T[node] = processId;

        // Statement 6: P[i] := 0
        P[processId] = P_INITIAL;

        // Statement 7: rival := C[1 − side(i)]
        int rival = C[other][node];

        emitEvent(cb, time++, processId, node,
                  TraceEvent.EventType.CUSTOM,
                  "P" + processId + " node=" + node
                  + " rival=" + (rival == EMPTY ? "none" : "P" + rival)
                  + " T=" + T[node]);
        if (rival != EMPTY) {
            if (T[node] == processId) {
                if (P[rival] == P_INITIAL) {
                    P[rival] = P_TIEBREAKER_SET;
                    emitEvent(cb, time++, processId, node,
                              TraceEvent.EventType.CUSTOM,
                              "P" + processId + " signals P" + rival
                              + " P[" + rival + "]=1 (stmt 11)");
                }
                int w = 0;
                while (P[processId] == P_INITIAL && w < SPIN_CAP) {
                    emitEvent(cb, time++, processId, node,
                              TraceEvent.EventType.BUSY_WAIT,
                              "P" + processId + " spin-12: P[" + processId
                              + "]=" + P[processId]
                              + " waiting for rival P" + rival
                              + " T=" + T[node]);
                    processes.get(processId).addWaitingTime(1);
                    w++;
                    busyW++;
                }
                emitEvent(cb, time++, processId, node,
                          TraceEvent.EventType.CUSTOM,
                          "P" + processId + " unblocked stmt-12"
                          + " P[" + processId + "]=" + P[processId]);
                if (T[node] == processId) {
                    int w2 = 0;
                    while (P[processId] <= P_TIEBREAKER_SET && w2 < SPIN_CAP) {
                        emitEvent(cb, time++, processId, node,
                                  TraceEvent.EventType.BUSY_WAIT,
                                  "P" + processId + " spin-14: P[" + processId
                                  + "]=" + P[processId]
                                  + " waiting rival P" + rival + " to exit CS");
                        processes.get(processId).addWaitingTime(1);
                        w2++;
                        busyW++;
                    }

                    emitEvent(cb, time++, processId, node,
                              TraceEvent.EventType.CUSTOM,
                              "P" + processId + " unblocked stmt-14"
                              + " P[" + processId + "]=" + P[processId]);
                }
            }
        }

        emitEvent(cb, time++, processId, node,
                  TraceEvent.EventType.ACQUIRE_SUCCESS,
                  "P" + processId + " wins node " + node + " → up");

        return new long[]{ time, busyW };
    }
    private long yaExit(
            int processId,
            int node,
            int treeSize,
            int[][] C,
            int[] T,
            int[] P,
            Consumer<TraceEvent> cb,
            long time) {
        int side = sideOf(processId, node, treeSize);
        C[side][node] = EMPTY;
        int rival = T[node];
        emitEvent(cb, time++, processId, node,
                  TraceEvent.EventType.RELEASE,
                  "P" + processId + " exits node " + node
                  + " | C[" + side + "][" + node + "]=EMPTY"
                  + " rival=" + (rival == EMPTY ? "none"
                                : rival == processId ? "self"
                                : "P" + rival));
        if (rival != processId) {
            P[rival] = P_CS_RELEASED;
            emitEvent(cb, time++, processId, node,
                      TraceEvent.EventType.CUSTOM,
                      "P" + processId + " signals P" + rival
                      + " P[" + rival + "]=2 (stmt 19) → unblocks spin-14");
        }

        return time;
    }
    private List<Integer> pathToRoot(int processId, int treeSize) {
        List<Integer> path = new ArrayList<>();
        int node = (treeSize + processId) / 2; // parent of leaf
        while (node >= 1) {
            path.add(node);
            if (node == 1) break;
            node /= 2;
        }
        if (path.isEmpty()) path.add(1);
        return path;
    }
    private int sideOf(int processId, int node, int treeSize) {
        int leaf = treeSize + processId;
        if (isInSubtree(leaf, node * 2))     return 0; // left subtree
        if (isInSubtree(leaf, node * 2 + 1)) return 1; // right subtree
        return 0; // unreachable for valid inputs
    }
    private boolean isInSubtree(int leaf, int subtreeRoot) {
        int node = leaf;
        while (node > 0) {
            if (node == subtreeRoot) return true;
            if (node < subtreeRoot)  break;
            node /= 2;
        }
        return false;
    }
    private static int nextPowerOfTwo(int value) {
        int p = 1;
        while (p < value) p *= 2;
        return p;
    }
    private static List<ProcessModel> buildProcessModels(int n, int maxCS) {
                       List<ProcessModel> list = new ArrayList<>(n);
                       Random rand = new Random();
                       for (int i = 0; i < n; i++) {
                                 int staggeredArrival = rand.nextInt(n * 2); 
                                 ProcessModel p = new ProcessModel(i, staggeredArrival, maxCS);
                                 p.setStartTime(staggeredArrival);
                                 list.add(p);
                       }
                       return list;
           }
    private static <T> List<T> reversed(List<T> list) {
        List<T> copy = new ArrayList<>(list);
        Collections.reverse(copy);
        return copy;
    }

    /** Formats the climb path as a readable string for trace events. */
    private static String formatPath(List<Integer> path, int pid, int treeSize) {
        StringBuilder sb = new StringBuilder();
        sb.append("leaf").append(treeSize + pid);
        for (int node : path) sb.append("→").append(node);
        sb.append("(root)");
        return sb.toString();
    }
}