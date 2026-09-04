# Synechron — Java GenAI Developer Interview Preparation Guide

**Role:** Java Developer with GenAI | **Experience:** 3-5 Years | **Location:** Bangalore  
**Interview Date:** 5th Sep 2026 | **Format:** F2F at Global Technology Park, Bellandur  
**Interview Structure:** Technical Round → Techno-Managerial Round → HR Round

---

## PART 1: COMPANY OVERVIEW & INTERVIEW PROCESS

### About Synechron
- Global digital consulting firm, founded 2001, headquartered in New York
- ~12,800+ employees worldwide
- Primary domain: **Financial Services & Banking** (major clients include Morgan Stanley, AMEX, and other banking institutions)
- Bangalore office: Global Technology Park Tower B, Bellandur/Marathahalli area
- Glassdoor rating: **4.0/5** (Bangalore), work-life balance rated **3.8/5**
- Known for: Digital transformation, AI/ML solutions, blockchain, cloud consulting for banking/fintech

### Interview Process at F2F Drives
Based on multiple reviews, Synechron's F2F drive typically has:

1. **Round 1 — Technical (30-60 min):** Core Java, Spring Boot, coding problems, SQL queries, project discussion
2. **Round 2 — Techno-Managerial (30 min):** System design, architecture, project depth, SOLID principles, scenario-based questions
3. **Round 3 — HR (15-20 min):** CTC discussion, notice period, location preference, cultural fit
4. **Possible client round later:** After joining, you may face a client-side interview (e.g., Morgan Stanley, AMEX)

### Key Tips from Reviews
- Interviewers focus heavily on **hands-on experience** — be ready to discuss real projects
- **Coding questions** are always part of round 1 — practice Java 8 Streams problems
- They ask "how would you rate yourself in Java/Spring Boot/Microservices?" — be honest, they cross-question
- Some interviewers ask "bookish" theoretical questions, others prefer practical scenario-based ones
- Communication skills matter — especially for the managerial round
- Result often comes same day or within a week

---

## PART 2: CORE JAVA QUESTIONS (Most Frequently Asked at Synechron)

### Q1. HashMap vs TreeMap — When would you use each?

**HashMap** uses a hash table internally, provides O(1) average-time for get/put, allows one null key, and does not maintain any ordering. **TreeMap** uses a Red-Black tree, provides O(log n) for get/put, does NOT allow null keys, and maintains keys in **natural sorted order** (or via a custom Comparator).

Use HashMap when you need fast lookups and don't care about ordering. Use TreeMap when you need entries sorted by key — for example, building a range-based cache or maintaining a sorted leaderboard.

**Follow-up they may ask:** How does HashMap work internally?  
HashMap stores entries in an array of "buckets." The bucket index is determined by `hash(key) & (n-1)`. When two keys land in the same bucket (collision), they're stored as a linked list (or a balanced tree if the list exceeds 8 nodes, since Java 8). On `get()`, the hash locates the bucket, then `equals()` identifies the exact key.

---

### Q2. How do you detect and avoid Deadlocks?

**Detection:** Use `jstack <pid>` to get thread dumps and look for threads in BLOCKED state waiting on each other's locks. VisualVM and JConsole also detect deadlocks automatically. Programmatically, `ThreadMXBean.findDeadlockedThreads()` can detect them at runtime.

**Prevention strategies:**
- **Lock ordering:** Always acquire locks in a consistent global order across all threads
- **tryLock with timeout:** Use `ReentrantLock.tryLock(timeout, unit)` instead of synchronized — if the lock isn't available, back off
- **Avoid nested locks:** Minimize holding multiple locks simultaneously
- **Use concurrent utilities:** `ConcurrentHashMap`, `AtomicInteger`, `ReadWriteLock` reduce the need for explicit locking

**Example scenario:** Thread A holds Lock1 and waits for Lock2. Thread B holds Lock2 and waits for Lock1. Fix: Both threads acquire Lock1 first, then Lock2.

---

### Q3. Diamond Problem / Multiple Inheritance in Java

