# Process Synchronization Analyzer (PS Analyzer v1.0)

## 1. Project Overview
The Process Synchronization Analyzer is an interactive desktop application designed to simulate, visualize, and benchmark twelve classic concurrent synchronization algorithms. Built on the Model-View-Controller (MVC) pattern using Java Swing, the platform integrates a discrete-event simulation engine with a multi-threaded execution framework to evaluate algorithmic correctness, system resource utilization, and starvation profiles in real time.

---

## 2. Architectural Layout
The system enforces a strict separation of concerns, decoupling background mathematical computations from the front-end graphical user interface:
* **com.psanalyzer.model**: Defines properties, execution states, and tracking structures (SynchronizationAlgorithm, TraceEvent, MetricsResult).
* **com.psanalyzer.controller**: Coordinates lifecycle logic, multi-threaded worker timers, data aggregation, and file exports (AlgorithmController, SimulationController, CompareController, ExportController).
* **com.psanalyzer.view**: Manages window layouts, real-time Java 2D graphics buffers, selection components, and metrics grids (MainWindow, ComparativeView, VizCanvas).

---

## 3. Supported Synchronization Protocols

### Software-Based Mutual Exclusion
* **Peterson's & Dekker's Algorithms**: Classic two-process software solutions utilizing shared flags and turn arbitration.
* **Lamport's Fast Mutual Exclusion**: An advanced protocol optimized to achieve constant-time O(1) entry overhead when contention is absent.
* **Filter Lock**: A generalized extension of Peterson’s algorithm designed to support N arbitrary processes.
* **Eisenberg & McGuire**: An N-process mutual exclusion algorithm ensuring a strict upper bound on waiting times to prevent starvation.
* **Black-White Bakery**: An adaptation of Lamport's Bakery algorithm restricting token values to a finite range using a binary coloring flag.
* **Yang-Anderson**: An N-process mutual exclusion algorithm structured around a binary tree of two-process lock elements.

### Classical Inter-Process Communication (IPC)
* **Monitor Producer-Consumer**: A bounded-buffer model employing condition variables within a synchronized monitor framework.
* **Dining Philosophers**: A resource-allocation simulation evaluating deadlock avoidance and starvation prevention across cyclic processes.
* **Readers-Writers**: A resource-access paradigm prioritizing waiting writers to eliminate data corruption while preventing stale updates.
* **Sleeping Barber**: A queuing simulation optimizing customer thread management based on state adjustments and resource availability.
* **Cigarette Smokers**: A coordination problem evaluating conditional synchronization across agents managing fragmented resources.

---

## 4. Evaluation Engine & Mathematical Models
The engine evaluates performance variables across six diagnostic metrics:
* **Average Turnaround Time**: Total normalized duration from an initial entry request to its final critical section release.
* **Average Waiting Time**: Cumulative step duration spent by a process blocking or busy-waiting inside entry protocols.
* **Average Response Time**: Time elapsed between a process arriving in the ready state and its first execution step inside the loop.
* **CPU Utilization**: The ratio of active processing step cycles to total elapsed simulation cycles.
* **Throughput**: The frequency of critical section entries completed per unit of normalized step-time.
* **Jain's Fairness Index**: Evaluates operational equity among competing threads, modeled mathematically as:

f(x_1, x_2, ..., x_n) = (sum(x_i) from i=1 to n)^2 / (n * sum(x_i^2) from i=1 to n)

Where n is the total process count and x_i represents the critical section entry frequency of process i.

---

## 5. Build & Execution Pipeline

### Prerequisites
* **Java Development Kit**: JDK 23 or higher
* **Build Automation**: Apache Maven 4.0.0 or higher
* **Target OS**: Windows 11, macOS, or Linux

### Project Compilation
To clear target directories, resolve dependencies, compile the codebase, and wrap the components into an executable archive, run:
mvn clean compile package