# Distributed API Rate Limiter

A rate limiter built in Java and Spring Boot, implementing four classic rate-limiting algorithms behind a common interface, with support for distributed enforcement across multiple app instances using Redis.

Built as a solution to the [Coding Challenges](https://codingchallenges.fyi/challenges/challenge-rate-limiter) "Build Your Own Rate Limiter" challenge.

## Overview

Rate limiters protect APIs from being overwhelmed (by a bad actor, a buggy client, or an unexpected traffic spike) by rejecting requests once a client exceeds a defined threshold. This project implements a rate limiter as a Spring Boot servlet `Filter` sitting in front of a protected endpoint, with the limiting logic swappable via a strategy interface.

Two endpoints are exposed:

| Endpoint     | Behavior                                              |
| ------------ | ----------------------------------------------------- |
| `/limited`   | Subject to whichever rate limiting strategy is active |
| `/unlimited` | Always responds, no limiting applied                  |

## Algorithms Implemented

All strategies implement a common `RateLimiterStrategy` interface (`boolean tryConsume(String clientId)`), so the active algorithm is a one-line change in the filter.

- **Token Bucket**: each client has a bucket with a fixed capacity that refills at a constant rate. Requests consume a token; if none are available, the request is rejected. Refill is calculated lazily from elapsed time rather than via a background thread.
- **Fixed Window Counter**: counts requests in fixed, non-overlapping time windows (e.g. per 60 seconds), resetting the count when a new window starts. Simple, but can allow up to 2x the intended limit right at a window boundary.
- **Sliding Window Log**: stores a timestamp per request and evicts anything outside the current window on each check. Fixes the boundary problem of fixed windows at the cost of storing every request timestamp.
- **Sliding Window Counter**: a hybrid approximation that keeps counts for the current and previous fixed windows and computes a weighted estimate based on how far into the current window the request landed. Smooths out the boundary spike without storing per-request logs.

All in-memory strategies keep per-client state (keyed by IP) in a `ConcurrentHashMap`, with `synchronized` access to avoid race conditions under concurrent load.

## Distributed Rate Limiting (Redis)

The in-memory strategies only work correctly for a single app instance: each instance has its own local map, so a client could get multiplied limits by hitting different instances behind a load balancer.

Two Redis-backed fixed window implementations move the counter state into Redis:

### `RedisFixedWindowRateLimiter` (INCR + EXPIRE)

- `INCR` atomically increments a per-client counter key (`ratelimit:<clientId>`)
- `EXPIRE` is set only on the first request in a window, so the key auto-resets without manual cleanup
- Because Redis executes commands atomically and is shared across instances, no in-process synchronization is needed

**Known limitation:** `INCR` and `EXPIRE` are two separate commands. If the app crashes or the connection drops between them on the first request of a window, the key is left with no TTL (`TTL` returns `-1`). The counter then never resets, and that client is blocked permanently once the count passes the limit.

### `RedisLuaFixedWindowRateLimiter` (atomic, recommended)

Runs `INCR` and `EXPIRE` inside a single Lua script, which Redis executes as one indivisible unit:

```lua
local c = redis.call('INCR', KEYS[1])
if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
return c
```

- Both commands happen or neither does, so the no-TTL lockout state cannot occur
- The first request of a window costs one network round trip instead of two
- Normal (non-first) requests cost the same as the plain version; see benchmarks below

Distributed behavior was verified by running two application instances on different ports against the same Redis instance and confirming that the combined request count, not the per-instance count, triggered rejection.

## Benchmarks

Latency of a single `tryConsume` call, measured directly on the Java class (no HTTP), fixed window algorithm, very high limit so every call takes the "allowed" path, 20,000 warm-up calls discarded, then 10,000 measured calls, 3 runs each (mean of the 3 run averages shown).

| Variant                                   | Avg latency per call | Round trips (normal request) |
| ----------------------------------------- | -------------------- | ---------------------------- |
| In-memory fixed window                    | ~0.21 µs             | 0                            |
| Redis `INCR` + `EXPIRE` limiter           | ~1,730 µs            | 1                            |
| Raw Redis `INCR` (no limiter logic)       | ~1,740 µs            | 1                            |
| Redis Lua limiter                         | ~1,780 µs            | 1                            |

**Findings**

- Shared Redis state costs roughly four orders of magnitude more per call than in-process state (~8,000x in this environment).
- The limiter's own logic adds almost nothing: it is within run-to-run noise (about 40-100 µs) of a single raw `INCR`. Cost is dominated by the network round trip, so reducing round trips per request matters more than optimizing Java code.
- On normal requests, the Lua script is not faster than the plain version. Its benefit is correctness (atomicity), not latency.

**Environment caveat:** measured with Java on Windows and Redis in a Docker Desktop (WSL2) container. A bare `redis-cli --latency` inside the container averaged ~0.47 ms per round trip, and a native Redis on localhost is typically far lower, so the absolute Redis numbers are inflated by this setup. The ratios and the round-trip reasoning are the meaningful results; absolute figures will differ on other machines.

## Tech Stack

- Java, Spring Boot (Web, servlet `Filter`)
- Redis (via `spring-boot-starter-data-redis` / Lettuce client)
- Maven

## Getting Started

### Prerequisites

- JDK 21+
- Maven
- Redis running locally (for the distributed strategies):

  ```
  docker run -d --name my-redis -p 6379:6379 redis
  ```

### Configuration

`src/main/resources/application.properties`:

```
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### Run

```
./mvnw spring-boot:run
```

The active strategy is selected in `RateLimiterFilter`'s constructor. Swap between `TokenBucketRateLimiter`, `FixedWindowCounterRateLimiter`, `SlidingWindowLogRateLimiter`, `SlidingWindowCounterRateLimiter`, `RedisFixedWindowRateLimiter`, or `RedisLuaFixedWindowRateLimiter` there to try each one.

## Testing It

Basic burst test:

```
for i in $(seq 1 15); do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/limited
done
```

You should see `200` responses up to the configured limit, then `429 Too Many Requests` afterward.

For the distributed strategies, run a second instance on another port (`-Dserver.port=8081`) and alternate requests between both ports. The shared Redis-backed limit should hold across both.

To observe the no-TTL failure state the Lua version prevents:

```
docker exec my-redis redis-cli INCR demo-key
docker exec my-redis redis-cli TTL demo-key     # prints -1: key exists, never expires
docker exec my-redis redis-cli DEL demo-key
```

### Running the benchmarks

Start Redis, then run `LimiterBenchmark` (Redis limiter vs. raw `INCR` vs. Lua limiter) and `InMemoryBenchmark` (in-memory baseline) as plain Java `main` programs.

## Project Structure

```
src/main/java/com/ayush/RateLimiter/
├── RateLimiterFilter.java              # Servlet filter, entry point for enforcement
├── RateLimiterStrategy.java            # Common interface for all algorithms
├── TockenBucket.java / TokenBucketRateLimiter.java
├── FixedWindowCounter.java / FixedWindowCounterRateLimiter.java
├── slidingWindowLog/
│   ├── SlidingWindowLog.java
│   └── SlidingWindowLogRateLimiter.java
├── slidingWindowCounter/
│   ├── SlidingWindowCounter.java
│   └── SlidingWindowCounterRateLimiter.java
├── redisBacked/
│   ├── RedisFixedWindowRateLimiter.java      # INCR + EXPIRE
│   └── RedisLuaFixedWindowRateLimiter.java   # atomic Lua script
└── benchmark/
    ├── InMemoryBenchmark.java
    └── LimiterBenchmark.java
```

## What This Project Covers

Beyond just implementing the algorithms, this project involved diagnosing and fixing real bugs along the way:

- A race condition in concurrent token consumption, found via concurrent load testing
- A boundary-burst flaw in fixed window counting, confirmed by comparing behavior against sliding window log
- An off-by-one window-shift bug and an infinite loop in the sliding window counter's idle-client handling
- A non-atomic `INCR` + `EXPIRE` gap that could leave a counter key with no TTL, fixed with a Lua script
- Spring bean wiring issues (autowiring a per-client object as a singleton bean; ambiguous constructors from Lombok)
- An IPv4 vs IPv6 loopback mismatch when keying per-client state

## Future Work

- Define behavior when Redis is unreachable (configurable fail-open vs. fail-closed)
- Redis-backed sliding window using a Lua script
- Key by authenticated user or API key instead of IP

## License

MIT