Java does not support multiple inheritance of classes (to avoid ambiguity). If class C extends both A and B, and both have a method `display()`, the compiler cannot decide which to call — this is the Diamond Problem.

**Java's solution:**  
- Use **interfaces** instead. A class can implement multiple interfaces.
- Since Java 8, interfaces can have **default methods**. If two interfaces have the same default method, the implementing class MUST override it to resolve the conflict.

```java
interface A { default void show() { System.out.println("A"); } }
interface B { default void show() { System.out.println("B"); } }
class C implements A, B {
    @Override
    public void show() { A.super.show(); } // Explicit resolution
}
```

---

### Q4. What is a Marker Interface? Give examples.

A marker interface is an interface with **no methods or fields**. It acts as a "tag" that tells the JVM or framework to treat objects of that type specially.

**Examples:**
- `Serializable` — tells JVM the object can be serialized to bytes
- `Cloneable` — tells JVM that `clone()` is allowed on this object
- `Remote` — marks an object for RMI

**Modern alternative:** Annotations (like `@Entity`, `@Deprecated`) have largely replaced marker interfaces since they can carry metadata and are more flexible.

---

### Q5. Why were default and static methods added to interfaces in Java 8?

**Default methods** allow library designers to add new methods to existing interfaces without breaking all implementations. For example, `forEach()` was added to `Iterable` as a default method — existing classes didn't need to change.

**Static methods** in interfaces let you place utility/helper methods directly inside the interface, eliminating the need for companion utility classes (like `Collections` alongside `Collection`).

---

### Q6. What is Method Hiding?

Method Hiding occurs when a subclass defines a **static** method with the same signature as a static method in its superclass. Unlike overriding (which is runtime polymorphism), method hiding is resolved at **compile time** based on the reference type, not the actual object.

```java
class Parent { static void greet() { System.out.println("Parent"); } }
class Child extends Parent { static void greet() { System.out.println("Child"); } }

Parent p = new Child();
p.greet(); // Prints "Parent" — resolved by reference type, not object
```

---

### Q7. How do you identify Memory Leaks in Java?

**Symptoms:** OutOfMemoryError, application slowing over time, GC running frequently but not freeing memory.

**Tools & techniques:**
- **Heap dumps:** Generate with `jmap -dump:live,format=b,file=heap.hprof <pid>`, analyze with Eclipse MAT or VisualVM
- **GC logs:** Enable with `-verbose:gc -Xlog:gc*` — look for heap not being reclaimed after Full GC
- **Profilers:** VisualVM, YourKit, JProfiler to see object retention graphs
- **Common causes:** Static collections that grow unbounded, unclosed resources (streams, connections), listeners/callbacks not deregistered, `ThreadLocal` not removed, inner classes holding references to outer class

---

### Q8. Design and implement a custom LRU Cache

```java
public class LRUCache<K, V> {
    private final int capacity;
    private final Map<K, V> cache;

    public LRUCache(int capacity) {
        this.capacity = capacity;
        // accessOrder=true makes LinkedHashMap order by access
        this.cache = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > capacity;
            }
        };
    }

    public V get(K key) { return cache.getOrDefault(key, null); }
    public void put(K key, V value) { cache.put(key, value); }
}
```

**Key insight:** `LinkedHashMap` with `accessOrder=true` automatically moves accessed entries to the end. Override `removeEldestEntry` to evict the least recently used when capacity is exceeded.

---

### Q9. Singleton Pattern — How can Reflection break it? How to prevent?

**Breaking with Reflection:**
```java
Constructor<Singleton> constructor = Singleton.class.getDeclaredConstructor();
constructor.setAccessible(true);
Singleton newInstance = constructor.newInstance(); // Creates a second instance!
```

**Prevention:**
1. **Enum-based Singleton:** `enum Singleton { INSTANCE; }` — JVM guarantees a single instance; reflection throws `IllegalArgumentException` on enum constructors
2. **Throw exception in constructor:** Check if instance already exists and throw `RuntimeException` in the private constructor
3. **readResolve()** for serialization attacks: Return the existing instance

