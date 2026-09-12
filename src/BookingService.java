import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

/** Deliberately unsynchronized starting point for the concurrency exercise. */
public final class BookingService {
    private final Map<Integer, Concert> concerts = new HashMap<>();
    private final long raceWindowNanos;

    public BookingService(List<Concert> concerts) {
        this(concerts, 0);
    }

    public BookingService(List<Concert> concerts, long raceWindowNanos) {
        if (raceWindowNanos < 0) {
            throw new IllegalArgumentException("Race window must not be negative");
        }
        this.raceWindowNanos = raceWindowNanos;
        for (Concert concert : concerts) {
            if (this.concerts.putIfAbsent(concert.id, concert) != null) {
                throw new IllegalArgumentException("Duplicate concert id: " + concert.id);
            }
        }
    }

    // TODO: Allow concurrent readers while excluding seat mutations.
    public List<Integer> listAvailableSeats(int concertId) {
        List<Integer> available = new ArrayList<>();
        concert(concertId).seats.forEach((seatId, seat) -> {
            if (seat.status == SeatStatus.FREE) {
                available.add(seatId);
            }
        });
        return available;
    }

    public boolean reserve(int concertId, int seatId, String clientId) {
        Concert.Seat seat = seat(concertId, seatId);
        // TODO: Make the check and update atomic.
        if (seat.status != SeatStatus.FREE) {
            return false;
        }
        simulateWork();
        seat.status = SeatStatus.RESERVED;
        seat.reservedBy = clientId;
        return true;
    }

    public boolean buy(int concertId, int seatId, String clientId) {
        Concert.Seat seat = seat(concertId, seatId);
        if (seat.status != SeatStatus.RESERVED || !clientId.equals(seat.reservedBy)) {
            return false;
        }
        simulateWork();
        seat.status = SeatStatus.SOLD;
        seat.reservedBy = null;
        seat.saleCount.incrementAndGet();
        concert(concertId).soldCount.incrementAndGet();
        return true;
    }

    public boolean cancel(int concertId, int seatId, String clientId) {
        Concert.Seat seat = seat(concertId, seatId);
        if (seat.status != SeatStatus.RESERVED || !clientId.equals(seat.reservedBy)) {
            return false;
        }
        simulateWork();
        seat.status = SeatStatus.FREE;
        seat.reservedBy = null;
        return true;
    }

    /** Exercise: atomically swap both seats' states and ownership. */
    public void transferSeat(int concertA, int seatA, int concertB, int seatB) {
        // TODO: Start with two concert locks, reproduce deadlock, then fix lock ordering.
        throw new UnsupportedOperationException("Exercise: implement transferSeat");
    }

    // TODO: Add scheduled reservation expiry, coordinating it with buy/cancel.
    // TODO: Add waitForSeat using a Condition, then a BlockingQueue worker pool.

    private void simulateWork() {
        // Optional scheduling aid: widens existing check-then-act races without changing the rules.
        // When adding locks, keep both the check and update inside the critical section.
        if (raceWindowNanos > 0) {
            LockSupport.parkNanos(raceWindowNanos);
        }
    }

    private Concert concert(int concertId) {
        Concert concert = concerts.get(concertId);
        if (concert == null) {
            throw new IllegalArgumentException("Unknown concert: " + concertId);
        }
        return concert;
    }

    private Concert.Seat seat(int concertId, int seatId) {
        Concert.Seat seat = concert(concertId).seats.get(seatId);
        if (seat == null) {
            throw new IllegalArgumentException("Unknown seat: " + seatId);
        }
        return seat;
    }
}
