package com.psanalyzer.model.algorithms;
import com.psanalyzer.model.data.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
public class SleepingBarber extends BaseAlgorithm {
@Override
public String getName() { return "Sleeping Barber"; }
@Override
public String getDescription() {
return "Barber sleeps when no customers; customers wait in limited chairs. "
+ "Uses Customers semaphore (barber waits), "
+ "Barber semaphore (customer waits), "
+ "and Seats mutex to protect freeSeats counter.";
}
@Override
public MetricsResult simulate(SimConfig config,
Consumer<TraceEvent> cb) {
int chairs = config.getExtraParamInt("waitingChairs", 3);
int numCustomers = Math.max(config.getNumberOfProcesses(), 1);
final int N = chairs;
Semaphore customers = new Semaphore(0, true);
Semaphore barber = new Semaphore(0, true);
Semaphore seats = new Semaphore(1, true);
final int[] freeSeats = { N };
AtomicLong time = new AtomicLong(0);
AtomicLong busyTicks = new AtomicLong(0);
final int[] totalSignalled = { 0 };
final long HAIRCUT_MS = 3L;
final long ARRIVAL_GAP = Math.max(1L, HAIRCUT_MS / N);
List<ProcessModel> processes = new ArrayList<>();
for (int i = 0; i < numCustomers; i++) {
long arrivalLong = i * ARRIVAL_GAP;
int arrivalInt = (int) Math.min(arrivalLong, Integer.MAX_VALUE);
ProcessModel p = new ProcessModel(i, arrivalInt, 1);
p.setResponseTime(-1); 
p.setStartTime(arrivalInt);
processes.add(p);
}

List<Thread> threads = new ArrayList<>();
List<Throwable> errors = new CopyOnWriteArrayList<>();

emitEvent(cb, time.get(), -1, 0,
TraceEvent.EventType.CUSTOM,
"Sleeping Barber started | chairs(N)=" + N
+ " customers=" + numCustomers
+ " arrivalGap=" + ARRIVAL_GAP + "ms");
Thread barberThread = new Thread(() -> {
int served = 0;

while (!Thread.currentThread().isInterrupted()) {
try {
// B1: down(&customers) — barber sleeps here when shop is empty
emitEvent(cb, time.incrementAndGet(), -1, 0,
TraceEvent.EventType.WAIT,
"Barber: down(Customers) -> sleeping, waiting for customer"
+ " [served=" + served + "]");
customers.acquire();

} catch (InterruptedException e) {
Thread.currentThread().interrupt();
emitEvent(cb, time.incrementAndGet(), -1, 0,
TraceEvent.EventType.CUSTOM,
"Barber: interrupted -> shop closed, served=" + served);
break;
}

try {
// B2: down(&mutex) — enter critical region
seats.acquire();
emitEvent(cb, time.incrementAndGet(), -1, 0,
TraceEvent.EventType.ACQUIRE_REQUEST,
"Barber: down(Seats) acquired -> entering critical region");

// B3: waiting-- (freeSeats++ in inverted-variable form)
freeSeats[0]++;
int fs = freeSeats[0];
emitEvent(cb, time.incrementAndGet(), -1, 0,
TraceEvent.EventType.ACQUIRE_REQUEST,
"Barber: FreeSeats++=" + fs
+ " -> customer moving to barber chair");

// B4: up(&barbers) — signal customer that barber is ready
barber.release();
emitEvent(cb, time.incrementAndGet(), -1, 0,
TraceEvent.EventType.SIGNAL,
"Barber: up(Barber) -> signalling customer to sit");

// B5: up(&mutex) — release critical region
seats.release();
emitEvent(cb, time.incrementAndGet(), -1, 0,
TraceEvent.EventType.RELEASE,
"Barber: up(Seats) -> seats unlocked");
// --- exit critical region ---

// B6: cut_hair() — OUTSIDE critical region per Tanenbaum
emitEvent(cb, time.incrementAndGet(), -1, 0,
TraceEvent.EventType.ACQUIRE_SUCCESS,
"Barber: cutting hair | FreeSeats=" + freeSeats[0]);

Thread.sleep(HAIRCUT_MS);
time.addAndGet(HAIRCUT_MS);
busyTicks.addAndGet(HAIRCUT_MS);

served++;
emitEvent(cb, time.incrementAndGet(), -1, 0,
TraceEvent.EventType.CUSTOM,
"Barber: haircut done [served=" + served + "]");

} catch (InterruptedException e) {
Thread.currentThread().interrupt();
}
}
});
threads.add(barberThread); // index 0 = barber
for (int idx = 0; idx < numCustomers; idx++) {
final int i = idx;

Thread t = new Thread(() -> {
try {
long arrivalMs = processes.get(i).getArrivalTime(); // already ms
Thread.sleep(arrivalMs);

emitEvent(cb, time.incrementAndGet(), i, 0,
TraceEvent.EventType.PROCESS_ARRIVE,
"Customer C" + i + " arrives");

// C1: down(&mutex) — enter critical region
seats.acquire();
emitEvent(cb, time.incrementAndGet(), i, 0,
TraceEvent.EventType.ACQUIRE_REQUEST,
"Customer C" + i + ": down(Seats) acquired -> checking chairs | "
+ "FreeSeats=" + freeSeats[0]);

// C2: if (waiting < CHAIRS) → if (freeSeats[0] > 0)
if (freeSeats[0] > 0) {

// C3: waiting++ (freeSeats-- in inverted-variable form)
freeSeats[0]--;
int fs = freeSeats[0];
emitEvent(cb, time.incrementAndGet(), i, 0,
TraceEvent.EventType.WAIT,
"Customer C" + i + ": FreeSeats--=" + fs
+ " -> sits in waiting room ("
+ (N - fs) + "/" + N + " seats taken)");

// totalSignalled updated inside the critical section,
// consistent with freeSeats accounting.
totalSignalled[0]++;

// C4: up(&customers) — wake barber
customers.release();
emitEvent(cb, time.incrementAndGet(), i, 0,
TraceEvent.EventType.SIGNAL,
"Customer C" + i + ": up(Customers) -> notifying barber");

// C5: up(&mutex) — release critical region
seats.release();
emitEvent(cb, time.incrementAndGet(), i, 0,
TraceEvent.EventType.RELEASE,
"Customer C" + i + ": up(Seats) -> seats unlocked");
long waitStartNs = System.nanoTime();
emitEvent(cb, time.incrementAndGet(), i, 0,
TraceEvent.EventType.BUSY_WAIT,
"Customer C" + i + ": down(Barber) -> waiting for barber");
barber.acquire();
long waitElapsedMs = (System.nanoTime() - waitStartNs) / 1_000_000L;
processes.get(i).addWaitingTime(waitElapsedMs);
long arrivalTime = processes.get(i).getArrivalTime();
long responseTimeLong = time.get() - arrivalTime;
processes.get(i).setResponseTime(
(int) Math.min(responseTimeLong, Integer.MAX_VALUE));
emitEvent(cb, time.incrementAndGet(), i, 0,
TraceEvent.EventType.ACQUIRE_SUCCESS,
"Customer C" + i + ": getting haircut");
Thread.sleep(HAIRCUT_MS); 
processes.get(i).setFinishTime(
(int) Math.min(time.get(), Integer.MAX_VALUE));
emitEvent(cb, time.incrementAndGet(), i, 0,
TraceEvent.EventType.PROCESS_FINISH,
"Customer C" + i + ": haircut done, leaving");
} else {
seats.release();
emitEvent(cb, time.incrementAndGet(), i, 0,
TraceEvent.EventType.ACQUIRE_FAIL,
"Customer C" + i
+ ": up(Seats) -> no free seats (FreeSeats=0), leaving");

processes.get(i).setFinishTime(
(int) Math.min(time.get(), Integer.MAX_VALUE));
emitEvent(cb, time.incrementAndGet(), i, 0,
TraceEvent.EventType.PROCESS_FINISH,
"Customer C" + i + ": left without haircut");
}

} catch (InterruptedException e) {
Thread.currentThread().interrupt();
errors.add(e);
}
});
threads.add(t);
}
barberThread.start();
for (int i = 1; i < threads.size(); i++) {
threads.get(i).start();
}

// Wait for every customer thread to complete (indices 1..n).
for (int i = 1; i < threads.size(); i++) {
try {
threads.get(i).join(5000);
if (threads.get(i).isAlive()) {
threads.get(i).interrupt();
errors.add(new RuntimeException(
"Customer thread C" + (i - 1)
+ " timed out and was interrupted"));
}
} catch (InterruptedException e) {
Thread.currentThread().interrupt();
threads.get(i).interrupt();
}
}
if (barberThread.isAlive()) {
barberThread.interrupt();
}
try {
barberThread.join(2000);
if (barberThread.isAlive()) {
errors.add(new RuntimeException(
"Barber thread failed to terminate within 2s after interrupt"));
}
} catch (InterruptedException e) {
Thread.currentThread().interrupt();
}

if (!errors.isEmpty()) {
errors.forEach(e ->
System.err.println("[SleepingBarber ERROR] " + e.getMessage()));
}

long finalTime = time.get();
emitEvent(cb, finalTime, -1, 0,
TraceEvent.EventType.PROCESS_FINISH,
"All customers processed | served=" + totalSignalled[0]
+ " turned-away=" + (numCustomers - totalSignalled[0]));

return computeMetrics(getName(), processes, finalTime, busyTicks.get());
}
private boolean allCustomersDone(List<Thread> threads) {
for (int i = 1; i < threads.size(); i++) {
if (threads.get(i).isAlive()) return false;
}
return true;
}
}