---

### Q10. Java 8 Streams Coding: Find first non-repeating character

This is a **very commonly asked coding question at Synechron.**

```java
String input = "swiss";
Character result = input.chars()
    .mapToObj(c -> (char) c)
    .collect(Collectors.groupingBy(
        Function.identity(),
        LinkedHashMap::new,  // Preserves insertion order
        Collectors.counting()
    ))
    .entrySet().stream()
    .filter(entry -> entry.getValue() == 1L)
    .map(Map.Entry::getKey)
    .findFirst()
    .orElse(null);
// Result: 'w'
```

**Critical point:** Use `LinkedHashMap::new` as the map factory to maintain insertion order — without it, you can't guarantee which non-repeating character comes "first."

---

### Q11. Find the longest substring without repeating characters

```java
public static String longestUniqueSubstring(String s) {
    Map<Character, Integer> lastSeen = new HashMap<>();
    int start = 0, maxStart = 0, maxLen = 0;

    for (int end = 0; end < s.length(); end++) {
        char c = s.charAt(end);
        if (lastSeen.containsKey(c) && lastSeen.get(c) >= start) {
            start = lastSeen.get(c) + 1;
        }
        lastSeen.put(c, end);
        if (end - start + 1 > maxLen) {
            maxLen = end - start + 1;
            maxStart = start;
        }
    }
    return s.substring(maxStart, maxStart + maxLen);
}
// "abcabcbb" → "abc" (length 3)
```

Uses the **sliding window** technique — O(n) time, O(min(n, charset)) space.

---

### Q12. String permutations (all combinations)

```java
public static void permute(String str, int l, int r) {
    if (l == r) { System.out.println(str); return; }
    for (int i = l; i <= r; i++) {
        str = swap(str, l, i);
        permute(str, l + 1, r);
        str = swap(str, l, i); // backtrack
    }
}
```
For "ABC" → outputs ABC, ACB, BAC, BCA, CBA, CAB. Time complexity: O(n × n!)

---

### Q13. Multithreading: What is the difference between `wait()` and `sleep()`?

| Feature | `wait()` | `sleep()` |
|---|---|---|
| Class | `Object` | `Thread` |
| Lock release | **Yes**, releases the monitor lock | **No**, holds the lock |
| Wake-up | `notify()` / `notifyAll()` | After timeout expires |
| Usage context | Must be inside `synchronized` block | Anywhere |
| Purpose | Inter-thread communication | Pausing execution |

---

### Q14. What is the difference between `CountDownLatch` and `CyclicBarrier`?

**CountDownLatch:** A one-shot mechanism. Threads call `countDown()`, and one or more waiting threads proceed when the count reaches zero. Cannot be reused.

**CyclicBarrier:** Reusable. All participating threads call `await()` and block until all have arrived at the barrier. Then they all proceed. Can be reset and reused across cycles.

**Use case:** CountDownLatch for "wait until N tasks complete." CyclicBarrier for "all threads must reach this point before any can continue" (e.g., parallel computation phases).

---

## PART 3: SPRING BOOT & MICROSERVICES

### Q15. Why Spring Boot when we already have Spring?

Spring requires extensive XML/Java configuration — datasource, view resolvers, component scanning, dispatcher servlet, etc. Spring Boot eliminates this boilerplate via:

- **Auto-configuration:** Detects libraries on classpath and configures them automatically
- **Embedded server:** Tomcat/Jetty built-in — no WAR deployment needed
- **Starter dependencies:** `spring-boot-starter-web` bundles everything for a web app
- **Opinionated defaults:** Sensible defaults that work out of the box
- **Actuator:** Production-ready monitoring endpoints

---

### Q16. What is @Lazy in Spring Boot?

By default, Spring creates all singleton beans at startup (**eager initialization**). `@Lazy` tells Spring to initialize the bean only when it's **first requested/injected**.

