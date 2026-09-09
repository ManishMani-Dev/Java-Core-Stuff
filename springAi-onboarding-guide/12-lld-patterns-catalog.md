# 12 · LLD Pattern Catalog — One Page, Real File Paths

A revision sheet. Every row points at working code you can open. Use it two ways:
**forward** ("I need to solve X, what did Spring AI do?") and **backward** ("I'm reading
this class, what pattern am I looking at?").

---

## 1. Creational

| Pattern | File | The variation worth noting |
|---|---|---|
| **Builder** | `Document.Builder`, `Prompt.Builder`, `ChatClientRequest.Builder`, `SearchRequest.Builder` | `Assert.state` in `build()` (not `Assert.notNull`), because failure is invalid *state*, not a bad argument |
| **Self-referential generic builder (CRTP)** | `ChatOptions.Builder<B extends Builder<B>>`, `AbstractVectorStoreBuilder<T extends Builder<T>>`, `ToolCallingChatOptions.Builder<B>` | Inherited setters return the **subtype**, so fluent chains survive inheritance |
| **Copy-on-write derivation (`mutate()`)** | `Prompt`, `ChatResponse`, `ChatClientRequest`, `ChatClientResponse`, `Query`, `ChatOptions` | Returns a Builder pre-populated from `this` — a codebase-wide convention |
| **Static factory** | `ToolCallbackProvider.from(...)`, `ChatClient.create(...)`, `McpToolNamePrefixGenerator.noPrefix()`, `ToolDefinition.builder()` | Names express intent; interfaces carry their own factories |
| **Abstract Factory / Provider** | `ToolCallbackProvider`, `SyncMcpToolCallbackProvider`, `MethodToolCallbackProvider` | Bulk, *dynamic* discovery — the set can change at runtime |

## 2. Structural

| Pattern | File | The variation worth noting |
|---|---|---|
| **Adapter** | `MethodToolCallback` (reflection), `FunctionToolCallback` (lambda), `SyncMcpToolCallback` (network), every `models/*` module | Three wildly different sources, one `ToolCallback` interface, because the interface speaks the wire format |
| **Bridge** | `Model<TReq,TRes>` — modality × provider | 6 modalities × 15 providers without 90 classes |
| **Facade** | `ChatClient` over `ChatModel`; `ToolCallingManager` over resolver/executor/limits | The facade uses only public API — the honesty test |
| **Composite** | `CompositeResponseTextCleaner`, `DelegatingToolCallbackResolver` | New quirk = new small class, not a new `if` |
| **Decorator** | Every `Advisor` wraps the rest of the chain; `AbstractObservationVectorStore` wraps `doAdd`/`doDelete`/`doSimilaritySearch` | Observability added without touching 22 stores |
| **Marker interface** | `ModelOptions`, `ResultMetadata`, `MemoryAdvisor`, `ToolAdvisor` | Two distinct uses: type anchor for unknowable extension, and letting a framework reason about structure |
| **Controlled escape hatch** | `VectorStore.getNativeClient()` returning `Optional<T>` | Portability abstractions must leak *somewhere*; make it opt-in and greppable |

## 3. Behavioural

| Pattern | File | The variation worth noting |
|---|---|---|
| **Chain of Responsibility** | `DefaultAroundAdvisorChain` + `Deque`; `DelegatingToolCallbackResolver` | Termination is an ordinary link (`ChatModelCallAdvisor` at `LOWEST_PRECEDENCE`), not an `if (isLast)` |
| **Template Method** | `BaseAdvisor.adviseCall/adviseStream`, `AbstractObservationVectorStore`, `AbstractFilterExpressionConverter`, `TextSplitter.splitText`, `ToolCallingAdvisor.doBeforeCall` | The hook should know as little as possible — `splitText(String)→List<String>` is the gold standard |
| **Strategy** | `BatchingStrategy`, `IdGenerator`, `ToolCallResultConverter`, `ToolExecutionExceptionProcessor`, `ChatMemory`, `FilterExpressionConverter`, every RAG stage, `ObservationConvention` | Also injected via annotation: `@Tool(resultConverter = ...)` |
| **Visitor / Interpreter** | `Filter` AST + `AbstractFilterExpressionConverter` | One AST, 20+ back ends, plus `SimpleVectorStoreFilterExpressionEvaluator` which *evaluates* instead of compiling |
| **Pipes and Filters** | ETL (`DocumentReader/Transformer/Writer`); RAG (6 stage interfaces) | Every stage extends a `java.util.function` type — composability for free |
| **Command** | `ToolCallback` | A named, parameterised, deferred, remotely-executable invocation |
| **Repository** | `ChatMemoryRepository` | Spring Data naming borrowed wholesale for zero-cost familiarity |
| **Blackboard** | `ChatClientRequest.context`, `Query.context`, `ToolContext` | Type safety traded for decoupling — mitigated by **published key constants** |
| **Observer / events** | `McpToolsChangedEvent` | Runtime-mutable capability sets |
| **Null Object** | `ObservationRegistry.NOOP`, `NoopApiKey`, `EmptyUsage`, `InMemoryChatMemoryRepository` | Eliminates null checks *and* gives you a zero-infrastructure test double |
| **Scatter–gather** | `QueryExpander` → parallel retrieval → `DocumentJoiner` | Fan-out and fan-in are separate, named roles |
| **Circuit breaker / bounded loop** | `ToolCallLimits`, `ToolCallLimitBehavior`, `ToolCallLimitExceededException` | The loop bound is a domain object with a policy, not a constant |

## 4. Module & API-level

| Principle | Evidence |
|---|---|
| **Dependency Inversion at module scale** | Core has zero Boot dependencies; enforced by `maven-enforcer-plugin` |
| **Open/Closed at module scale** | Adding a provider touches zero core classes (chapter 11 §6) |
| **Interface Segregation** | `VectorStoreRetriever` split from `VectorStore`; `Model` split from `StreamingModel`; `CallAdvisor` split from `StreamAdvisor` |
| **Executable architecture** | `bannedDependencies`, NullAway, spring-javaformat, checkstyle — architecture the build can check |
| **Type-level constraints** | Low- vs. high-cardinality observation key enums; step-builder interfaces that make illegal call orders uncompilable |
| **Backward-compatible extension** | `default String call(String, ToolContext)` added without breaking implementors |
| **Secure by default** | Prompt/completion content logging off unless explicitly enabled; `PII_MARKER` for PII |
| **Standard adoption over invention** | OpenTelemetry GenAI attribute names; Spring Data method naming; `java.util.function` types |

## 5. Recurring idioms — the Spring AI dialect

Learn these six and you can read any file in the repo:

**1 · Extend a JDK functional interface, add a domain-named default.**
```java
public interface DocumentTransformer extends Function<List<Document>, List<Document>> {
    default List<Document> transform(List<Document> docs) { return apply(docs); }
}
```
Also: `DocumentReader`, `DocumentWriter`, `QueryTransformer`, `QueryExpander`,
`DocumentRetriever`, `DocumentJoiner`, `DocumentPostProcessor`, `QueryAugmenter`,
`McpToolFilter`, `ToolExecutionEligibilityChecker`.

**2 · `mutate()` returns a pre-populated Builder.** Immutable types with ergonomic edits.

**3 · `do*` is the Template Method hook.** `doAdd`, `doDelete`, `doSimilaritySearch`,
`doExpression`, `doKey`, `doBeforeCall`, `splitText`.

**4 · `Assert.notNull` in constructors, `Assert.state` in builders.**
`IllegalArgumentException` for bad parameters, `IllegalStateException` for incomplete
state — a distinction the whole codebase honours.

**5 · `ObjectProvider<T>` for optional Spring collaborators**, with
`getIfUnique(() -> SOME_NOOP)` supplying a Null Object.

**6 · Every new package gets a `package-info.java` with `@NullMarked`.** Packages are not
hierarchical; there is no inheritance to rely on.

## 6. Five design decisions worth being able to defend

Interview-grade questions with real answers in this codebase:

1. **Why does `ToolCallback.call` take and return `String`?** Because the wire protocol is
   JSON text, so an MCP tool (already JSON) and a Java method (needs binding) implement the
   same interface with no shared serialisation dependency. (Ch. 05, 09)
2. **Why does the blocking `ChatModel` extend the streaming `StreamingChatModel`?**
   Consumer simplicity over strict ISP: any `ChatModel` can be passed to code that wants a
   `Flux`, with no `instanceof` or intersection types. (Ch. 03)
3. **Why is the tool-calling loop in an advisor rather than in each `ChatModel`?** Because
   the same control flow appeared in ~15 providers, and duplicated control flow is a sign
   the responsibility belongs to a collaborator, not to the implementations. (Ch. 05)
4. **Why an AST for filters instead of a `Map<String,Object>`?** Because 20+ target query
   languages need one source of truth that is parseable, printable, transformable, and
   testable without a database. (Ch. 06)
5. **Why are auto-config dependencies `optional=true`?** So the module has zero classpath
   impact when the underlying library is absent, keeping `@ConditionalOnClass` honest —
   at the cost of requiring starters for convenience. (Ch. 11)

## 7. Anti-patterns Spring AI deliberately avoids

Useful as a checklist against your own code:

| Avoided | Instead | Where to see it |
|---|---|---|
| God object with feature flags | Chain of Responsibility | `ChatClient` + advisors |
| Copy-pasted control flow across implementations | Hoist into a collaborator | `ToolCallingAdvisor` |
| `Map<String,Object>` as a public API contract | A typed AST | `Filter` |
| Magic numbers bounding a loop | A policy object | `ToolCallLimits` |
| Reinventing JDK interfaces | Extend them | ETL and RAG stages |
| Cross-module conventions as string literals | Shared constants | `SpringAIModelProperties`, `ChatMemory.CONVERSATION_ID` |
| Nullability in Javadoc prose | Nullability in the type system | JSpecify + NullAway |
| Architecture rules in a wiki | Architecture rules in the build | `bannedDependencies` |
| A leak-proof abstraction that forces forks | A marked escape hatch | `getNativeClient()` |
| Sensitive features on by default | Opt-in via configuration | Content observation handlers |

## 8. A self-test

Answer without looking. If you can do all ten, you can review a Spring AI PR.

1. Where would you add a new chat provider, and which existing files must you edit?
2. What is the order value of `MessageChatMemoryAdvisor`, and why does it matter relative
   to `ToolCallingAdvisor`?
3. Why does `AdvisorChain.copy(after)` exist?
4. What are the three invariants `MessageWindowChatMemory` protects when trimming?
5. Why does `DocumentJoiner` take `Map<Query, List<List<Document>>>`?
6. Which two enums does `ChatModelObservationDocumentation` split key names into, and why?
7. What breaks if you forget `package-info.java` in a new package?
8. What breaks if you forget the `.imports` entry for a new auto-configuration?
9. Why is `getNativeClient()` an `Optional`?
10. Name three places the Template Method pattern appears, and the hook method in each.

Next: [13 · Contribution Playbook](13-contribution-playbook.md).
