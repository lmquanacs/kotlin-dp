# MCP Client — a worked design example

A design walkthrough for building a **Model Context Protocol client** in Kotlin, used here as a
realistic end-to-end example of the patterns in this repository.

MCP is a good example precisely because it is not a toy: a full client is a **bidirectional JSON-RPC
peer with a negotiated lifecycle, a pluggable transport, cursor-paginated collections, push
notifications, two distinct error channels, and a service-name resolution step**. Almost every
pattern in the catalogue earns its place somewhere in it — and a few conspicuously do not.

It is also a good example of how much two constraints can delete. A **stateless** client pinned to
the **latest protocol revision** drops roughly half the machinery below and five of the patterns.
**§2** covers that reduction and is worth reading before the rest.

> **Status:** this is a design document. The Kotlin below is illustrative — it is written to be
> read, not compiled, and is not covered by this repo's test suite. Treat the shapes as the
> deliverable, not the exact signatures.
>
> **Spec caveat:** MCP's transport and authorization details have changed across spec revisions
> (HTTP+SSE was superseded by Streamable HTTP; the auth story has moved more than once). The
> *architecture* here is stable across those changes, but check the current spec revision before
> committing to wire-level details — particularly the protocol-version header name and the exact
> version string, which §2.2 pins to a constant.

---

## 1. The shape of the problem

In a **session-based** client, the most common mistake is building a one-way request/response client.
There, an MCP client is a *peer*, not a client: the server can send *you* requests —
`sampling/createMessage`, `roots/list`, `elicitation/create` — so message routing must be symmetric
from day one, and retrofitting that later means rewriting the dispatcher.

In a **stateless** client that constraint disappears entirely, along with about half of this
document. Statelessness and protocol-version support are the first two decisions, not details — see
**§2**. The rest of §§3–12 describe the full session-based, version-negotiating design; §2 gives the
reduction and points at what still applies.

The connect path has four distinct stages, and conflating the middle two is a mistake in both
models:

```
ServiceName ──resolve()──► Set<Endpoint> ──select()──► Endpoint ──► Transport ──► Session
              (discovery)                (load balance)            (I/O)        (protocol)
```

**One name resolves to many addresses.** Resolution and selection are different concerns with
different failure modes and different caching rules.

---

## 2. Constraints that shape this design

Two constraints do more to determine the pattern set than anything about the domain. Decide both
before writing code.

### 2.1 Stateless vs session-based

A **session-based** client holds a live connection: a stdio subprocess, or Streamable HTTP with an
`Mcp-Session-Id` and a server→client stream. It initializes once, keeps state, and receives pushes.

A **stateless** client sends one self-contained HTTP request per call. No session ID, no persistent
stream, no sticky routing. Each request can land on a different replica. This is the model that fits
serverless functions, plain load balancers, and short-lived CLI invocations.

**This is not a small variation.** It removes whole capabilities from the protocol surface and
deletes most of the machinery below.

#### What changes

| Concern | Session-based | Stateless |
|---|---|---|
| Transport | stdio, Streamable HTTP + session | **HTTP POST per call only** — stdio is impossible |
| `initialize` handshake | once per connection | per request, or skipped by agreement |
| Correlation map (`id`→`Deferred`) | required | **not needed** — the HTTP response *is* the correlation |
| Server→client requests (sampling, roots, elicitation) | supported | **unavailable** |
| Notifications (`list_changed`, `progress`, logging) | pushed | **unavailable** — poll or expire |
| `resources/subscribe` | supported | **unavailable** |
| Cancellation | `notifications/cancelled` | abort the HTTP request |
| Session state machine | 7 states | **2** — `Configured` / `Failed` |
| Load balancing | needs sticky sessions | **any endpoint, per request** ← the win |
| Re-resolution | on reconnect | per request, and it's cheap ← the win |
| Cache invalidation | push-driven (`list_changed`) | **TTL only** ← the main regression |
| Reconnect logic | required | none exists |

#### What you gain

Horizontal scaling with no session affinity, trivial failover (a dead replica costs one retry, not a
reconnect), serverless deployment, and materially less code — no `Peer`, no pending-request registry,
no reconnect state machine, no lifecycle to leak.

#### What you give up

