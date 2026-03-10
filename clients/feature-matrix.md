# KubeMQ SDK Feature Matrix

Last updated: 2026-03-10
SDK versions: Java v2.1.1

> Before each minor/major release, review this file and update all SDK columns to reflect current status.

## Client Management

| Feature | Java | Tier |
|---------|------|------|
| Ping | ✅ | Core |
| Channel List | ✅ | Core |
| Server Info | ✅ | Extended |
| Channel Create | ✅ | Extended |
| Channel Delete | ✅ | Extended |

## Events (Pub/Sub)

| Feature | Java | Tier |
|---------|------|------|
| Publish | ✅ | Core |
| Subscribe with callback | ✅ | Core |
| Wildcard subscribe | ✅ | Core |
| Group subscribe | ✅ | Core |
| Unsubscribe | ✅ | Core |

## Events Store (Persistent Pub/Sub)

| Feature | Java | Tier |
|---------|------|------|
| Publish | ✅ | Core |
| Subscribe from beginning | ✅ | Core |
| Subscribe from sequence | ✅ | Core |
| Subscribe from timestamp | ✅ | Core |
| Subscribe from time delta | ✅ | Core |
| Subscribe from last | ✅ | Core |
| Subscribe new only | ✅ | Core |
| Unsubscribe | ✅ | Core |

## Queues (Stream -- Primary API)

| Feature | Java | Tier |
|---------|------|------|
| Stream upstream | ✅ | Core |
| Stream downstream | ✅ | Core |
| Visibility timeout | ✅ | Core |
| Ack | ✅ | Core |
| Reject | ✅ | Core |
| Requeue | ✅ | Core |
| DLQ | ✅ | Core |
| Delayed messages | ✅ | Core |
| Message expiration | ✅ | Core |

## Queues (Simple -- Secondary API)

| Feature | Java | Tier |
|---------|------|------|
| Send single | ✅ | Extended |
| Send batch | ✅ | Extended |
| Receive (single pull) | ✅ | Extended |
| Peek | ✅ | Extended |
| Purge queue | ❌ (NI) | Extended |

## RPC -- Commands

| Feature | Java | Tier |
|---------|------|------|
| Send command | ✅ | Core |
| Subscribe to commands | ✅ | Core |
| Group subscribe | ✅ | Core |
| Send response | ✅ | Core |

## RPC -- Queries

| Feature | Java | Tier |
|---------|------|------|
| Send query | ✅ | Core |
| Subscribe to queries | ✅ | Core |
| Group subscribe | ✅ | Core |
| Send response | ✅ | Core |
| Cache-enabled queries | ✅ | Core |

## Legend

- ✅ Compliant -- fully implemented and tested
- ⚠️ Partial -- implemented with limitations (see notes)
- ❌ Missing -- not implemented
- ❌ (NI) -- not implemented; stub throws `NotImplementedException`
