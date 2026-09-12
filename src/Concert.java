import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class Concert {
    final int id;
    final String name;
    final int totalSeats;
    final Map<Integer, Seat> seats = new LinkedHashMap<>();

    // A diagnostic counter, not protection against selling the same seat twice.
    final AtomicInteger soldCount = new AtomicInteger();

    public Concert(int id, String name, int totalSeats) {
        if (totalSeats < 1) {
            throw new IllegalArgumentException("A concert must have at least one seat");
        }
        this.id = id;
        this.name = name;
        this.totalSeats = totalSeats;
        for (int seatId = 1; seatId <= totalSeats; seatId++) {
            seats.put(seatId, new Seat());
        }
    }

    static final class Seat {
        SeatStatus status = SeatStatus.FREE;
        String reservedBy;
        // Diagnostic history: a seat can look SOLD even after multiple successful purchases.
        final AtomicInteger saleCount = new AtomicInteger();
        // TODO: Add a reservation deadline when implementing expiry.
    }
}
