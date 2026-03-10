# Python Language Constraints for Spec Agents

**Purpose:** Language-specific pitfalls that spec agents MUST follow to prevent common errors in Python SDK specs.

---

## Syntax Rules

### PY-1: Type hints are required but not enforced at runtime
Python type hints (PEP 484) are for documentation and static analysis only.
- All public API methods MUST include type hints in specs.
- Use `from __future__ import annotations` for forward references (Python 3.7+).
- **WRONG:** `def send(self, msg):` — missing type hints
- **RIGHT:** `def send(self, msg: Message) -> Result:` or `async def send(self, msg: Message) -> Result:`

### PY-2: async vs sync API duplication
Python async (`asyncio`) and sync APIs are fundamentally different — you cannot use `await` in sync code.
- SDKs often need BOTH sync and async interfaces.
- Options: (a) two separate client classes (`Client` and `AsyncClient`), (b) sync wrapper using `asyncio.run()`, (c) single async-only API.
- Specs must explicitly state which approach is used and be consistent.
- **WRONG:** Single class with both `def send()` and `async def send_async()` — confusing API.

### PY-3: No real private access
Python has no true private members. Convention:
- `_name` — "private by convention" (single underscore)
- `__name` — name-mangled (double underscore) — avoid in SDKs, it complicates inheritance
- Use single underscore for internal/private members in specs.
- Public API = no underscore prefix.

### PY-4: Exception hierarchy
Python exceptions extend `Exception` (not `BaseException` — that's for system exits).
```python
class KubeMQError(Exception):
    """Base exception for KubeMQ SDK."""
    def __init__(self, message: str, code: str | None = None):
        super().__init__(message)
        self.code = code
```
- Use `raise ... from original_error` for exception chaining.
- **WRONG:** `raise KubeMQError(str(e))` — loses original traceback
- **RIGHT:** `raise KubeMQError("failed") from e`

### PY-5: Context managers for resource cleanup
Any class that holds resources (gRPC channels, connections) MUST implement context manager protocol:
```python
class Client:
    def __enter__(self) -> "Client": ...
    def __exit__(self, *args) -> None: ...

    # Async version:
    async def __aenter__(self) -> "Client": ...
    async def __aexit__(self, *args) -> None: ...
```
- Specs must show `with Client(...) as client:` usage examples.

---

## Naming Rules

### PY-6: Standard library name collisions
These names exist in Python's stdlib and should be avoided:

| Name | Module | Risk |
|------|--------|------|
| `ConnectionError` | builtins | High — built-in exception |
| `TimeoutError` | builtins | High — built-in exception |
| `PermissionError` | builtins | High — built-in exception |
| `logging` | stdlib | Medium — module name |
| `queue` | stdlib | Medium — module name |
| `signal` | stdlib | Medium — module name |
| `channel` | N/A but gRPC uses it | Medium |

**Resolution:** Prefix with `KubeMQ` (e.g., `KubeMQConnectionError`, `KubeMQTimeoutError`).

### PY-7: Package and module naming
Python packages and modules use lowercase with underscores:
- **WRONG:** `kubemqSdk`, `KubeMQ_Client`
- **RIGHT:** `kubemq`, `kubemq.client`, `kubemq.exceptions`
- Package structure:
  - `kubemq/` — root package
  - `kubemq/client.py` — client classes
  - `kubemq/exceptions.py` — exception types (NOT `errors.py` — Python convention is `exceptions`)
  - `kubemq/config.py` — configuration
  - `kubemq/_internal/` — internal modules

### PY-8: Method naming convention
All methods and functions use `snake_case`:
- **WRONG:** `sendMessage`, `getMessage`, `isConnected`
- **RIGHT:** `send_message`, `get_message`, `is_connected`
- Properties use `@property` decorator, not `get_*`/`set_*` methods.

---

## Concurrency Rules

### PY-9: asyncio event loop management
Never create a new event loop inside library code.
- **WRONG:** `loop = asyncio.new_event_loop()` inside the SDK
- **RIGHT:** Use `await` in async methods, let the user manage the event loop.
- For sync wrappers, use `asyncio.run()` at the top level only.

### PY-10: Thread safety with GIL caveats
Python's GIL protects against some data races but NOT all.
- `dict`, `list` operations are generally atomic for single operations.
- Compound operations (check-then-act) still need `threading.Lock`.
- For async code, use `asyncio.Lock` (NOT `threading.Lock`).
- **WRONG:** Using `threading.Lock` in async code — causes deadlocks.

### PY-11: Graceful shutdown
Async cleanup must use `asyncio` shutdown patterns:
```python
async def close(self) -> None:
    """Gracefully shut down the client."""
    self._running = False
    if self._channel:
        await self._channel.close()
    # Cancel pending tasks
    for task in self._tasks:
        task.cancel()
    await asyncio.gather(*self._tasks, return_exceptions=True)
```
- Always cancel pending tasks and await them.
- Use `atexit` or signal handlers for cleanup in sync mode.

---

## Dependency Rules

### PY-12: Optional dependencies with extras
For optional dependencies (OTel, specific logging backends):
- Declare as extras in `pyproject.toml`:
```toml
[project.optional-dependencies]
otel = ["opentelemetry-api>=1.0", "opentelemetry-sdk>=1.0"]
```
- Use conditional imports:
```python
try:
    from opentelemetry import trace
    HAS_OTEL = True
except ImportError:
    HAS_OTEL = False
```
- **Never** make OTel a hard dependency in the main `[project.dependencies]`.

### PY-13: Minimum Python version
- Check `pyproject.toml` for `requires-python`.
- Features like `match/case` (3.10+), `|` union types (3.10+), `TypeAlias` (3.10+) may not be available.
- Use `from __future__ import annotations` for postponed evaluation of annotations (3.7+).
- `dataclasses` require Python 3.7+.

---

## Build Rules

### PY-14: pyproject.toml is the standard
Modern Python projects use `pyproject.toml`, not `setup.py` or `setup.cfg`:
- Build backend: `hatchling`, `setuptools`, or `poetry-core`
- **WRONG:** Referencing `setup.py` configuration in specs
- **RIGHT:** All configuration in `pyproject.toml`

### PY-15: Verify gRPC generated code
Before referencing a gRPC method, verify it exists in the `_pb2.py` / `_pb2_grpc.py` files.
- Python gRPC uses `grpcio-tools` for code generation.
- Service stubs are `{ServiceName}Stub`.
- Do not assume methods exist because they'd be convenient.

### PY-16: Test framework
Python SDK tests should use `pytest` (not `unittest`):
- Async tests use `pytest-asyncio` with `@pytest.mark.asyncio`.
- Fixtures for client setup/teardown.
- `pytest-cov` for coverage reporting.