**Sampling and elicitation are gone.** A server cannot ask your model to complete something, and
cannot ask the user a question mid-tool-call. If a server's tools depend on either, a stateless
client cannot drive them — this is a compatibility constraint to check per server, not something to
engineer around.

**Push invalidation is gone**, so a changed tool list reaches you only when your TTL expires. Pick
that TTL deliberately: it is now your staleness bound, and the only one you have.

**Long-running tools fit badly.** With no progress notifications and an HTTP timeout as your only
limit, anything slow needs the server to expose a job-handle pattern (`start` → `poll`) at the tool
level.

#### The reduced architecture

```
ServiceName ──resolve()──► Endpoint ──HTTP POST──► JsonRpcResponse
```

```
┌──────────────────────────────────────────────┐
│  Host / agent loop                            │
├──────────────────────────────────────────────┤
│  StatelessMcpClient   ← Facade                │
│    callTool · listTools (TTL-cached)          │
├──────────────────────────────────────────────┤
│  Resolver + selector  ← Strategy chain + cache│
├──────────────────────────────────────────────┤
│  HTTP client          ← keep-alive, auth, retry│
└──────────────────────────────────────────────┘
```

Three layers instead of six. `Session` and `Peer` disappear:

```kotlin
class StatelessMcpClient(
    private val resolver: Resolver,
    private val selector: EndpointSelector,
    private val http: HttpTransport,
    private val toolCache: CacheAside<ServiceName, List<Tool>>,
    private val retry: RetryPolicy,
) {
    suspend fun callTool(
        service: ServiceName,
        tool: ToolName,
        args: JsonObject,
    ): ToolCallOutcome {
        val server = resolver.resolve(service) as? ResolvedServer.Http
            ?: return ToolCallOutcome.Failed.NotConnected

        // Re-resolved and re-selected per request: failover is free, no sticky session needed.
        val endpoint = selector.select(server.endpoints)

        // One request, one response. No pending map, no id bookkeeping beyond JSON-RPC's requirement.
        val response = http.post(endpoint, server.auth, rpc("tools/call", callParams(tool, args)))
        return response.toToolOutcome()
    }

    /** TTL-cached: there is no `list_changed` to invalidate on. */
    suspend fun listTools(service: ServiceName): List<Tool> = toolCache.get(service).orEmpty()
}
```

#### What still applies from the rest of this document

| Section | Status under stateless |
|---|---|
| §5 Core types | **Applies** — minus `ResolvedServer.Stdio` |
| §8 Two error channels | **Applies unchanged** — this is orthogonal to transport |
| §9 Service name resolution | **Applies, and matters more** — now on the hot path of every call |
| §10 Pagination | **Applies** — the cursor travels in the request, so it needs no session |
| §11 Multi-server aggregation | **Applies unchanged** |
| §12 Testing | **Applies, and gets easier** — a fake HTTP responder replaces the in-process transport |
| §6 Session state | **Mostly deleted** — two states, no capability gate unless you re-`initialize` |
| §7 The peer | **Deleted** — no correlation map, no inbound dispatch |
| §10 Notifications | **Deleted** — nothing pushes |

#### Two things to get right

**Resolution is now per-request, so its cache is load-bearing.** Every `callTool` hits the resolver.
Without single-flight and a sane TTL you have turned one tool call into one DNS lookup —
see [cache-aside](../src/main/kotlin/com/example/kotlindp/patterns/production/cacheaside/).

**Retry is safe for reads and dangerous for writes.** Stateless requests look trivially retryable,
which is the trap: `tools/list` is idempotent, but `tools/call` generally is not. Retrying a tool
that charges a card or sends a message duplicates the effect. Classify per method, and require an
idempotency key before retrying a call —
see [retry](../src/main/kotlin/com/example/kotlindp/patterns/production/retry/).

### 2.2 Pinned to the latest protocol revision

Supporting exactly one protocol revision — the latest — removes the other half of the compatibility
machinery.

Normally `initialize` is a *negotiation*: the client proposes a version, the server may answer with
an older one it supports, and the client decides whether to proceed. Supporting many revisions then
pulls in a per-version codec ([Strategy](../src/main/kotlin/com/example/kotlindp/patterns/behavioral/strategy/))
or a compatibility shim ([Adapter](../src/main/kotlin/com/example/kotlindp/patterns/structural/adapter/)),
plus a supported-versions set and a matrix of what works where.

