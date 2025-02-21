# Cache Coherency Protocols

## Introduction
Cache coherency protocols are essential for maintaining data consistency in multiprocessor systems where multiple caches may hold copies of the same memory location. These protocols help ensure that updates to a shared memory location are correctly reflected across all processors.

## Types of Cache Coherency Protocols
There are two main categories of cache coherency protocols:

### 1. **Directory-Based Protocols**
In directory-based cache coherency, a centralized directory keeps track of which processors have cached copies of a particular memory block. The directory is responsible for managing the state of each cache line and ensuring consistency. This approach is commonly used in large-scale multiprocessor systems.

#### **Examples of Directory-Based Protocols:**
- **Full-map Directory Protocol**: Maintains a complete list of all caches holding a copy of a memory block.
- **Limited Directory Protocol**: Uses a limited number of pointers to track caches that store copies of a block.
- **Sparse Directory Protocol**: Uses approximations to track shared blocks efficiently while reducing storage overhead.

### 2. **Snooping-Based Protocols**
Snooping protocols rely on a broadcast mechanism where all caches monitor (or "snoop") a shared bus to observe memory transactions. These protocols are more suitable for small to medium-sized multiprocessor systems.

#### **Types of Snooping Protocols:**
- **Write Invalidate Protocols**: When a processor writes to a shared block, it invalidates other copies in other caches.
  - **MESI Protocol**: A common write-invalidate protocol with four states: Modified, Exclusive, Shared, and Invalid.
  - **MOESI Protocol**: Extends MESI with an Owned state to allow dirty sharing.
  - **MESIF Protocol**: Adds a Forward state to optimize data sharing.
- **Write Update (Write Broadcast) Protocols**: Instead of invalidating copies, the new data is broadcast to all caches holding the block.
  - **Dragon Protocol**: Uses an update-based mechanism to keep cache copies consistent without invalidation.
  - **Firefly Protocol**: Similar to Dragon but optimized for certain architectures.

## Comparison of Protocols
| Feature | Directory-Based | Snooping-Based |
|---------|---------------|--------------|
| Scalability | High (suitable for large systems) | Low (limited due to bus contention) |
| Latency | Higher due to directory lookup | Lower for small systems |
| Traffic | Lower (point-to-point messages) | Higher (broadcast messages) |
| Complexity | Higher (requires directory management) | Lower (simpler logic) |

## Conclusion
Choosing the right cache coherency protocol depends on system architecture and scalability requirements. While snooping protocols are efficient for small-scale multiprocessors, directory-based protocols are better suited for larger distributed systems.


