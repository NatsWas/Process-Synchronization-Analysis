package com.psanalyzer.model.algorithms;
import com.psanalyzer.model.data.*;
import java.util.*;
import java.util.function.Consumer;
public class DiningPhilosophers extends BaseAlgorithm {
    private static final int THINKING = 0;
    private static final int HUNGRY = 1;
    private static final int EATING = 2;
    private static class PhilosopherState {
        int id;
        int eaten = 0;
        int state = THINKING;
        PhilosopherState(int id) {
            this.id = id;
        }
    }
    @Override
    public String getName() { return "Dining Philosophers"; }
    @Override
    public String getDescription() {
        return "Tanenbaum's solution: maximum concurrency via neighbor state-testing.";
    }
    private void test(int i, int n, PhilosopherState[] p, Consumer<TraceEvent> cb, long time) {
        int left = (i + n - 1) % n; 
        int right = (i + 1) % n;
        if (p[i].state == HUNGRY && p[left].state != EATING && p[right].state != EATING) {
            p[i].state = EATING;
            emitEvent(cb, time, i, 0, TraceEvent.EventType.ACQUIRE_SUCCESS,
                    "test(" + i + ") passed: neighbors " + left + " and " + right + " are not eating -> EATING");
        }
    }
    @Override
    public MetricsResult simulate(SimConfig config, Consumer<TraceEvent> cb) {
        int n = config.getNumberOfProcesses();
        if (n < 2) n = 5;
        int meals = 3;
        try {
            meals = config.getExtraParamInt("mealsPerPhilosopher", 3);
        } catch (Exception e) { /* use default */ }
        List<ProcessModel> processes = new ArrayList<>();
        PhilosopherState[] philosophers = new PhilosopherState[n];
        for (int i = 0; i < n; i++) {
            ProcessModel proc = new ProcessModel(i, 0, meals);
            processes.add(proc);
            philosophers[i] = new PhilosopherState(i);
        }
        long time = 0;
        long busyTicks = 0;
        emitEvent(cb, time, -1, 0, TraceEvent.EventType.CUSTOM,
                "Dining Philosophers (Tanenbaum) started | n=" + n + " meals=" + meals);
        Random rand = new Random(42);
        int totalMealsEaten = 0;
        int targetMeals = n * meals;
        long maxTime = (long) n * meals * 1000;
        while (totalMealsEaten < targetMeals) {
            if (time > maxTime) {
                emitEvent(cb, time, -1, 0, TraceEvent.EventType.CUSTOM,
                        "DEADLOCK DETECTED — simulation aborted at time=" + time);
                break;
            } 
            int i = rand.nextInt(n);
            if (philosophers[i].eaten >= meals) continue;
            switch (philosophers[i].state) {
                case THINKING: {
                    int thinkTime = 1 + rand.nextInt(3);
                    for (int t = 0; t < thinkTime; t++) {
                        emitEvent(cb, time, i, 0, TraceEvent.EventType.BUSY_WAIT,
                                "Philosopher " + i + " is thinking");
                        time++;
                    }
                    processes.get(i).setStartTime((int) time);
                    philosophers[i].state = HUNGRY;
                    emitEvent(cb, time, i, 0, TraceEvent.EventType.ACQUIRE_REQUEST,
                            "Philosopher " + i + " is HUNGRY, invoking test(" + i + ")");
                    time++;
                    test(i, n, philosophers, cb, time);
                    if (philosophers[i].state != EATING) {
                        emitEvent(cb, time, i, 0, TraceEvent.EventType.ACQUIRE_FAIL,
                                "Philosopher " + i + " test() failed -> blocking on semaphore");
                    }
                    break;
                }
                case HUNGRY: {
                    emitEvent(cb, time, i, 0, TraceEvent.EventType.BUSY_WAIT,
                            "Philosopher " + i + " is waiting (blocked)");
                    processes.get(i).addWaitingTime(1);
                    time++;
                    break;
                }
                case EATING: {
                    if (philosophers[i].eaten == 0) {
                        processes.get(i).setResponseTime(
                                (int)(time - processes.get(i).getStartTime()));
                    }
                    for (int t = 0; t < 3; t++) {
                        emitEvent(cb, time, i, 0, TraceEvent.EventType.BUSY_WAIT,
                                "Philosopher " + i + " is eating (meal #" 
                                + (philosophers[i].eaten + 1) + ")");
                        time++;
                        busyTicks++;
                    }
                    philosophers[i].eaten++;
                    totalMealsEaten++;
                    philosophers[i].state = THINKING;
                    emitEvent(cb, time, i, 0, TraceEvent.EventType.RELEASE,
                            "Philosopher " + i + " put down forks -> THINKING");
                    time++;
                    if (philosophers[i].eaten >= meals) {
                        processes.get(i).setFinishTime((int) time);
                        emitEvent(cb, time, i, 0, TraceEvent.EventType.PROCESS_FINISH,
                                "Philosopher " + i + " finished all " + meals + " meals");
                    }
                    int left = (i + n - 1) % n;
                    int right = (i + 1) % n;
                    emitEvent(cb, time, i, 0, TraceEvent.EventType.CUSTOM,
                            "Philosopher " + i + " invoking test() on left neighbor " + left);
                    test(left, n, philosophers, cb, time);
                    emitEvent(cb, time, i, 0, TraceEvent.EventType.CUSTOM,
                            "Philosopher " + i + " invoking test() on right neighbor " + right);
                    test(right, n, philosophers, cb, time);
                    break;
                }
            }
        }
        return computeMetrics(getName(), processes, time, busyTicks);
    }
}