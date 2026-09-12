# Concert Ticket Booking System

A small Java concurrency exercise. Twenty-five client threads share one
in-memory `BookingService`, randomly browse and reserve seats, then buy or cancel
their reservation. The default stress run uses 20 rounds, each with fresh concerts
and up to 100 attempts per client. Change the settings in `src/Main.java` to
adjust the workload.

## Run

Use Java 23 (the existing IntelliJ project SDK). Run `Main` in IntelliJ, or from
PowerShell in the project directory:

```powershell
New-Item -ItemType Directory -Force out | Out-Null
javac -d out src/*.java
java -cp out Main
```

No dependencies or build tool are required.

The unsynchronized starter is expected to print invariant failures and exit with
an `AssertionError`. Checks are always enabled; no `-ea` flag is required.

## Contention workload

- All clients wait at a start gate so requests begin together.
- There are only four seats per concert. Most requests target the first concert
  and its first available seat; the remaining requests choose randomly.
- Clients immediately try to buy or cancel after reserving, without console
  logging or sleeps between requests. Cancellation is chosen 75% of the time
  to keep seats available for further contention.
- Fresh concerts each round give the clients repeated chances to collide before
  everything sells out. Inventory is replaced only after all clients have joined.
- The service optionally pauses for 1 ms **after a successful state check and
  before its update** in `reserve`, `buy`, and `cancel`. This simulates processing
  time and makes existing check-then-act races much easier to observe. It does
  not force an invalid result: locking the full operation also protects this
  window. Set `raceWindowNanos` to `0` in `Main` to disable the pause; the ordinary
  one-argument `BookingService` constructor also disables it.

For example, two clients can both see a free seat and pause. One reserves and
starts buying while the other's stale reservation check still allows it to
overwrite ownership. Both purchases can then pass their checks before either
finishes, producing two successful sales for one seat.

## Starting behavior

- Seat IDs run from 1 through the concert's capacity.
- Listings return a new list of free seat IDs.
- `reserve` succeeds only for a free seat.
- `buy` and `cancel` succeed only for the client holding the reservation.
- Buying is final; cancellation only releases a reservation.
- These rules work sequentially. The service is deliberately **unsynchronized**,
  so concurrent checks and updates can violate them.
- Reservations currently last until purchase or cancellation; timed expiry is
  part of the exercise. Clients cancel any remaining hold when they finish.
- `transferSeat` is an exercise stub and is not called by the simulation.

`Concert.soldCount` and each seat's `saleCount` count successful purchases using
`AtomicInteger`s. Their increments are safe, but booking operations are not.
After joining all clients, each round checks purchases against capacity and sold
seat counts, detects seats sold more than once, compares per-seat sales with
final state, and checks for leftover reservations. Output happens after the
clients finish so logging does not serialize their requests. A clean stress run
still does **not** prove correctness: races depend on scheduling.

## Two-hour exercise

1. **20 minutes:** Explore the model and naive service. Run the simulation and
   inspect the invariant failures. Compare results with the optional pause on
   and off, and with different client counts.
2. **30 minutes:** Make mutations atomic. Add per-concert `ReadWriteLock`s so
   listings can run concurrently. Compare fair and unfair locks.
3. **25 minutes:** Implement `transferSeat` as a swap of the complete seat state
   and ownership. Decide how sales counters should behave when sold seats move.
   Try opposite-direction transfers, reproduce deadlock, then fix lock ordering.
4. **25 minutes:** Add reservation deadlines and a `ScheduledExecutorService`
   reaper for five-second holds. Coordinate expiry with purchases, cancellation,
   and new reservations. Shut down the scheduler when the simulation ends.
5. **20 minutes:** Add `waitForSeat` with a `Condition`, then extend the stress
   workload to cover transfers, expiry, and waiting as well. Update the checks
   for the chosen transfer semantics. Remember that a condition wait needs a
   loop and notifications when a seat becomes free.

Optional extension: route client requests through a `BlockingQueue` and worker
pool. Keep everything in memory; networking and persistence are out of scope.