**Use cases:**
- Improve application startup time when you have heavy beans that aren't immediately needed
- Break **circular dependencies** — if Bean A depends on B and B depends on A, marking one as `@Lazy` creates a proxy that defers actual initialization

**Caution:** Use sparingly — lazy beans can cause unexpected runtime errors if misconfigured, since the failure only shows when the bean is first accessed.

---

### Q17. How do you maintain two different databases in a Spring Boot application?

Configure two `DataSource` beans with `@Primary` on the default one. Each gets its own `EntityManagerFactory` and `TransactionManager`.

```java
@Configuration
@EnableJpaRepositories(basePackages = "com.app.db1.repo",
    entityManagerFactoryRef = "db1EntityManager",
    transactionManagerRef = "db1TransactionManager")
public class Db1Config {
    @Primary @Bean
    public DataSource db1DataSource() { ... }

    @Primary @Bean
    public LocalContainerEntityManagerFactoryBean db1EntityManager() { ... }
}
```

Repeat for `Db2Config` pointing to `com.app.db2.repo` with different `DataSource` properties. Each repository package maps to its own database.

---

### Q18. What is Spring Boot Profiling?

Profiles let you define **environment-specific configurations**. You create `application-dev.yml`, `application-prod.yml`, etc.

Activate via: `spring.profiles.active=dev` (in properties, env variable, or command-line `--spring.profiles.active=prod`).

Beans can be annotated `@Profile("dev")` so they're only created in that environment. This is used for switching databases, logging levels, external service URLs, feature flags, etc.

---

### Q19. How do you handle exceptions like timeouts when calling APIs?

- **For External APIs:** Use `try-catch` for immediate handling, but more importantly, use **Resilience4j** for Circuit Breaker (stops calling a failing service), Retry (automatic retries with backoff), and Timeout (caps call duration)
- **For Repositories:** Set `spring.jpa.properties.javax.persistence.query.timeout` or use `@QueryHints` to prevent hanging on locked database tables
- **Custom Exceptions:** Wrap technical errors in meaningful custom exceptions like `ExternalServiceUnavailableException` so the frontend receives clean error messages
- **Global handling:** Use `@ControllerAdvice` with `@ExceptionHandler` for centralized exception management

---

### Q20. Explain the N+1 problem in Hibernate and how to solve it.

**Problem:** You load a list of N parent entities, and for each one, Hibernate fires a separate query to load its children — resulting in 1 (parent) + N (children) queries.

```
SELECT * FROM department;          -- 1 query
SELECT * FROM employee WHERE dept_id = 1;  -- N queries
SELECT * FROM employee WHERE dept_id = 2;
...
```

**Solutions:**
- **JOIN FETCH:** `@Query("SELECT d FROM Department d JOIN FETCH d.employees")`
- **@EntityGraph:** `@EntityGraph(attributePaths = {"employees"})` on repository methods
- **@BatchSize(size=50):** Hibernate loads children in batches of 50 instead of one-by-one
- **Subselect fetching:** `@Fetch(FetchMode.SUBSELECT)` uses a subquery

---

### Q21. What are the key Microservices design patterns you've used?

- **API Gateway:** Single entry point (Spring Cloud Gateway / Zuul) for routing, auth, rate limiting
- **Service Discovery:** Eureka / Consul — services register themselves and discover each other dynamically
- **Circuit Breaker:** Resilience4j — prevents cascading failures by short-circuiting calls to unhealthy services
- **Config Server:** Spring Cloud Config — centralized externalized configuration
- **Saga Pattern:** Manage distributed transactions — Choreography (events) or Orchestration (central coordinator)
- **CQRS:** Separate read and write models for scalability
- **Event Sourcing:** Store state changes as events rather than current state

---

### Q22. Explain Spring Boot Actuator and how you use it in production.

Actuator provides **production-ready features**: health checks (`/actuator/health`), metrics (`/actuator/metrics`), environment info, thread dumps, HTTP trace, and custom endpoints.

In production: expose only `/health` and `/info` publicly; secure the rest. Integrate with Prometheus + Grafana for monitoring. Custom health indicators for database connectivity, external API health, queue depth.

