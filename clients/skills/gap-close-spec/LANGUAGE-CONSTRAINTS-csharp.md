# C# / .NET Language Constraints for Spec Agents

**Purpose:** Language-specific pitfalls that spec agents MUST follow to prevent common errors in C# SDK specs.

---

## Syntax Rules

### CS-1: async/await must propagate correctly
Every async method MUST return `Task` or `Task<T>`, never `void` (except event handlers).
- **WRONG:** `public async void SendAsync(...)` — fire-and-forget, exceptions are lost
- **RIGHT:** `public async Task SendAsync(...)` or `public async Task<Result> SendAsync(...)`
- All async methods should accept `CancellationToken` as the last parameter.

### CS-2: IDisposable and IAsyncDisposable
Any class that holds unmanaged resources (gRPC channels, timers, semaphores) MUST implement `IDisposable` and preferably `IAsyncDisposable`.
- Always include `using`/`await using` examples in specs.
- Implement the dispose pattern: `Dispose(bool disposing)` with finalizer safety.
- **WRONG:** Client class with gRPC channel but no `IDisposable` implementation.

### CS-3: Nullable reference types (NRT)
Modern C# (8.0+) supports nullable reference types. Specs should:
- Enable `<Nullable>enable</Nullable>` in the project.
- Mark nullable parameters as `string?`, not just `string`.
- Use `[NotNull]`, `[MaybeNull]` attributes where needed.
- Never ignore nullable warnings in code snippets.

### CS-4: Properties vs fields
C# uses properties, not public fields.
- **WRONG:** `public string Address;`
- **RIGHT:** `public string Address { get; set; }` or `public string Address { get; init; }` (C# 9+)
- Use `init` setters for immutable configuration objects.

### CS-5: Record types for DTOs (C# 9+)
Use `record` types for immutable data transfer objects:
```csharp
public record KubeMQError(string Code, string Message, string? RequestId = null);
```
Check the target framework version before using records.

---

## Naming Rules

### CS-6: Standard library name collisions
These names exist in .NET BCL and should be avoided:

| Name | Namespace | Risk |
|------|-----------|------|
| `Exception` | `System` | High — base exception type |
| `TimeoutException` | `System` | High — commonly caught |
| `OperationCanceledException` | `System` | High — CancellationToken |
| `Channel` | `System.Threading.Channels` | Medium |
| `Task` | `System.Threading.Tasks` | High — never reuse |
| `Logger` | `Microsoft.Extensions.Logging` | Medium |
| `Timer` | `System.Threading` / `System.Timers` | Medium |
| `HttpClient` | `System.Net.Http` | Medium |

**Resolution:** Prefix with `KubeMQ` or use descriptive names (e.g., `KubeMQTimeoutException`).

### CS-7: Namespace convention
Follow .NET namespace conventions:
- `KubeMQ.Sdk` — root namespace
- `KubeMQ.Sdk.Client` — client classes
- `KubeMQ.Sdk.Exceptions` — exception types (NOT `Errors`)
- `KubeMQ.Sdk.Config` — configuration
- `KubeMQ.Sdk.Grpc` — gRPC internals (internal)
- Use PascalCase for all namespace segments.

### CS-8: Async method naming
All async methods MUST end with `Async` suffix:
- `SendAsync`, `SubscribeAsync`, `ConnectAsync`
- **WRONG:** `public Task<Result> Send(...)` — missing `Async` suffix
- Synchronous wrappers (if any) use the bare name: `Send(...)`

---

## Concurrency Rules

### CS-9: ConfigureAwait(false) in library code
SDK/library code should use `ConfigureAwait(false)` on all awaits to avoid deadlocks:
```csharp
var result = await client.SendAsync(msg).ConfigureAwait(false);
```
- This is critical for library code used in ASP.NET or WPF contexts.
- Application code does NOT need this — only library code.

### CS-10: CancellationToken propagation
Every async method MUST accept `CancellationToken cancellationToken = default` as its last parameter.
- Pass the token to all downstream async calls.
- Check `cancellationToken.ThrowIfCancellationRequested()` at entry points.
- **WRONG:** `public Task<Result> SendAsync(Message msg)` — missing CancellationToken
- **RIGHT:** `public Task<Result> SendAsync(Message msg, CancellationToken cancellationToken = default)`

### CS-11: SemaphoreSlim for async locking
`lock` statements cannot be used with `await`. Use `SemaphoreSlim` for async-compatible locking:
```csharp
private readonly SemaphoreSlim _semaphore = new(1, 1);

await _semaphore.WaitAsync(cancellationToken);
try {
    // critical section with await
} finally {
    _semaphore.Release();
}
```
- **WRONG:** `lock (_obj) { await DoSomethingAsync(); }` — deadlock risk

### CS-12: Thread-safe collections
Use `System.Collections.Concurrent` types for shared state:
- `ConcurrentDictionary<TKey, TValue>` instead of `Dictionary` + lock
- `ConcurrentQueue<T>` instead of `Queue` + lock
- `Channel<T>` (System.Threading.Channels) for producer-consumer patterns

---

## Dependency Rules

### CS-13: Optional dependencies via conditional references
For optional dependencies (OTel, logging providers):
- Use `<PackageReference>` with `<PrivateAssets>all</PrivateAssets>` or make it a separate NuGet package.
- Use runtime type checking: `Type.GetType("OpenTelemetry.Trace.TracerProvider, OpenTelemetry")`
- Or create a separate package: `KubeMQ.Sdk.OpenTelemetry` that users install only if needed.
- **Never** make OTel a hard dependency of the core SDK package.

### CS-14: Microsoft.Extensions.* compatibility
If using `Microsoft.Extensions.Logging`, `Microsoft.Extensions.DependencyInjection`, etc.:
- Support multiple major versions (6.x, 7.x, 8.x).
- Use the lowest common version in the core package.
- Provide extension methods for DI: `services.AddKubeMQ(options => { ... })`

---

## Build Rules

### CS-15: Target framework considerations
- Target `netstandard2.0` for maximum compatibility (supports .NET Framework 4.6.1+ and .NET Core 2.0+).
- Multi-target if needed: `<TargetFrameworks>netstandard2.0;net6.0;net8.0</TargetFrameworks>`
- Features requiring newer APIs should be conditionally compiled with `#if NET6_0_OR_GREATER`.

### CS-16: NuGet package metadata
Specs referencing NuGet packaging must include:
- `<PackageId>`, `<Version>`, `<Authors>`, `<Description>`
- `<PackageReadmeFile>` (NuGet supports README since 2021)
- `<RepositoryUrl>` and `<PackageLicenseExpression>`
- Source Link for debugging: `<EmbedUntrackedSources>true</EmbedUntrackedSources>`

### CS-17: Verify gRPC generated code
Before referencing a gRPC method, verify it exists in the `.proto` file. The proto definition is the source of truth.
- C# gRPC uses `Grpc.Tools` for code generation.
- Generated files are in `obj/` — don't commit them.
- Service clients are `{ServiceName}.{ServiceName}Client`.
