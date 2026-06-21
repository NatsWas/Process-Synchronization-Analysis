package com.psanalyzer.model.algorithms;
import com.psanalyzer.model.data.*;
import java.util.*;
import java.util.function.Consumer;
public class CigaretteSmokers extends BaseAlgorithm {
    private static final int LOCK = 0;
    private static final int SMOKER_TOBACCO = 1;
    private static final int SMOKER_PAPER = 2;
    private static final int SMOKER_MATCH = 3;
    private static final int AGENT = 4;
    private static final int[] SMOKER_SEM = {
        SMOKER_TOBACCO,
        SMOKER_PAPER,
        SMOKER_MATCH
    };
    @Override
    public String getName() {
        return "Cigarette Smokers";
    }
    @Override
    public String getDescription() {
        return "Agent places 2 of 3 ingredients; smoker with the 3rd ingredient smokes. "
                + "Uses semaphore-based synchronization.";
    }
    private void semWait(int[] semaphores, int slot, boolean isLock) {
        semaphores[slot]--;
        if (isLock && semaphores[slot] < 0) {
            throw new IllegalStateException(
                "SIMULATION BUG: lock semaphore went negative at slot " + slot
                + " — indicates a misplaced acquire in the sequential "
                + "round structure. semaphores[" + slot + "]="
                + semaphores[slot]);
        }
    }
    private void semSignal(int[] semaphores, int slot) {
        semaphores[slot]++;
    }
    @Override
    public MetricsResult simulate(SimConfig config, Consumer<TraceEvent> cb) {
        int rounds = config.getExtraParamInt("rounds", 6);
        String[] smokerNames = {
            "Smoker(has Tobacco)",
            "Smoker(has Paper)",
            "Smoker(has Matches)"
        };
        int[] semaphores = new int[5];
        semaphores[LOCK] = 1;
        semaphores[SMOKER_TOBACCO] = 0;
        semaphores[SMOKER_PAPER] = 0;
        semaphores[SMOKER_MATCH] = 0;
        semaphores[AGENT] = 0;
        List<ProcessModel> processes = new ArrayList<>();
        ProcessModel agent = new ProcessModel(0, 0, rounds);
        agent.setStartTime(0);
        agent.setResponseTime(-1);
        processes.add(agent);
        for (int i = 0; i < 3; i++) {
            ProcessModel p = new ProcessModel(i + 1, 0, rounds);
            p.setStartTime(0);
            p.setResponseTime(-1);
            processes.add(p);
        }
        boolean[] responseRecorded = new boolean[4];
        long time = 0, busyTicks = 0;
        int[] smokeCount = new int[3];
        Random rand = new Random(42);
        emitEvent(cb, time, 0, 0, TraceEvent.EventType.PROCESS_START,
                "Agent starts | will run " + rounds + " rounds");
        for (int i = 0; i < 3; i++) {
            emitEvent(cb, time, i + 1, 0, TraceEvent.EventType.PROCESS_START,
                    smokerNames[i] + " starts");
            emitEvent(cb, time, i + 1, 0, TraceEvent.EventType.WAIT,
                    smokerNames[i] + " goes to sleep waiting for ingredients");
        }
        time++;
        for (int round = 1; round <= rounds; round++) {
            emitEvent(cb, time++, 0, 0, TraceEvent.EventType.CUSTOM,
                    "--- Round " + round + " ---");
            emitEvent(cb, time++, 0, 0, TraceEvent.EventType.ACQUIRE_REQUEST,
                    "Agent: wait(lock)");
            semWait(semaphores, LOCK, true);
            emitEvent(cb, time++, 0, 0, TraceEvent.EventType.ACQUIRE_SUCCESS,
                    "Agent acquired lock");
            if (!responseRecorded[0]) {
                processes.get(0).setResponseTime(
                    (int)(time - processes.get(0).getStartTime()));
                responseRecorded[0] = true;
            }
            String placed1;
            String placed2;
            int selectedSmoker;
            int randNum = rand.nextInt(3) + 1;
            if (randNum == 1) {
                placed1 = "Tobacco";
                placed2 = "Paper";
                selectedSmoker = 2;
                emitEvent(cb, time++, 0, 0, TraceEvent.EventType.RELEASE,
                        "Agent places " + placed1 + " + " + placed2 + " on table");
                emitEvent(cb, time++, 0, 0, TraceEvent.EventType.SIGNAL,
                        "Agent: signal(smoker_match) → waking " + smokerNames[selectedSmoker]);
                semSignal(semaphores, SMOKER_MATCH);
            } else if (randNum == 2) {
                placed1 = "Tobacco";
                placed2 = "Matches";
                selectedSmoker = 1;
                emitEvent(cb, time++, 0, 0, TraceEvent.EventType.RELEASE,
                        "Agent places " + placed1 + " + " + placed2 + " on table");
                emitEvent(cb, time++, 0, 0, TraceEvent.EventType.SIGNAL,
                        "Agent: signal(smoker_paper) → waking " + smokerNames[selectedSmoker]);
                semSignal(semaphores, SMOKER_PAPER);
            } else {
                placed1 = "Matches";
                placed2 = "Paper";
                selectedSmoker = 0;
                emitEvent(cb, time++, 0, 0, TraceEvent.EventType.RELEASE,
                        "Agent places " + placed1 + " + " + placed2 + " on table");
                emitEvent(cb, time++, 0, 0, TraceEvent.EventType.SIGNAL,
                        "Agent: signal(smoker_tobacco) → waking " + smokerNames[selectedSmoker]);
                semSignal(semaphores, SMOKER_TOBACCO);
            }
            emitEvent(cb, time++, 0, 0, TraceEvent.EventType.SIGNAL,
                    "Agent: signal(lock)");
            semSignal(semaphores, LOCK);
            emitEvent(cb, time++, 0, 0, TraceEvent.EventType.WAIT,
                    "Agent goes to sleep waiting for smoker to finish");
            semWait(semaphores, AGENT, false);
            if (semaphores[AGENT] < 0) {
                processes.get(0).addWaitingTime(1);
            }
            int smokerPid = selectedSmoker + 1;
            semWait(semaphores, SMOKER_SEM[selectedSmoker], false);
            emitEvent(cb, time++, smokerPid, 0, TraceEvent.EventType.ACQUIRE_SUCCESS,
                    smokerNames[selectedSmoker] + " wakes up (received signal) "
                    + "| sem[smoker]=" + semaphores[SMOKER_SEM[selectedSmoker]]);
            if (!responseRecorded[smokerPid]) {
                processes.get(smokerPid).setResponseTime(
                    (int)(time - processes.get(smokerPid).getStartTime()));
                responseRecorded[smokerPid] = true;
            }
            emitEvent(cb, time++, smokerPid, 0, TraceEvent.EventType.ACQUIRE_REQUEST,
                    smokerNames[selectedSmoker] + ": wait(lock)");
            semWait(semaphores, LOCK, true);
            emitEvent(cb, time++, smokerPid, 0, TraceEvent.EventType.ACQUIRE_SUCCESS,
                    smokerNames[selectedSmoker] + " acquired lock");
            emitEvent(cb, time++, smokerPid, 0, TraceEvent.EventType.CUSTOM,
                    smokerNames[selectedSmoker] + " picks up " + placed1 + " + " + placed2);
            emitEvent(cb, time++, smokerPid, 0, TraceEvent.EventType.SIGNAL,
                    smokerNames[selectedSmoker] + ": signal(agent) → waking agent");
            semSignal(semaphores, AGENT);
            emitEvent(cb, time++, smokerPid, 0, TraceEvent.EventType.SIGNAL,
                    smokerNames[selectedSmoker] + ": signal(lock)");
            semSignal(semaphores, LOCK);
            busyTicks += 2;
            time += 2;
            smokeCount[selectedSmoker]++;
            emitEvent(cb, time, smokerPid, 0, TraceEvent.EventType.CUSTOM,
                    smokerNames[selectedSmoker] + " smokes! (total=" + smokeCount[selectedSmoker] + ")");
            time++;
            emitEvent(cb, time, smokerPid, 0, TraceEvent.EventType.WAIT,
                    smokerNames[selectedSmoker] + " finishes smoking and goes back to sleep (wait(smoker_X))");
            for (int i = 0; i < 3; i++) {
                if (i != selectedSmoker) {
                    emitEvent(cb, time, i + 1, 0, TraceEvent.EventType.CUSTOM,
                            smokerNames[i] + " remains asleep (no signal received)");
                }
            }
        }
        time++;
        for (int i = 0; i < 4; i++) {
            processes.get(i).setFinishTime(time);
            if (!responseRecorded[i]) {
                processes.get(i).setResponseTime((int) time);
            }
            emitEvent(cb, time, i, 0, TraceEvent.EventType.PROCESS_FINISH,
                    (i == 0 ? "Agent" : smokerNames[i - 1]) + " finished"
                    + (i > 0 ? " (smoked " + smokeCount[i - 1] + " times)" : ""));
        }
        return computeMetrics(getName(), processes, time, busyTicks);
    }
}