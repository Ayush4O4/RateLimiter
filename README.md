# Distributed API Rate Limiter

A rate limiter built in Java and Spring Boot, implementing four classic rate-limiting algorithms behind a common interface, with support for distributed enforcement across multiple app instances using Redis.

Built as a solution to the [Coding Challenges](https://codingchallenges.fyi/challenges/challenge-rate-limiter) "Build Your Own Rate Limiter" challenge.

## Overview

Rate limiters protect APIs from being overwhelmed — by a bad actor, a buggy client, or just an unexpected traffic spike — by rejecting requests once a client exceeds a defined threshold. This project implements a rate limiter as a Spring Boot servlet `Filter` sitting in front of a protected endpoint, with the actual limiting logic swappable via a strategy interface.

Two endpoints are exposed:

| Endpoint     | Behavior                              |
|--------------|----------------------------------------|
| `/limited`   | Subject to whichever rate limiting strategy is active |
| `/unlimited` | Always responds, no limiting applied  |

## Algorithms Implemented

All four strategies implement a common `RateLimiterStrategy` interface (`boolean tryConsume(String clientId)`), so the active algorithm is a one-line change in the filter.

- **Token Bucket** — each client has a bucket with a fixed capacity that refills at a constant rate. Requests consume a token; if none are available, the request is rejected. Refill is calculated lazily from elapsed time rather than via a background thread.
- **Fixed Window Counter** — counts requests in fixed, non-overlapping time windows (e.g. per 60 seconds), resetting the count when a new window starts. Simple, but can allow up to 2x the intended limit right at a window boundary.
- **Sliding Window Log** — stores a timestamp per request and evicts anything outside the current window on each check. Fixes the boundary problem of fixed windows at the cost of storing every request timestamp.
- **Sliding Window Counter** — a hybrid approximation: keeps counts for the current and previous fixed windows and computes a weighted estimate based on how far into the current window the request landed. Smooths out the boundary spike without storing per-request logs.

All in-memory strategies use per-client state (keyed by IP) stored in a `ConcurrentHashMap`, with `synchronized` access to avoid race conditions under concurrent load.

## Distributed Rate Limiting (Redis)

The in-memory strategies above only work correctly for a single app instance — each instance has its own local map, so a client could get multiplied limits by hitting different instances behind a load balancer.

`RedisFixedWindowRateLimiter` solves this by moving the counter state into Redis:

- `INCR` atomically increments a per-client counter key (`ratelimit:<clientId>`)
- `EXPIRE` is set only on the first request in a window, so the key auto-resets without manual cleanup
- Because Redis executes commands atomically and is shared across instances, no in-process synchronization is needed — the concurrency problem is delegated to Redis itself

This was verified by running two application instances on different ports against the same Redis instance and confirming the combined request count — not the per-instance count — was what triggered rejection.

## Tech Stack

- Java, Spring Boot (Web, servlet `Filter`)
- Redis (via `spring-boot-starter-data-redis` / Lettuce client)
- Maven

## Getting Started

### Prerequisites

- JDK 21+
- Maven
- Redis running locally (for the distributed strategy):
  ```
  docker run -d -p 6379:6379 redis
  ```

### Configuration

`src/main/resources/application.properties`:
```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### Run

```bash
./mvnw spring-boot:run
```

The active strategy is selected in `RateLimiterFilter`'s constructor — swap between `TokenBucketRateLimiter`, `FixedWindowCounterRateLimiter`, `SlidingWindowLogRateLimiter`, `SlidingWindowCounterRateLimiter`, or `RedisFixedWindowRateLimiter` there to try each algorithm.

## Testing It

Basic burst test:
```bash
for i in $(seq 1 15); do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/limited
done
```

You should see `200` responses up to the configured limit, then `429 Too Many Requests` afterward.

For the distributed strategy, run a second instance on another port (`-Dserver.port=8081`) and alternate requests between both ports — the shared Redis-backed limit should hold across both.

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
└── redisBacked/
    └── RedisFixedWindowRateLimiter.java
```

## What This Project Covers

Beyond just implementing the algorithms, this project involved diagnosing and fixing real bugs along the way:

- A race condition in concurrent token consumption, found via concurrent load testing
- A boundary-burst flaw in fixed window counting, confirmed by comparing behavior against sliding window log
- An off-by-one window-shift bug and an infinite loop in the sliding window counter's idle-client handling
- Spring bean wiring issues (autowiring a per-client object as a singleton bean; ambiguous constructors from Lombok)
- An IPv4 vs IPv6 loopback mismatch when keying per-client state

## License

MIT
