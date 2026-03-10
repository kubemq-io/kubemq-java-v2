# JS/TS Language Constraints for Spec Agents

**Purpose:** Language-specific pitfalls that spec agents MUST follow to prevent common errors in JavaScript/TypeScript SDK specs.

---

## Syntax Rules

### JS-1: CommonJS vs ESM module format
The SDK must support both module systems or clearly pick one:
- **CommonJS:** `const { Client } = require('@kubemq/sdk');` — Node.js default before v20
- **ESM:** `import { Client } from '@kubemq/sdk';` — modern standard
- Use `package.json` `"exports"` field to support both:
```json
{
  "exports": {
    ".": {
      "import": "./dist/esm/index.js",
      "require": "./dist/cjs/index.js",
      "types": "./dist/types/index.d.ts"
    }
  }
}
```
- Specs must state which module format(s) are supported.
- **WRONG:** Only providing CommonJS when the ecosystem is moving to ESM.

### JS-2: Promise-based async API
All async operations MUST return `Promise<T>`, usable with `async/await`:
- **WRONG:** Callback-style `send(msg, (err, result) => { ... })`
- **RIGHT:** `async send(msg: Message): Promise<Result>`
- Event-based patterns (subscriptions) use EventEmitter or async iterators, not callbacks.

### JS-3: TypeScript strict mode
All TypeScript code in specs must be valid under `"strict": true`:
- No implicit `any` types
- Strict null checks enabled (`string | null` vs `string`)
- No unused parameters (prefix with `_`)
- **WRONG:** `function send(msg) { ... }` — implicit any
- **RIGHT:** `function send(msg: Message): Promise<Result> { ... }`

### JS-4: Error handling with typed errors
JavaScript/TypeScript errors extend `Error`:
```typescript
export class KubeMQError extends Error {
  constructor(
    message: string,
    public readonly code: string,
    public readonly requestId?: string,
    public readonly cause?: Error
  ) {
    super(message);
    this.name = 'KubeMQError';
    // Fix prototype chain for instanceof checks
    Object.setPrototypeOf(this, new.target.prototype);
  }
}
```
- Always set `this.name` in custom errors.
- Always fix prototype chain with `Object.setPrototypeOf` (required for TypeScript class inheritance of Error).
- Use `cause` property (ES2022) for error chaining.

### JS-5: Interface vs type alias
Use `interface` for object shapes that can be extended, `type` for unions and primitives:
- **Interface:** `interface ClientConfig { address: string; ... }` — extendable
- **Type:** `type ErrorCode = 'TIMEOUT' | 'AUTH_FAILED' | 'NOT_FOUND';` — union
- **WRONG:** Using `type` for everything or `interface` for everything.

---

## Naming Rules

### JS-6: Standard library / ecosystem name collisions
These names are commonly used in Node.js/browser and should be avoided:

| Name | Source | Risk |
|------|--------|------|
| `Error` | global | High — base error type |
| `TypeError` | global | High — common error |
| `Buffer` | Node.js global | High — binary data |
| `Channel` | `worker_threads` / BroadcastChannel | Medium |
| `EventEmitter` | `events` | Medium |
| `Logger` | various logging libs | Medium |
| `Timer` | global (setTimeout return) | Low |
| `Response` | `fetch` API | Medium |
| `Request` | `fetch` API | Medium |

**Resolution:** Prefix with `KubeMQ` (e.g., `KubeMQError`) or use descriptive names.

### JS-7: Package and file naming
- Package name: `@kubemq/sdk` (scoped npm package)
- File names: `kebab-case.ts` (e.g., `kubemq-client.ts`, `error-types.ts`)
- **WRONG:** `KubeMQClient.ts`, `error_types.ts`
- Export barrel: `src/index.ts` re-exports all public API
- Internal modules: `src/internal/` — not exported from barrel

### JS-8: Method and property naming
All methods and properties use `camelCase`:
- **WRONG:** `send_message`, `get_channel`, `is_connected`
- **RIGHT:** `sendMessage`, `getChannel`, `isConnected`
- Constants use `UPPER_SNAKE_CASE`: `DEFAULT_TIMEOUT`, `MAX_RETRIES`
- Enums use `PascalCase` for names, `PascalCase` or `UPPER_SNAKE_CASE` for values.

