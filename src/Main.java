import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        int clientCount = 500;
        int iterationsPerClient = 500;
        int rounds = 20;
        long raceWindowNanos = 1_000_000; // 1 ms of simulated work; set to 0 to disable.
        int failedRounds = 0;

        System.out.printf("Running %d rounds with %d clients and a %.1f ms race window.%n",
                rounds, clientCount, raceWindowNanos / 1_000_000.0);
        for (int round = 1; round <= rounds; round++) {
            if (!runRound(round, clientCount, iterationsPerClient, raceWindowNanos)) {
                failedRounds++;
            }
        }
        System.out.printf("Invariant violations in %d/%d rounds.%n", failedRounds, rounds);
        if (failedRounds > 0) {
            throw new AssertionError("Unsafe booking operations: " + failedRounds + " rounds failed");
        }
    }

    private static boolean runRound(int round, int clientCount, int iterationsPerClient,
                                    long raceWindowNanos) throws InterruptedException {
        // Fresh inventory prevents sold-out concerts from making the rest of the run idle.
        List<Concert> concerts = List.of(
                new Concert(1, "Rock Night", 154),
                new Concert(2, "Jazz Evening", 234),
                new Concert(3, "Electronic Live", 413));
        BookingService service = new BookingService(concerts, raceWindowNanos);
        List<Integer> concertIds = concerts.stream().map(concert -> concert.id).toList();

        CountDownLatch ready = new CountDownLatch(clientCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> clientFailure = new AtomicReference<>();
        List<Thread> clients = new ArrayList<>();
        for (int i = 1; i <= clientCount; i++) {
            String id = "client-" + i;
            Thread thread = new Thread(new Client(id, service, concertIds, iterationsPerClient, ready, start), id);
            thread.setUncaughtExceptionHandler((client, error) -> clientFailure.compareAndSet(null, error));
            clients.add(thread);
            thread.start();
        }
        ready.await();
        start.countDown();
        for (Thread client : clients) {
            client.join();
        }
        if (clientFailure.get() != null) {
            throw new AssertionError("Client failed", clientFailure.get());
        }

        // All clients have finished, so these reads no longer race with mutations.
        int successfulBuys = 0;
        int soldSeats = 0;
        int capacity = 0;
        List<String> violations = new ArrayList<>();
        for (Concert concert : concerts) {
            int sold = (int) concert.seats.values().stream().filter(s -> s.status == SeatStatus.SOLD).count();
            int buys = concert.soldCount.get();
            successfulBuys += buys;
            soldSeats += sold;
            capacity += concert.totalSeats;
            if (buys != sold || buys > concert.totalSeats) {
                violations.add(concert.name + ": " + buys + " purchases for " + sold + " sold seats");
            }
            concert.seats.forEach((seatId, seat) -> {
                int sales = seat.saleCount.get();
                if (sales > 1) {
                    violations.add(concert.name + " seat " + seatId + " sold " + sales + " times (" + seat.status + ")");
                } else if (sales != (seat.status == SeatStatus.SOLD ? 1 : 0)) {
                    violations.add(concert.name + " seat " + seatId + ": sales=" + sales + ", status=" + seat.status);
                }
                if (seat.status == SeatStatus.RESERVED || seat.reservedBy != null) {
                    violations.add(concert.name + " seat " + seatId + " has a leftover reservation");
                }
            });
        }
        System.out.printf("Round %02d: buys=%d, sold=%d/%d -> %s%n", round, successfulBuys,
                soldSeats, capacity, violations.isEmpty() ? "PASS" : "FAIL");
        violations.forEach(violation -> System.out.println("  " + violation));
        return violations.isEmpty();
    }
}