---

### Q23. How does Spring Security Authentication work?

1. User submits credentials → `UsernamePasswordAuthenticationFilter` intercepts
2. Creates `UsernamePasswordAuthenticationToken` (unauthenticated)
3. Passes to `AuthenticationManager` → delegates to `AuthenticationProvider`
4. Provider uses `UserDetailsService` to load user, `PasswordEncoder` to verify password
5. On success, returns authenticated `Authentication` object → stored in `SecurityContextHolder`
6. For JWT: `OncePerRequestFilter` validates token on every request and sets context

---

### Q24. Explain IoC (Inversion of Control) in Spring.

In traditional programming, your code creates and manages its dependencies. With IoC, the **Spring container** creates objects, manages their lifecycle, and injects dependencies. Your code declares what it needs (via `@Autowired`, constructor injection), and Spring supplies it.

**Benefits:** Loose coupling, easier testing (inject mocks), centralized lifecycle management.

**Types of DI:** Constructor injection (preferred — makes dependencies explicit and supports immutability), Setter injection, Field injection (discouraged — harder to test).

---

## PART 4: SQL QUESTIONS

### Q25. Pagination query — Fetch 20 records starting from 5th row

```sql
SELECT first_name, last_name
FROM user_table
ORDER BY id
LIMIT 20 OFFSET 4;  -- Skip first 4 rows, then take 20
```

**Note:** OFFSET is zero-based in logic: to start from the 5th row, skip 4 rows (OFFSET 4).

---

### Q26. Find employees whose salary ≥ "Rita's" salary (subquery)

**Same table:**
```sql
SELECT Fname, Lname FROM Employee
WHERE Salary >= (SELECT Salary FROM Employee WHERE Fname = 'Rita');
```

**Salary in separate table:**
```sql
SELECT e.Fname, e.Lname
FROM Employee e
JOIN Salary s ON e.Eid = s.Eid
WHERE s.Salary >= (
    SELECT s2.Salary FROM Salary s2
    JOIN Employee e2 ON e2.Eid = s2.Eid
    WHERE e2.Fname = 'Rita'
);
```

---

### Q27. Find the 2nd highest salary

```sql
-- Method 1: LIMIT/OFFSET
SELECT DISTINCT Salary FROM Employee ORDER BY Salary DESC LIMIT 1 OFFSET 1;

-- Method 2: Subquery
SELECT MAX(Salary) FROM Employee
WHERE Salary < (SELECT MAX(Salary) FROM Employee);

-- Method 3: DENSE_RANK (handles duplicates)
SELECT Salary FROM (
    SELECT Salary, DENSE_RANK() OVER (ORDER BY Salary DESC) as rnk
    FROM Employee
) ranked WHERE rnk = 2;
```

---

### Q28. Difference between DELETE, TRUNCATE, and DROP?

| Feature | DELETE | TRUNCATE | DROP |
|---|---|---|---|
| Type | DML | DDL | DDL |
| WHERE clause | Yes | No | No |
| Rollback | Yes (logged) | Limited | No |
| Speed | Slow (row by row) | Fast (deallocates pages) | Fastest |
| Effect | Removes specific rows | Removes all rows, keeps structure | Removes table entirely |
| Triggers | Fires triggers | Does NOT fire triggers | N/A |

---

## PART 5: GENERATIVE AI / LLM QUESTIONS

### Q29. What is Generative AI and how does it differ from traditional AI?

Traditional AI focuses on **classification, prediction, and pattern recognition** — it identifies which category something belongs to or predicts a numerical outcome. Generative AI creates **new content** — text, images, code, audio — by learning patterns from training data and producing original outputs.

**Key distinction:** Traditional ML model classifies an email as spam/not-spam. A generative model writes an entirely new email based on a prompt.

**Core architectures:** Transformers (GPT, Claude, Gemini), GANs (image generation), Diffusion Models (Stable Diffusion), VAEs.

---

### Q30. What is a Large Language Model (LLM)? Name some you've worked with.

