# Java Language Constraints for Spec Agents

**Purpose:** Language-specific pitfalls discovered from prior gap-close-spec runs. Spec agents MUST follow ALL rules listed here to prevent common errors.

**Source:** Retrospective analysis of 98 issues across 6 reviews (Java SDK gap-close-spec run, 2026-03-10).

---

## Syntax Rules

### J-1: No import aliases
Java does NOT support import aliases (`import X as Y`). This is a compile error.
- **Instead:** Use fully-qualified class names inline, or use static imports for specific methods.
- **Example (WRONG):** `import io.kubemq.sdk.error.KubeMQException as KException;`
- **Example (RIGHT):** `import io.kubemq.sdk.error.KubeMQException;` or use `io.kubemq.sdk.error.KubeMQException` inline.

### J-2: Method bodies are required
Every method in a concrete class must have a complete body. Abstract methods are only allowed in abstract classes or interfaces.
- **In specs:** Never write `public void doSomething(); // to be defined later` in a concrete class.
- **Instead:** Provide the full method body, or explicitly declare the class `abstract`.

### J-3: Checked vs unchecked exceptions
Java distinguishes between checked exceptions (extends `Exception`) and unchecked exceptions (extends `RuntimeException`).
- Checked exceptions MUST be declared in `throws` clauses.
- If your exception hierarchy extends `RuntimeException`, do NOT add `throws` clauses — it's misleading.
- Be consistent: decide once whether the KubeMQ exception hierarchy is checked or unchecked, and stick with it across all specs.

### J-4: Generics are invariant
Java generics are invariant by default. `List<Dog>` is NOT a `List<Animal>`.
- Use `? extends T` for producer (read-only) positions.
- Use `? super T` for consumer (write-only) positions.
- Verify all generic type parameters are correctly bounded in interface definitions.

---

## Naming Rules

### J-5: Standard library name collisions
These class names exist in the JDK and MUST NOT be reused for KubeMQ types:

| JDK Class | Package | Risk |
|-----------|---------|------|
| `CancellationException` | `java.util.concurrent` | High — commonly imported in async code |
| `TimeoutException` | `java.util.concurrent` | High — commonly imported |
| `ExecutionException` | `java.util.concurrent` | High — CompletableFuture unwrapping |
| `IOException` | `java.io` | High — everywhere |
| `LoggerFactory` | SLF4J (`org.slf4j`) | High — every class with logging |
| `Logger` | SLF4J / `java.util.logging` | Medium — depends on import |
| `Channel` | `java.nio.channels` | Medium — NIO code |
| `Future` | `java.util.concurrent` | Medium — async code |
| `Timer` | `java.util` | Low |

**Resolution:** Prefix with `KubeMQ` (e.g., `KubeMQTimeoutException`) or use a distinct descriptive name (e.g., `OperationTimedOutException`).

### J-6: Package naming convention
All KubeMQ SDK types must be in `io.kubemq.sdk.*` packages. Use these sub-packages:
- `io.kubemq.sdk.exception` — All exception types (NOT `io.kubemq.sdk.error`)
- `io.kubemq.sdk.client` — Client classes
- `io.kubemq.sdk.config` — Configuration types
- `io.kubemq.sdk.common` — Shared utilities and base types

Pick ONE package convention and use it consistently across all specs. Do NOT mix `error/` and `exception/` sub-packages.

---

## Concurrency Rules

### J-7: ThreadLocalRandom is NOT a static field
`ThreadLocalRandom.current()` must be called at point of use, never stored in a static field.
- **WRONG:** `private static final ThreadLocalRandom random = ThreadLocalRandom.current();`
- **RIGHT:** `ThreadLocalRandom.current().nextInt(100)` at point of use.

### J-8: CAS loops need bounded retry
`AtomicReference.compareAndSet()` loops can cause `StackOverflowError` if implemented as recursive calls, or spin forever if unbounded.
- Always use a `while` loop with a maximum retry count (e.g., 100 iterations).
- Add exponential backoff or `Thread.onSpinWait()` (Java 9+) between retries.
- Log a warning if max retries are exhausted.

### J-9: Single-threaded executor bottleneck
Using `Executors.newSingleThreadExecutor()` for callback execution creates a bottleneck if callbacks are slow.
- For callback executors, prefer `Executors.newCachedThreadPool()` or a bounded pool with queue.
- If ordering matters, use a single-threaded executor but document the ordering guarantee and throughput limitation.

### J-10: Lock and semaphore release in finally
All lock acquisitions and semaphore permits MUST be released in a `finally` block.
```java
lock.lock();
try {
    // critical section
} finally {
    lock.unlock();
}
```

---

## Dependency Rules

### J-11: `provided` scope requires lazy loading
Dependencies with Maven `<scope>provided</scope>` (e.g., OpenTelemetry, SLF4J) may not be present at runtime.
- **NEVER** import `provided`-scope classes directly in eagerly-loaded classes (causes `NoClassDefFoundError`).
- **ALWAYS** use a proxy/factory pattern:
```java
// In eagerly-loaded class:
private static final Tracer tracer = TracerFactory.getTracer(); // factory checks classpath

// TracerFactory (lazy loading):
public class TracerFactory {
    public static Tracer getTracer() {
        try {
            Class.forName("io.opentelemetry.api.trace.Tracer");
            return OpenTelemetryTracerImpl.create(); // only loaded if OTel is present
        } catch (ClassNotFoundException e) {
            return NoOpTracer.INSTANCE;
        }
    }
}
```

### J-12: Maven plugin configuration accuracy
When specifying Maven plugin configurations (JaCoCo, Surefire, Spotless, etc.), verify the plugin version exists and the configuration keys are valid. Common mistakes:
- Wrong `<goal>` names
- Non-existent configuration keys
- Version numbers that don't exist on Maven Central

---

## Build Rules

### J-13: Verify gRPC method existence
Before referencing a gRPC method (e.g., `SendQueueMessagesBatch`), verify it exists in the `.proto` file or generated stubs. The proto definition is the source of truth — do not assume a method exists because it would be convenient.

### J-14: JMH benchmark integration
JMH benchmarks require a specific Maven configuration with `jmh-generator-annprocess` annotation processor. The benchmarks should be in a separate module or use the `maven-shade-plugin` to create an executable benchmark JAR. Do not assume `mvn test` runs JMH benchmarks.

---

## Resource Management Rules

### J-15: ManagedChannel lifecycle
When reconnecting gRPC channels, always shutdown the old `ManagedChannel` before creating a new one. Failing to do so leaks gRPC threads and connections.

### J-16: Stub methods must be honest
Stub/placeholder methods must log at WARN level describing what they do NOT do. Never silently drop operations.

---

## ClassLoader Rules

### J-17: Context classloader for optional dependencies
When detecting optional dependencies (e.g., OTel), use `Thread.currentThread().getContextClassLoader()` with a fallback to `getClass().getClassLoader()`. In application-server environments, the SDK classloader may not see jars visible to the context classloader.

---

## API Consistency Rules

### J-18: Deprecated alias @SuppressWarnings
When implementing `@Deprecated` method aliases, add `@SuppressWarnings("deprecation")` to any internal callers of the deprecated methods to keep the build warning-free.