Pinned to latest, all of that becomes a constant and a comparison:

```kotlin
/** The only revision this client speaks. Not a negotiated field — a fact about the build. */
const val PROTOCOL_VERSION: String = "<latest-revision>"

sealed interface ConnectFailure {
    data class UnsupportedProtocolVersion(
        val serverOffered: String,
        val weRequire: String = PROTOCOL_VERSION,
    ) : ConnectFailure
    // ...
}
```

**Fail fast and loudly.** A version mismatch is a compatibility fact, not a degraded mode — there is
nothing to fall back to. Detect it when a server is registered or configured, not on the first tool
call in production.

**The trade is explicit:** you cannot talk to servers on older revisions at all. That is usually the
right call for an internal fleet you control, and the wrong one for a client that consumes
third-party servers you don't.

Three consequences specific to this combination:

**Only one transport is in scope.** The older HTTP+SSE transport belongs to superseded revisions, so
pinning to latest means Streamable HTTP and nothing else — reinforcing the point in §14 that the
Bridge no longer has two implementations to bridge.

**In stateless mode the version travels on every request.** There is no session to carry it, so the
`MCP-Protocol-Version` header goes on each POST rather than being established once at handshake.
(Confirm the exact header name against the current spec — see the caveat at the top.)

**Protocol version is not the same thing as server capabilities, and conflating them is the trap.**
Pinning the revision fixes the *grammar*; it says nothing about whether a given server offers tools,
resources, or prompts. Capability checking stays. And if you also skip `initialize` — which stateless
clients often do — you have no capabilities object at all, so discovery degenerates to calling the
method and handling JSON-RPC `-32601 Method not found`. Model that as a first-class outcome rather
than an unexpected error:

```kotlin
sealed interface Failed : ToolCallOutcome {
    /** Server does not implement this method on the pinned revision. Expected, not exceptional. */
    data class MethodUnsupported(val method: String) : Failed
    // ...
}
```

One test is enough to hold this in place: assert the constant equals the version string actually sent
on the wire, so a bump can't be half-applied.

> **The one fork worth confirming.** "Stateless" is used for two different things. If your servers
> use Streamable HTTP and may answer a single POST with an *SSE stream*, then within that one
> request/response the server can still send you requests — so sampling and progress survive, and
> you need a mini-dispatcher for the duration of the call. If your client is pure JSON
> request/response, the table above holds exactly. The rest of this section assumes the latter.

---

## 3. Layering

*Session-based model. For the three-layer stateless reduction, see §2.*

```
┌────────────────────────────────────────────────────────────┐
│  Host / agent loop                                          │
│    aggregates many servers into one tool namespace          │
├────────────────────────────────────────────────────────────┤
│  McpClient            ← Facade                              │
│    callTool · listTools · readResource · getPrompt          │
├────────────────────────────────────────────────────────────┤
│  Session              ← State machine + capability gate     │
│    initialize handshake · capability negotiation            │
├────────────────────────────────────────────────────────────┤
│  Peer                 ← Correlation + bidirectional dispatch│
│    id→Deferred registry · inbound request handlers          │
├────────────────────────────────────────────────────────────┤
│  Transport            ← Bridge                              │
│    stdio · Streamable HTTP · in-process (tests)             │
├────────────────────────────────────────────────────────────┤
│  Resolver             ← Strategy chain + cache              │
│    static · env · DNS/SRV · registry · .well-known          │
└────────────────────────────────────────────────────────────┘
```

Each boundary is a seam you can test independently. The in-process transport is what makes the whole
stack testable without spawning subprocesses or opening sockets.

---

## 4. Pattern map