An LLM is a neural network (typically Transformer-based) trained on massive text corpora to understand and generate human-like text. They use the **next-token prediction** objective — given a sequence of tokens, predict the most probable next token.

**Major LLMs:**
- **OpenAI GPT-4 / GPT-4o** — via API or Azure OpenAI
- **Anthropic Claude** — known for long context windows and safety
- **Google Gemini** — multimodal capabilities
- **Meta Llama 3** — open-source, self-hostable
- **Mistral / Mixtral** — efficient open-source models

In a Java context, you typically interact with these via REST APIs, using libraries like **LangChain4j** or **Spring AI**.

---

### Q31. What is RAG (Retrieval-Augmented Generation)? How would you build one?

RAG combines an LLM with an external **knowledge retrieval system** so the model can answer questions using your private/up-to-date data instead of relying solely on its training data.

**Architecture:**
1. **Ingestion Pipeline:** Documents → chunk into segments → generate vector embeddings (using an embedding model like OpenAI `text-embedding-ada-002`) → store in a vector database (Pinecone, ChromaDB, Weaviate, PGVector)
2. **Query Pipeline:** User question → embed the question → similarity search in vector DB → retrieve top-K relevant chunks → inject chunks into the LLM prompt as context → LLM generates an answer grounded in those chunks

**Java implementation with Spring Boot:**
```java
// Using LangChain4j
EmbeddingStore<TextSegment> store = ChromaEmbeddingStore.builder().build();
EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
    .apiKey(apiKey).build();

ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
    .embeddingStore(store)
    .embeddingModel(embeddingModel)
    .maxResults(5)
    .build();

// AI Service with RAG
interface Assistant {
    @SystemMessage("Answer using provided context only")
    String answer(String question);
}
```

**Why RAG over fine-tuning?** RAG is cheaper, doesn't require retraining, can use real-time data, and you can update the knowledge base anytime by re-indexing documents.

---

### Q32. What is Prompt Engineering? What techniques do you use?

Prompt engineering is the art of designing inputs to guide an LLM toward producing accurate, relevant, and structured outputs.

**Key techniques:**
- **Zero-shot:** Directly ask the question with no examples
- **Few-shot:** Provide 2-3 examples of input-output pairs before the actual question
- **Chain-of-Thought (CoT):** Ask the model to "think step by step" — improves reasoning on complex tasks
- **System prompts:** Set the model's role and behavior ("You are a financial advisor...")
- **Output formatting:** Specify JSON, XML, or structured format in the prompt
- **Delimiters:** Use `###` or XML tags to clearly separate instructions from content

**In production:** Store prompts as templates with placeholders, version-control them, and A/B test different variants.

---

### Q33. What is LangChain / LangChain4j? How does it help?

**LangChain** is a framework for building LLM-powered applications. **LangChain4j** is the Java port (hit 1.0 GA in May 2025).

**Core abstractions:**
- **AI Services:** Declarative interfaces where you define behavior via annotations; LangChain4j handles the plumbing
- **Chains:** Sequence of steps — retrieve context → build prompt → call LLM → parse response
- **Memory:** Conversational memory to maintain chat history across turns
- **Tools/Function Calling:** Let the LLM invoke Java methods (database queries, API calls) as "tools"
- **RAG:** Built-in document loaders, splitters, embedding stores, and retrievers
- **Agents:** LLM decides which tools to use and in what order to accomplish a goal

