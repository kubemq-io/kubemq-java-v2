# Go Language Constraints for Spec Agents

**Purpose:** Language-specific pitfalls that spec agents MUST follow to prevent common errors in Go SDK specs.

---

## Syntax Rules

### GO-1: No constructors — use factory functions
Go has no constructors. Use `NewXxx()` factory functions.
- **WRONG:** `type Client struct { ... }` with implicit construction
- **RIGHT:** `func NewClient(opts ...Option) (*Client, error)` factory function

### GO-2: Exported vs unexported naming
Go uses capitalization for visibility, not access modifiers.
- Exported (public): `Client`, `SendMessage`, `ErrorCode`
- Unexported (private): `client`, `sendMessage`, `errorCode`
- All types in specs intended for public use MUST start with uppercase.

### GO-3: Error interface implementation
Go errors must implement the `error` interface: `Error() string`.
- For wrapping: implement `Unwrap() error` (Go 1.13+)
- For `errors.Is`/`errors.As`: implement `Is(target error) bool` and/or expose typed fields
- **WRONG:** `type KubeMQError struct { Message string }` (missing Error() method)
- **RIGHT:** `type KubeMQError struct { ... }` with `func (e *KubeMQError) Error() string { ... }`

### GO-4: No generics before Go 1.18
If the SDK targets Go <1.18, do NOT use generics (`[T any]`). Use `interface{}` or concrete types instead.
- Check the `go.mod` file for the minimum Go version.
- If targeting 1.18+, generics are fine but avoid overuse — Go community prefers concrete types.

### GO-5: Multiple return values for errors
Go functions return errors as the last return value, not via exceptions.
- **WRONG:** `func Send(msg Message) Result` (no error return)
- **RIGHT:** `func Send(msg Message) (Result, error)`
- Never panic for recoverable errors.

---

## Naming Rules

### GO-6: Standard library name collisions
These names exist in Go's standard library and should be avoided or prefixed:

| Name | Package | Risk |
|------|---------|------|
| `Context` | `context` | High — used everywhere |
| `Error` | `errors` | High — conflicts with error interface |
| `Logger` | `log` / `slog` | Medium |
| `Client` | `net/http` | Medium — very common |
| `Channel` | built-in (chan) | Medium — keyword-adjacent |
| `Timer` | `time` | Low |
| `Reader`/`Writer` | `io` | Medium |

**Resolution:** Prefix with `KubeMQ` (e.g., `KubeMQClient`) or use domain-specific names (e.g., `EventsClient`).

### GO-7: Package naming convention
Go packages use short, lowercase, single-word names. No underscores or camelCase.
- **WRONG:** `package kubemq_errors`, `package kubeMQClient`
- **RIGHT:** `package kubemq`, `package errors` (within the module)
- SDK packages: `github.com/kubemq-io/kubemq-go/...`
- Sub-packages: `errors`, `client`, `config`, `transport`

### GO-8: Interface naming convention
Go interfaces with a single method are named with the `-er` suffix.
- `Reader`, `Writer`, `Closer`, `Sender`, `Subscriber`
- Multi-method interfaces use descriptive nouns: `MessageHandler`, `CredentialProvider`
- **WRONG:** `type IClient interface` (no `I` prefix — that's C#/Java style)

---

## Concurrency Rules

### GO-9: Goroutine leak prevention
Every goroutine launched MUST have a shutdown mechanism.
- Use `context.Context` for cancellation signals.
- Every `go func()` must have a corresponding shutdown path (context cancel, channel close, or WaitGroup).
- **WRONG:** `go func() { for { ... } }()` with no exit condition
- **RIGHT:** `go func() { for { select { case <-ctx.Done(): return; case msg := <-ch: ... } } }()`

### GO-10: Channel usage patterns
- Unbuffered channels (`make(chan T)`) block both sender and receiver — use only for synchronization.
- Buffered channels (`make(chan T, n)`) for message passing — document the buffer size rationale.
- Always close channels from the sender side, never the receiver.
- Use `select` with `default` for non-blocking operations.

### GO-11: Mutex usage
- Use `sync.Mutex` for exclusive access, `sync.RWMutex` for read-heavy workloads.
- Always unlock in `defer`: `mu.Lock(); defer mu.Unlock()`
- Never copy a `sync.Mutex` (it's a value type) — embed it as a pointer or use it in a struct that's never copied.
- **WRONG:** Passing a struct containing a mutex by value.

### GO-12: context.Context is always the first parameter
- **WRONG:** `func Send(msg Message, ctx context.Context) error`
- **RIGHT:** `func Send(ctx context.Context, msg Message) error`
- Never store `context.Context` in a struct field (except for request-scoped objects).

---

## Dependency Rules

### GO-13: Optional dependencies via build tags
Go doesn't have `provided` scope like Maven. For optional dependencies (OTel):
- Use build tags (`//go:build otel`) to conditionally compile instrumentation code.
- OR use a separate sub-package (e.g., `kubemq-go/otel`) that users import only if needed.
- Never import optional dependencies in the main package — it forces all users to download them.

### GO-14: Module versioning (v2+)
If the SDK is at major version 2+, the module path MUST include the version suffix:
- `github.com/kubemq-io/kubemq-go/v2`
- All import paths must include `/v2` — this is enforced by the Go toolchain.
- **WRONG:** `github.com/kubemq-io/kubemq-go` for v2+ releases.

---

## Build Rules

### GO-15: `internal/` packages are not importable
Code in `internal/` directories cannot be imported outside the module. Use this for implementation details.
- Public API types go in top-level packages.
- Internal helpers go in `internal/`.
- **Never** put types referenced in specs' public API into `internal/`.

### GO-16: Functional options pattern
Go SDKs use functional options for configuration:
```go
type Option func(*clientConfig)

func WithAddress(addr string) Option {
    return func(c *clientConfig) { c.address = addr }
}

client, err := NewClient(WithAddress("localhost:50000"), WithTLS(tlsConfig))
```
All configuration in specs MUST use this pattern, not builder pattern or struct literals.

### GO-17: Verify protobuf/gRPC generated code
Before referencing a gRPC method, verify it exists in the generated `.pb.go` files. The `.proto` definition is the source of truth.
- Generated files are typically in a `pb` or `proto` sub-package.
- Do not assume methods exist because they'd be convenient.