| Layer | Pattern | Why | Catalogue |
|---|---|---|---|
| Transport | **Bridge** | Protocol logic and transport vary independently — the textbook case | [bridge](../src/main/kotlin/com/example/kotlindp/patterns/structural/bridge/) |
| Session | **State** | Capabilities *only exist after* `initialize`; sealed states make that unrepresentable-if-wrong | [state](../src/main/kotlin/com/example/kotlindp/patterns/behavioral/state/) |
| Peer | **Correlation Identifier** | `id` → pending `CompletableDeferred`; the core of any JSON-RPC peer | — |
| Peer | **Chain of Responsibility** | Inbound/outbound interceptors: auth, tracing, rate limit, consent | [chainofresponsibility](../src/main/kotlin/com/example/kotlindp/patterns/behavioral/chainofresponsibility/) |
| Client | **Facade** | One method per use case over lifecycle + negotiation + correlation | [facade](../src/main/kotlin/com/example/kotlindp/patterns/structural/facade/) |
| Client | **Decorator** | Caching `tools/list`, metrics, logging — via `by` delegation | [decorator](../src/main/kotlin/com/example/kotlindp/patterns/structural/decorator/) |
| Tools | **Adapter** | MCP JSON Schema → your LLM SDK's tool format | [adapter](../src/main/kotlin/com/example/kotlindp/patterns/structural/adapter/) |
| Lists | **Iterator** | Cursor pagination (`nextCursor`) as a lazy `Flow` | [iterator](../src/main/kotlin/com/example/kotlindp/patterns/behavioral/iterator/) |
| Notifications | **Observer** | `StateFlow` for connection state, `SharedFlow` for events | [observer](../src/main/kotlin/com/example/kotlindp/patterns/behavioral/observer/) |
| Config | **Abstract Factory** | Transport + auth + framing as a *matched* family | [abstractfactory](../src/main/kotlin/com/example/kotlindp/patterns/creational/abstractfactory/) |
| Multi-server | **Composite** | N servers → one tool list, plus a collision policy | [composite](../src/main/kotlin/com/example/kotlindp/patterns/structural/composite/) |
| Resolution | **Strategy + CoR** | Pluggable resolvers tried in precedence order | [strategy](../src/main/kotlin/com/example/kotlindp/patterns/behavioral/strategy/) |
| Resolution | **Cache-Aside** | Single-flight: 10 connections must not fire 10 DNS lookups | [cacheaside](../src/main/kotlin/com/example/kotlindp/patterns/production/cacheaside/) |
| Errors | **Sealed result** | Two error channels that mean different things (§8) | [result](../src/main/kotlin/com/example/kotlindp/patterns/kotlinidioms/result/) |
| Identity | **Value class** | `ServiceName` vs `Endpoint` vs `ToolName` | [inlinereified](../src/main/kotlin/com/example/kotlindp/patterns/kotlinidioms/inlinereified/) |
| Resilience | **Retry + Circuit Breaker** | Reconnection, and the resolver is itself fallible | [production/](../src/main/kotlin/com/example/kotlindp/patterns/production/) |

---

## 5. Core types

Resolution should not yield a URL. It should yield a **transport family**, because stdio servers
have no address at all:

```kotlin
@JvmInline value class ServiceName(val value: String)
@JvmInline value class ToolName(val value: String)

data class Endpoint(val host: String, val port: Int, val secure: Boolean)

sealed interface ResolvedServer {
    data class Stdio(
        val command: String,
        val args: List<String>,
        val env: Map<String, String> = emptyMap(),
    ) : ResolvedServer

    data class Http(
        val endpoints: List<Endpoint>,
        val auth: AuthSpec,
    ) : ResolvedServer
}

sealed interface AuthSpec {
    object None : AuthSpec
    data class Bearer(val token: String) : AuthSpec
    data class OAuth(val issuer: String, val scopes: Set<String>) : AuthSpec
}
```

That sealed return is what keeps the Abstract Factory honest: a resolver returning `Stdio` cannot
accidentally be paired with HTTP auth, because the family members are constructed together.

---

## 6. Session state — where sealed classes pay for themselves

The flag-based version of this has a `var initialized: Boolean` and a
`var serverCapabilities: ServerCapabilities?`, and every call site has to know that the second is
only meaningful when the first is true. The sealed version deletes that entire class of bug:

```kotlin
sealed interface SessionState {
    object Disconnected : SessionState
    data class Resolving(val name: ServiceName) : SessionState
    data class Connecting(val server: ResolvedServer) : SessionState
    data class Initializing(val transport: Transport) : SessionState

    /** Capabilities exist only in this state — there is no nullable field to read too early. */
    data class Ready(
        val transport: Transport,
        val protocolVersion: String,
        val serverCapabilities: ServerCapabilities,
        val serverInfo: ServerInfo,
    ) : SessionState

    data class Failed(val cause: ConnectFailure) : SessionState
    object Closed : SessionState
}
```

Capability checks then become a single gate rather than a scattered convention:

```kotlin
private fun requireReady(feature: String): SessionState.Ready {
    val state = _state.value
    check(state is SessionState.Ready) { "$feature requires a ready session, was ${state::class.simpleName}" }
    return state
}

suspend fun listTools(): List<Tool> {
    val ready = requireReady("tools/list")
    check(ready.serverCapabilities.tools != null) { "server does not advertise tools" }
    // ...
}
```

**Resolution and the handshake are `suspend` and fallible, so they cannot happen in a constructor.**
They belong in the `Disconnected → Ready` transition — which is a large part of why this state
machine earns its keep rather than being ceremony.

---

## 7. The peer: correlation and bidirectional dispatch

```kotlin
class Peer(
    private val transport: Transport,
    private val scope: CoroutineScope,
    private val inbound: InboundHandlers,   // sampling / roots / elicitation
) {
    private val pending = ConcurrentHashMap<RequestId, CompletableDeferred<JsonRpcResponse>>()
    private val nextId = AtomicLong()

    suspend fun request(method: String, params: JsonElement?, timeout: Duration): JsonRpcResponse {
        val id = RequestId(nextId.incrementAndGet())
        val deferred = CompletableDeferred<JsonRpcResponse>()
        pending[id] = deferred
        try {
            transport.send(JsonRpcRequest(id, method, params))
            return withTimeout(timeout) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            // Tell the peer, not just ourselves — otherwise the server keeps working on a dead request.
            transport.send(JsonRpcNotification("notifications/cancelled", cancelParams(id, "timeout")))
            throw e
        } finally {
            pending.remove(id)          // must be in `finally`, or a timeout leaks the entry forever
        }
    }

    /** Symmetric routing. This is the part a one-way client design cannot retrofit cheaply. */
    private suspend fun onMessage(message: JsonRpcMessage) = when (message) {
        is JsonRpcResponse     -> pending.remove(message.id)?.complete(message) ?: Unit
        is JsonRpcRequest      -> transport.send(inbound.handle(message))   // server → client
        is JsonRpcNotification -> notifications.emit(message)
    }
}
```

Three details that are load-bearing:

- **`pending.remove` in `finally`.** A timed-out request that stays in the map is a permanent leak,
  and the entry will never be completed.
- **Cancellation is protocol-level.** Local `withTimeout` alone leaves the server doing work nobody
  will read. Send `notifications/cancelled`.
- **Never swallow `CancellationException`** in the dispatch loop. A broad `runCatching` there breaks
  structured concurrency and the scope hangs on close. See
  [coroutines](../src/main/kotlin/com/example/kotlindp/patterns/kotlinidioms/coroutines/).

---

## 8. Two error channels — do not collapse them

This is the design decision most worth getting right, and it is
[typed errors](../src/main/kotlin/com/example/kotlindp/patterns/kotlinidioms/result/) in its purest
form:

| Channel | Means | Who handles it |
|---|---|---|
| JSON-RPC `error` response | Protocol failure — unknown method, bad params, server fault | **Your code**: retry, reconnect, surface to the operator |
| Tool result with `isError: true` | The tool ran and failed — file missing, API rejected the call | **The model**: feed it back as context so it can adapt |

Collapsing both into one exception type destroys that distinction, and the symptom is an agent that
gives up on recoverable tool failures instead of retrying with different arguments.

```kotlin
sealed interface ToolCallOutcome {
    /** The tool ran. `isError` distinguishes success from an execution failure the model should see. */
    data class Completed(val content: List<Content>, val isError: Boolean) : ToolCallOutcome

    /** The call never ran. These are yours to handle, not the model's. */
    sealed interface Failed : ToolCallOutcome {
        data class UnknownTool(val name: ToolName) : Failed
        data class InvalidArguments(val detail: String) : Failed
        data class Protocol(val code: Int, val message: String) : Failed
        data class Transport(val cause: Throwable) : Failed
        object Timeout : Failed
        object NotConnected : Failed
    }
}
```

---

## 9. Service name resolution

Resolvers are a first-match chain — the functional form, so precedence is visible at the call site
instead of buried in constructor nesting:

```kotlin
fun interface Resolver {
    /** null means "not mine" — the chain continues. */
    suspend fun resolve(name: ServiceName): ResolvedServer?
}

class ResolverChain(private val resolvers: List<Resolver>) : Resolver {
    override suspend fun resolve(name: ServiceName): ResolvedServer? =
        resolvers.firstNotNullOfOrNull { it.resolve(name) }
}

val resolver = ResolverChain(
    listOf(
        localOverrideResolver,   // developer escape hatch, always first
        envResolver,
        registryResolver,        // Consul / etcd / MCP registry
        dnsSrvResolver,
    ),
)
```

Caching is a **decorator**, so the DNS resolver never learns it is cached:

```kotlin
class CachingResolver(
    private val delegate: Resolver,
    private val cache: CacheAside<ServiceName, ResolvedServer>,
) : Resolver by delegate {
    override suspend fun resolve(name: ServiceName): ResolvedServer? = cache.get(name)
}
```

**Single-flight is the point** — see
[cache-aside](../src/main/kotlin/com/example/kotlindp/patterns/production/cacheaside/). Ten
concurrent connections to one service must produce one lookup, not ten.

### Four things that bite

**Re-resolve on every reconnect, not just first connect.** Caching an address for the process
lifetime pins you to a dead pod. This is the single most common service-discovery bug.

**Stale-while-revalidate, not fail-on-stale.** When the registry is down, a last-known-good address
is almost always better than refusing to connect. This is the opposite of the usual TTL instinct, so
make it an explicit policy flag rather than an accident.

**Honour the record TTL** rather than inventing your own — a 30-second cache over a 5-second SRV
record is a slow failover you will debug at 3am.

**Treat resolution as a security boundary.** Name→address resolution is a redirect primitive: if a
registry entry can point a trusted service name at an attacker-controlled host, any credential
scoped to that *name* is sent to that *address*. Validate resolved hosts against an allowlist, and
bind tokens to the resolved origin rather than the logical name. The same applies to
`.well-known`-style discovery, which is attacker-influenced by construction.

---

## 10. Pagination and notifications

MCP list endpoints are cursor-paginated, which is exactly the
[lazy iterator](../src/main/kotlin/com/example/kotlindp/patterns/behavioral/iterator/) case — callers
should never see a page boundary, and pages they don't consume should never be fetched:

```kotlin
fun tools(): Flow<Tool> = flow {
    var cursor: String? = null
    do {
        val page = peer.request("tools/list", ListParams(cursor), timeout).decode<ToolsPage>()
        page.tools.forEach { emit(it) }
        cursor = page.nextCursor
    } while (cursor != null)
}
```

Notifications need the `StateFlow` vs `SharedFlow` distinction from
[observer](../src/main/kotlin/com/example/kotlindp/patterns/behavioral/observer/), and getting it
backwards silently loses data:

| Signal | Type | Why |
|---|---|---|
| Connection state | `StateFlow<SessionState>` | Always has a current value; late subscribers need it |
| `notifications/*` | `SharedFlow<Notification>` | Events; conflation would drop them |
| `progress` | `SharedFlow<Progress>` | Every tick matters for a progress bar |
| Cached tool list | `StateFlow<List<Tool>>` | State, invalidated by `tools/list_changed` |

The `list_changed` notifications are what make the caching decorator safe: cache aggressively, and
invalidate on the push rather than on a timer.

---

## 11. Multi-server aggregation

The host presents many servers to the model as one tool namespace — a
[composite](../src/main/kotlin/com/example/kotlindp/patterns/structural/composite/) with a collision
policy that must be decided explicitly:

```kotlin
class AggregateClient(private val clients: Map<ServiceName, McpClient>) {

    suspend fun allTools(): List<QualifiedTool> = clients.flatMap { (name, client) ->
        client.listTools().map { QualifiedTool(qualify(name, it.name), name, it) }
    }

    /** Prefixing is the boring, correct default. Silent last-wins is how a model calls the wrong tool. */
    private fun qualify(server: ServiceName, tool: ToolName) = ToolName("${server.value}__${tool.value}")
}
```

Use `supervisorScope` here, not `coroutineScope`: one unreachable server must not blank the entire
tool list. That choice is a product decision, not a technical one — see
[coroutines](../src/main/kotlin/com/example/kotlindp/patterns/kotlinidioms/coroutines/).

---

## 12. Testing strategy

The layering exists so that almost nothing needs a real process or socket:

| Layer | How to test it |
|---|---|
| Resolver chain | Fake resolvers returning fixed values; assert precedence and single-flight |
| Session state | The transition function is **pure** — one assertion per rule, no I/O |
| Peer correlation | In-process transport; assert timeouts remove pending entries |
| Bidirectional | Fake server that *sends* `sampling/createMessage` and asserts your reply |
| Pagination | Fake pages; assert page N+1 is never fetched when the caller takes N |
| Reconnect | Transport that fails on demand; assert re-resolution happened |

Inject the clock and the sleep function (as [retry](../src/main/kotlin/com/example/kotlindp/patterns/production/retry/)
does) so backoff and TTL tests run in milliseconds. **A resilience helper that can only be tested in
real time will not be tested.**

---

## 13. Pitfalls checklist

**Both models**

- [ ] `CancellationException` is rethrown, never swallowed
- [ ] Every outbound request has a timeout
- [ ] Tool `isError: true` reaches the model; protocol errors do not
- [ ] Resolved hosts are validated before credentials are sent
- [ ] Tool names are namespaced across servers
- [ ] One failing server doesn't blank the aggregate tool list
- [ ] Protocol version mismatch fails loudly, at registration rather than first call
- [ ] The pinned version constant is asserted against what goes on the wire
- [ ] Capability checks still happen — pinning the revision is not a capability guarantee
- [ ] `-32601 Method not found` is a modelled outcome, not an unexpected exception

**Session-based only**

- [ ] Message routing is symmetric — the server can send *you* requests
- [ ] `pending.remove(id)` happens in `finally`
- [ ] Timeouts send `notifications/cancelled`, not just local cancellation
- [ ] Re-resolution happens on reconnect, not only first connect
- [ ] Cached lists are invalidated by `list_changed`, not by a timer alone

**Stateless only**

- [ ] Resolution is cached with single-flight — every call goes through it
- [ ] `tools/call` is **not** retried blindly; only with an idempotency key
- [ ] The tool-list TTL is a deliberate staleness bound, not a default
- [ ] Servers requiring sampling or elicitation are detected and rejected at config time
- [ ] HTTP keep-alive is on, or you pay a TLS handshake per tool call
- [ ] Auth token refresh is handled per request, not per session

## 14. Patterns to leave out

Included here because recognising an unnecessary pattern is worth as much as applying a necessary
one:

- **Object Pool** — connections are long-lived and one-per-server. Pooling adds a validity protocol
  and lifecycle bugs for no gain. See
  [objectpool](../src/main/kotlin/com/example/kotlindp/patterns/production/objectpool/).
- **Memento** — there is no undo.
- **Interpreter** — the only candidate is RFC 6570 URI-template expansion for resource templates,
  which is a library call.
- **Builder** — named and default arguments cover client configuration, unless you want a nested
  multi-server DSL. See [builder](../src/main/kotlin/com/example/kotlindp/patterns/creational/builder/).
- **Visitor** — a sealed message hierarchy plus exhaustive `when` is strictly better here, because
  you own the hierarchy. See [visitor](../src/main/kotlin/com/example/kotlindp/patterns/behavioral/visitor/).
- **Mediator** — tempting for the host layer, but a `SystemMediator` coordinating every client and
  the model is a god object. Keep coordination in the agent loop.

### Additionally, under the stateless + latest-only constraints

- **State** — the session state machine collapses to `Configured` / `Failed`. Two states is an
  `enum`, not a sealed hierarchy. This is the pattern's own advice applied against itself: sealed
  states earn their place when states *carry different data*, and here they no longer do.
- **Observer** — nothing pushes, so there is no subject. Connection state is not a `StateFlow`
  because there is no connection.
- **Bridge** — with stdio impossible, one transport remains. Keep the interface only if you want an
  in-memory fake for tests; that is a testability argument, not a Bridge.
- **Correlation Identifier** — the HTTP response *is* the correlation.
- **Strategy / Adapter for protocol versions** — pinning to the latest revision replaces the
  per-version codec and the compatibility shim with a `const val` and one equality check (§2.2).

That is five patterns deleted by two deployment constraints rather than by any design decision, which
is the most useful thing this example demonstrates: **the right pattern set is a function of the
constraints, not of the domain.** The same client, run session-based against a mixed-revision fleet,
needs every one of them back.
