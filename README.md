# Parallel-News-Aggregator

A multithreaded Java application that processes large volumes of JSON news articles in parallel. Uses a fixed thread pool with chunk-based work distribution for scalable performance, lock-free aggregation via ConcurrentHashMap and AtomicInteger, and a custom hand-written JSON parser optimized for low memory overhead. Produces deterministic outputs including per-category/language article indexes, keyword frequency analysis for English articles, and aggregate statistics reports.
Built with: Java · ExecutorService · ConcurrentHashMap · AtomicInteger · Custom JSON Parser