**Spring Boot integration:**
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-spring-boot-starter</artifactId>
</dependency>
```

---

### Q34. What is an AI Agent? How is it different from a simple chatbot?

A **chatbot** follows a fixed flow — respond to user input using predefined rules or a single LLM call. An **AI Agent** has **autonomy** — it can:

- **Plan:** Break a complex goal into sub-tasks
- **Use tools:** Call APIs, query databases, search the web
- **Reason:** Decide which tool to use based on the current state
- **Iterate:** Evaluate results and take corrective actions

**Example:** A user asks "Find all overdue invoices and send a reminder email to each client." An agent would: (1) query the invoice database, (2) filter overdue ones, (3) draft personalized emails, (4) send them via an email API.

**In Java:** LangGraph (graph-based agent orchestration) or LangChain4j's agent capabilities handle tool selection and multi-step execution.

---

### Q35. What are Embeddings and Vector Databases?

**Embeddings** are numerical representations (dense vectors) of text, images, or other data. Semantically similar content has vectors that are close together in the vector space.

Example: "How do I reset my password?" and "I forgot my login credentials" would have similar embeddings, even though they share few words.

**Vector databases** (Pinecone, ChromaDB, Weaviate, Milvus, PGVector) are optimized for storing and searching these vectors using **similarity metrics** like cosine similarity, dot product, or Euclidean distance.

**In a RAG pipeline:** Documents are chunked → embedded → stored in a vector DB. At query time, the user's question is embedded → top-K similar chunks are retrieved → fed to the LLM as context.

---

### Q36. How do you integrate an LLM API into a Spring Boot application?

```java
@Service
public class AiService {
    private final RestTemplate restTemplate;

    public String chat(String userMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
            "model", "gpt-4",
            "messages", List.of(Map.of("role", "user", "content", userMessage)),
            "max_tokens", 1000
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
            "https://api.openai.com/v1/chat/completions", request, Map.class);

        // Parse response.getBody() to extract the generated text
        return extractContent(response.getBody());
    }
}
```

**Better approach:** Use **Spring AI** or **LangChain4j** starters for type-safe abstractions, automatic retries, streaming, and built-in support for multiple LLM providers.

---

### Q37. What is fine-tuning vs RAG? When do you choose which?

| Aspect | Fine-tuning | RAG |
|---|---|---|
| What it does | Modifies model weights with custom data | Retrieves external data at query time |
| Cost | High (GPU, training compute) | Low (only embedding + storage) |
| Data freshness | Stale once trained | Always current (update the index) |
| Best for | Changing model behavior/style/tone | Grounding answers in specific documents |
| Hallucination | Can still hallucinate | Reduced — answers cite retrieved context |
| Privacy | Data baked into model | Data stays in your vector DB |

**Rule of thumb:** Use RAG when you need answers grounded in specific, frequently changing documents. Use fine-tuning when you need the model to adopt a specific style, follow domain-specific instructions, or learn patterns not easily expressed in a prompt.

---

### Q38. How do you handle Hallucinations in LLM applications?

**Hallucination** = the model generates plausible-sounding but factually incorrect information.

**Mitigation strategies:**
1. **RAG:** Ground responses in retrieved, verified documents
2. **Temperature = 0:** Reduces randomness, makes output more deterministic
3. **System prompts:** "Only answer based on the provided context. If unsure, say 'I don't know.'"
4. **Citation enforcement:** Ask the model to cite which chunk it used for each claim
5. **Output validation:** Post-process the response — check facts against a database, run format validators
6. **Guardrails:** Use frameworks like Guardrails AI or NeMo Guardrails to enforce output constraints
7. **Human-in-the-loop:** For critical applications, flag low-confidence responses for human review

---

### Q39. What is LangGraph and how does it differ from LangChain?

**LangChain** is a general-purpose framework for building linear chains of LLM calls and tools.

**LangGraph** is a **graph execution engine** for building **stateful, multi-step agent workflows**. Each node in the graph represents a step (LLM call, tool invocation, conditional check), and edges define the flow — including loops, branches, and parallel paths.

**When to use LangGraph:** When your AI workflow needs conditional branching (if the LLM decides to search, go to search node; if it decides to answer, go to answer node), multi-agent coordination, or complex state management that a simple chain can't handle.

---

## PART 6: DATA STRUCTURES & ALGORITHMS

### Q40. Common coding patterns asked at Synechron

Based on reviews, prepare these patterns:

1. **Sliding Window:** Longest substring without repeating chars (covered above)
2. **HashMap counting:** First non-repeating character (covered above)
3. **String permutations:** Backtracking approach (covered above)
4. **Two pointer:** Finding pairs with a given sum in a sorted array
5. **Stack-based:** Balanced parentheses, next greater element
6. **Sorting/Searching:** Custom comparator sorting, binary search variations
7. **LinkedList:** Detect cycle (Floyd's algorithm), reverse a linked list

**Key tip from reviews:** Interviewers at Synechron don't ask LeetCode hard problems. Focus on medium-level problems with clean, well-explained Java 8 solutions.

---

## PART 7: DOCKER, CI/CD & CLOUD

### Q41. How do you Dockerize a Spring Boot application?

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/myapp.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Multi-stage build** (reduces image size):
```dockerfile
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

