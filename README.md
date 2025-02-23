# Processor Test Interface 

To Simulate our multicore processor writing in chisel, we will first generate verilog using sbt and after use verilator to generate .vcd files test for each instruction of our processor. We can after visualize the chrogram for debuging if neccessary with gtkwave .



# Implémentation d'un cache mémoire DirrectMapped programmable en chisel:

Implementation a cache using the MILK protocle (custom one), it as signal for the read, write and the request for the L1I and L1D processor cache.



# Implémentation du Protocole MSI

Le protocole **MSI (Modified, Shared, Invalid)** est un protocole de cohérence de cache utilisé dans les systèmes multiprocesseurs pour assurer la consistance des données entre les caches.

## Signaux Nécessaires

### 1. Signaux de Requête (Envoyés par les caches au bus ou au contrôleur de mémoire)
Ces signaux indiquent qu'un processeur souhaite effectuer une opération sur un bloc de cache, potentiellement modifiant son état de cohérence.

- **BusRd (Bus Read)** : Requête envoyée lorsqu'un processeur veut lire une donnée dans l'état **Invalid (I)**.
- **BusRdX (Bus Read Exclusive)** : Requête envoyée lorsqu'un processeur veut écrire sur un bloc, forçant son passage à l'état **Modified (M)**.
- **BusUpgr (Bus Upgrade)** : Requête envoyée lorsqu'un processeur possède le bloc en état **Shared (S)** mais souhaite le modifier sans recharger les données depuis la mémoire.

### 2. Signaux de Réponse (Envoyés par les autres caches ou la mémoire)
Ces signaux permettent de répondre aux requêtes ci-dessus et de maintenir la cohérence.

- **Flush** : Envoyé lorsqu'un cache possède une copie modifiée du bloc et doit l'écrire en mémoire.
- **FlushOpt (Flush Optionnel)** : Envoyé lorsqu'un cache peut fournir directement le bloc sans écriture en mémoire.
- **SharedResponse** : Indique qu'un autre cache détient déjà une copie du bloc en état **Shared (S)**.

### 3. Signaux d'Invalidation (Envoyés aux caches)
Ces signaux garantissent que les copies d'un bloc de données sont invalidées lorsque nécessaire.

- **Invalidate (Inv)** : Envoyé aux caches qui possèdent le bloc en **Shared (S)** lorsque qu'un autre processeur demande un accès exclusif.

### 4. Signaux d'Acknowledgment (Pour la Synchronisation)
Ces signaux confirment la réception des invalidations ou des transferts de données.

- **Ack (Acknowledgment)** : Confirme qu'un cache a bien invalidé ou mis à jour un bloc.
- **MemoryResponse** : Indique que la mémoire a fourni les données demandées à un processeur.

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


