# PgVector Local Metadata Example Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a new PGVector example that uses local Docker connection settings and metadata-based filtering.

**Architecture:** Create a new Java example class by copying the metadata example and replacing the Testcontainers connection with environment-variable-based local PG connection (same as the local PG example). Keep metadata filtering flow unchanged.

**Tech Stack:** Java, LangChain4j, PGVector

---

### Task 1: Create new local metadata example class

**Files:**
- Create: `pgvector-example/src/main/java/PgVectorEmbeddingStoreLocalPgMetadataExample.java`

**Step 1: Write the class skeleton**

```java
public class PgVectorEmbeddingStoreLocalPgMetadataExample {

    public static void main(String[] args) {
    }
}
```

**Step 2: Add imports and local connection configuration**

```java
String host = envOrDefault("PGHOST", "localhost");
int port = Integer.parseInt(envOrDefault("PGPORT", "5434"));
String database = envOrDefault("PGDATABASE", "postgres");
String user = envOrDefault("PGUSER", "postgres");
String password = envOrDefault("PGPASSWORD", "postgres");
```

**Step 3: Wire embedding model and store**

```java
EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

EmbeddingStore<TextSegment> embeddingStore = PgVectorEmbeddingStore.builder()
        .host(host)
        .port(port)
        .database(database)
        .user(user)
        .password(password)
        .table("test_db")
        .dimension(embeddingModel.dimension())
        .build();
```

**Step 4: Add metadata segments, embeddings, and filtered searches**

```java
TextSegment segment1 = TextSegment.from("I like football.", Metadata.metadata("userId", "1"));
Embedding embedding1 = embeddingModel.embed(segment1).content();
embeddingStore.add(embedding1, segment1);

TextSegment segment2 = TextSegment.from("I like basketball.", Metadata.metadata("userId", "2"));
Embedding embedding2 = embeddingModel.embed(segment2).content();
embeddingStore.add(embedding2, segment2);

Embedding queryEmbedding = embeddingModel.embed("What is your favourite sport?").content();

Filter onlyForUser1 = metadataKey("userId").isEqualTo("1");
EmbeddingSearchRequest embeddingSearchRequest1 = EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmbedding)
        .filter(onlyForUser1)
        .build();
EmbeddingSearchResult<TextSegment> embeddingSearchResult1 = embeddingStore.search(embeddingSearchRequest1);
EmbeddingMatch<TextSegment> embeddingMatch1 = embeddingSearchResult1.matches().get(0);
System.out.println(embeddingMatch1.score());
System.out.println(embeddingMatch1.embedded().text());

Filter onlyForUser2 = metadataKey("userId").isEqualTo("2");
EmbeddingSearchRequest embeddingSearchRequest2 = EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmbedding)
        .filter(onlyForUser2)
        .build();
EmbeddingSearchResult<TextSegment> embeddingSearchResult2 = embeddingStore.search(embeddingSearchRequest2);
EmbeddingMatch<TextSegment> embeddingMatch2 = embeddingSearchResult2.matches().get(0);
System.out.println(embeddingMatch2.score());
System.out.println(embeddingMatch2.embedded().text());
```

**Step 5: Add `envOrDefault` helper**

```java
private static String envOrDefault(String name, String defaultValue) {
    String value = System.getenv(name);
    return (value == null || value.trim().isEmpty()) ? defaultValue : value;
}
```

**Step 6: Commit**

```bash
git add pgvector-example/src/main/java/PgVectorEmbeddingStoreLocalPgMetadataExample.java
git commit -m "Add local pgvector metadata example"
```

### Task 2: (Optional) Manual verification

**Files:**
- None (manual run)

**Step 1: Run the example**

Run:
```bash
mvn -pl pgvector-example -am -DskipTests exec:java \
  -Dexec.mainClass=PgVectorEmbeddingStoreLocalPgMetadataExample
```

Expected: Two outputs of `score` and the corresponding text for user 1 and user 2.

**Step 2: Note if verification was skipped**

If the environment isn’t available, record that manual verification was not run.