### Q42. Explain your CI/CD pipeline.

Typical pipeline: **Code push → Build (Maven/Gradle) → Unit tests → SonarQube analysis → Docker image build → Push to registry → Deploy to staging → Integration tests → Deploy to production**

Tools: Jenkins/GitHub Actions/Azure DevOps for orchestration, SonarQube for code quality, JaCoCo for code coverage, Docker/Kubernetes for deployment, Helm charts for K8s configuration.

---

## PART 8: HR ROUND PREPARATION

### Q43. Tell me about yourself / your current project

Structure: Current role → Project domain → Tech stack → Your specific contributions → Why you're looking for a change.

Be specific about your GenAI work — even if it's small. Mention any POCs, internal tools, or hackathon projects involving LLMs.

### Q44. Why Synechron?

"Synechron's focus on financial services and digital transformation, combined with its investment in AI and GenAI solutions, aligns with my career goal of building AI-powered applications in the fintech domain. The opportunity to work with enterprise-grade banking clients at the Bangalore office is exciting."

### Q45. CTC Negotiation Tips (from reviews)

- Synechron typically offers **25-40% hike** for lateral hires
- Be prepared with your exact current CTC breakdown (fixed + variable + bonuses)
- Expected CTC should be reasonable — research Glassdoor/AmbitionBox salary data for your role
- If you have competing offers, mention them — it strengthens your negotiation
- Clarify: Is it a **direct payroll** with Synechron or via a staffing agency? (Direct payroll is better for benefits)

### Q46. Key details to prepare beforehand

Have these ready before you walk in:
- Current CTC (exact breakdown)
- Expected CTC (with justification)
- Notice period / Last Working Day
- UAN number (for PF transfer verification)
- Any other offers in hand
- Self-rating for: Java, GenAI, Spring Boot, SQL, DSA, Microservices, Multithreading (out of 5)

---

## PART 9: QUICK REVISION CHECKLIST

### Day-Before Checklist

- [ ] Review HashMap internals, ConcurrentHashMap, TreeMap
- [ ] Practice 2-3 Java 8 Stream coding problems
- [ ] Review SOLID principles (they ask this in managerial round)
- [ ] Be ready to explain your project architecture (draw diagrams if needed)
- [ ] Review Spring Boot annotations: @Bean, @Component, @Service, @Repository, @Configuration, @Conditional
- [ ] Understand Circuit Breaker pattern with Resilience4j
- [ ] Know RAG pipeline end-to-end
- [ ] Understand LangChain4j / Spring AI basics
- [ ] Review Kafka basics (offsets, consumer groups, partitions) — they ask this
- [ ] Prepare 2-3 SQL queries (joins, subqueries, window functions)
- [ ] Know Docker basics and your CI/CD pipeline
- [ ] Print resume (2 copies) + carry Aadhar card
- [ ] Reach Global Technology Park Tower B by 9:45 AM

### Rating Guide (for the self-assessment form)

Be honest but strategic:
- Rate yourself **4/5** on your strongest skills (Java, Spring Boot)
- Rate yourself **3/5** on areas you're decent at (GenAI, Multithreading)
- Never rate **5/5** — it invites very deep cross-questioning
- Never rate below **2/5** — it raises concerns

---

*Good luck with your interview! Remember: Synechron interviewers value clear communication and practical experience over textbook answers. Explain concepts through real examples from your projects.*
