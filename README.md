# Concert Ticket Booking System

A small Java concurrency exercise project to test different ways of 
approaching multithreaded programming in controlled environment. 

Twenty-five client threads share one in-memory `BookingService`, 
randomly browse and reserve seats, then buy or cancel their reservation. 
The default stress run uses 20 rounds, each with fresh concerts
and up to 100 attempts per client. Change the settings in `src/Main.java` to
adjust the workload.

## Run

Tested with Java 23. 
Run `Main` in IntelliJ, or from PowerShell in the project directory:

```powershell
New-Item -ItemType Directory -Force out | Out-Null
javac -d out src/*.java
java -cp out Main
```

No dependencies or build tool are required.
