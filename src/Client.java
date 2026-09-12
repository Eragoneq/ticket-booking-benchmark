import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

public final class Client implements Runnable {
    private final String id;
    private final BookingService service;
    private final List<Integer> concertIds;
    private final int iterations;
    private final CountDownLatch ready;
    private final CountDownLatch start;

    public Client(String id, BookingService service, List<Integer> concertIds, int iterations,
                  CountDownLatch ready, CountDownLatch start) {
        this.id = id;
        this.service = service;
        this.concertIds = concertIds;
        this.iterations = iterations;
        this.ready = ready;
        this.start = start;
    }

    @Override
    public void run() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        try {
            ready.countDown();
            start.await();
            for (int i = 0; i < iterations; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                // Most requests target the same concert and its first available seat.
                int concertId = concertIds.get(random.nextInt(5) == 0
                        ? random.nextInt(concertIds.size()) : 0);
                List<Integer> available = service.listAvailableSeats(concertId);
                if (available.isEmpty()) {
                    continue;
                }
                int seatId = available.get(random.nextInt(5) == 0
                        ? random.nextInt(available.size()) : 0);
                if (!service.reserve(concertId, seatId, id)) {
                    continue;
                }
                // Frequent cancellation keeps seats in contention. No logging or think-time
                // between reserve and buy: another reservation may still be in flight.
                try {
                    if (random.nextInt(4) == 0) {
                        service.buy(concertId, seatId, id);
                    }
                } finally {
                    // Releases cancellations or any hold left by an unsuccessful purchase.
                    service.cancel(concertId, seatId, id);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