---

## Concurrency Rules

### JS-9: Single-threaded event loop awareness
JavaScript is single-threaded. There are no mutex/lock needs for typical code.
- No data races in single-threaded code — but async interleavings can still cause bugs.
- For CPU-intensive work, use `worker_threads` (Node.js) but document the constraint.
- **WRONG:** Using mutex/lock libraries in typical async Node.js code.
- **RIGHT:** Use async/await control flow for ordering; use `AbortController` for cancellation.

### JS-10: AbortController for cancellation
Use `AbortController` / `AbortSignal` instead of custom cancellation tokens:
```typescript
async function send(msg: Message, options?: { signal?: AbortSignal }): Promise<Result> {
  if (options?.signal?.aborted) throw new KubeMQError('Aborted');
  // Pass signal to downstream operations
}
```
- This is the standard cancellation pattern in modern JS/TS.
- gRPC-js supports `AbortSignal` via call options.

### JS-11: Resource cleanup with Symbol.dispose (ES2024) or manual close
```typescript
class Client {
  async close(): Promise<void> {
    // Clean up gRPC channel, timers, etc.
  }

  // Future: Symbol.asyncDispose for `await using`
  async [Symbol.asyncDispose](): Promise<void> {
    await this.close();
  }
}
```
- Always provide an explicit `close()` / `destroy()` method.
- Document that users MUST call `close()` to prevent resource leaks.

### JS-12: EventEmitter memory leaks
If using EventEmitter for subscriptions:
- Set `maxListeners` if many concurrent subscriptions expected.
- Always provide `removeListener` / `off` cleanup guidance.
- Consider async iterators as an alternative: `for await (const msg of client.subscribe(channel)) { ... }`
- **WRONG:** Creating EventEmitter listeners without cleanup documentation.

---

## Dependency Rules

### JS-13: Optional dependencies with peer deps
For optional dependencies (OTel):
- Declare as `peerDependencies` with `"optional": true` in `peerDependenciesMeta`:
```json
{
  "peerDependencies": {
    "@opentelemetry/api": "^1.0.0"
  },
  "peerDependenciesMeta": {
    "@opentelemetry/api": { "optional": true }
  }
}
```
- Use dynamic import: `const otel = await import('@opentelemetry/api').catch(() => null);`
- **Never** make OTel a hard `dependency`.

### JS-14: Node.js version support
- Check `"engines"` field in `package.json` for minimum Node.js version.
- Features like `AbortController` (Node 15+), `fetch` (Node 18+), `Symbol.dispose` (Node 22+) may not be available.
- Use feature detection or polyfills for newer APIs.

---

## Build Rules

### JS-15: Tree-shaking support
The SDK must be tree-shakeable for bundler users:
- Use ESM exports (not CommonJS `module.exports`)
- Mark package as side-effect free: `"sideEffects": false` in `package.json`
- Avoid top-level side effects (e.g., global registrations at import time)
- **WRONG:** `import '@kubemq/sdk';` triggers side effects that register global handlers.

### JS-16: TypeScript declaration files
- Always ship `.d.ts` declaration files in the npm package.
- Set `"types"` or `"typings"` in `package.json`.
- Use `"declaration": true` and `"declarationMap": true` in `tsconfig.json`.
- Test declarations work by consuming them in a separate test project.

### JS-17: Verify gRPC generated code
Before referencing a gRPC method, verify it exists in the `.proto` file or generated code.
- JS gRPC uses `@grpc/grpc-js` and `@grpc/proto-loader` or `grpc-tools` for static generation.
- Do not assume methods exist because they'd be convenient.

### JS-18: Test framework
Use a modern test runner:
- `vitest` (recommended — fast, ESM-native, TypeScript-native)
- `jest` with `ts-jest` (widely used)
- `node:test` (built-in, no dependencies, Node 18+)
- Specs should not assume a specific test runner unless the project already uses one.
