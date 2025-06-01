package org.oxoo2a.sim4da;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test-Klasse für die Aufgabe-1-Simulation im sim4da-Simulator.
 * Für n = 2,4,8,…,1024 baut sie je einen Token-Ring mit Feuerwerk
 * auf, misst pro Knoten Round-Trip-Zeiten und Multicasts und
 * ermittelt in der Summary das maximale n und die MaxRounds.
 */
public class OneRingToRuleThemAll {

    private static final double P0 = 0.5;
    private static final int    K  = 3;

    /** Injiziert nur den ersten Token in Node "0". */
    class Coordinator {
        private final NetworkConnection nc =
                new NetworkConnection("Coordinator");

        public void engage() {
            nc.engage(() ->
                    nc.sendBlindly(
                            new Message().add("token","TOKEN"),
                            "0"
                    )
            );
        }
    }

    /**
     * RingSegment: empfängt Token und FIREWORK, zählt Runden,
     * multicasts, misst Zeiten, terminiert nach K leeren Runden
     * und gibt dann noch einmal das Token weiter.
     */
    class RingSegment extends Node {
        private final String       id, nextId;
        private final List<String> allIds;
        private double             p = P0;
        private int                emptyRounds   = 0;
        private int                totalRounds   = 0;
        private int                fireworkCount = 0;
        private long               lastTime      =
                System.currentTimeMillis();
        private final List<Long>   roundTimes    = new ArrayList<>();

        public RingSegment(
                String id, String nextId, List<String> allIds
        ) {
            super(id);
            this.id     = id;
            this.nextId = nextId;
            this.allIds = allIds;
        }

        @Override
        public void engage() {
            while (true) {
                Message m = receive();
                if ("FIREWORK".equals(m.query("type"))) {
                    emptyRounds = 0;
                    continue;
                }

                long now = System.currentTimeMillis();
                roundTimes.add(now - lastTime);
                lastTime = now;
                totalRounds++;
                emptyRounds++;

                if (emptyRounds >= K) {
                    // einmalig Token weiterreichen, dann aussteigen
                    sendBlindly(m, nextId);
                    break;
                }

                if (ThreadLocalRandom.current().nextDouble() < p) {
                    for (String dest : allIds) {
                        if (!dest.equals(id)) {
                            sendBlindly(
                                    new Message().add("type","FIREWORK"),
                                    dest
                            );
                        }
                    }
                    fireworkCount++;
                    emptyRounds = 0;
                }
                p /= 2.0;

                sendBlindly(m, nextId);
            }
            printStats();
        }

        private void printStats() {
            long minVal = Long.MAX_VALUE, maxVal = Long.MIN_VALUE, sum = 0;
            int  minIdx = -1, maxIdx = -1;
            for (int i = 0; i < roundTimes.size(); i++) {
                long t = roundTimes.get(i);
                sum += t;
                if (t < minVal) {
                    minVal = t; minIdx = i + 1;
                }
                if (t > maxVal) {
                    maxVal = t; maxIdx = i + 1;
                }
            }
            double avg = roundTimes.isEmpty()
                    ? 0.0
                    : (double) sum / roundTimes.size();
            System.out.printf(
                    Locale.US,
                    "Node %s final: rounds=%d, multicasts=%d, "
                            + "min_time=%d (%d), avg_time=%.2f, max_time=%d (%d)%n",
                    id, totalRounds, fireworkCount,
                    minVal, minIdx,
                    avg,
                    maxVal, maxIdx
            );
        }
    }

    @Test
    public void testOneRingToRuleThemAll() {
        int[] ringSizes =
                {2,4,8,16,32,64,128,256,512,1024};
        List<Result> results = new ArrayList<>();
        PrintStream oldOut = System.out;

        for (int n : ringSizes) {
            System.out.println("\n--- Experiment n=" + n + " ---");
            ByteArrayOutputStream baos =
                    new ByteArrayOutputStream();
            System.setOut(new PrintStream(baos));

            Simulator sim = Simulator.getInstance();
            List<String> allIds = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String id     = String.valueOf(i);
                String nextId = String.valueOf((i + 1) % n);
                allIds.add(id);
                new RingSegment(id, nextId, allIds);
            }

            new Coordinator().engage();
            long t0 = System.currentTimeMillis();
            sim.simulate();
            long duration = System.currentTimeMillis() - t0;
            sim.shutdown();

            System.setOut(oldOut);
            String output = baos.toString();
            System.out.print(output);

            List<NodeStats> stats = parseStats(output);
            if (stats.isEmpty()) {
                System.err.println("n=" + n + ": keine Statistik, Stopp.");
                break;
            }
            results.add(aggregate(n, stats, duration));
        }

        printSummary(results);
    }

    static class NodeStats {
        final int    rounds, multicasts;
        final long   minTime, maxTime;
        final double avgTime;
        NodeStats(
                int r, int m, long mi, double a, long ma
        ) {
            rounds     = r;
            multicasts = m;
            minTime    = mi;
            avgTime    = a;
            maxTime    = ma;
        }
    }

    static class Result {
        final int    n, maxRounds, totalMulticasts;
        final long   minTime, maxTime, duration;
        final double avgTime;
        Result(
                int n, int mr, int tm,
                long mi, double av, long ma, long du
        ) {
            this.n               = n;
            this.maxRounds       = mr;
            this.totalMulticasts = tm;
            this.minTime         = mi;
            this.avgTime         = av;
            this.maxTime         = ma;
            this.duration        = du;
        }
    }

    private List<NodeStats> parseStats(String out) {
        List<NodeStats> list = new ArrayList<>();
        Pattern p = Pattern.compile(
                "Node \\d+ final: rounds=(\\d+), multicasts=(\\d+), "
                        + "min_time=(\\d+) \\(\\d+\\), avg_time=([0-9]+(?:\\.[0-9]+)?), "
                        + "max_time=(\\d+) \\(\\d+\\)"
        );
        for (String line : out.split("\\R")) {
            Matcher m = p.matcher(line.trim());
            if (!m.matches()) continue;
            int    rounds     = Integer.parseInt(m.group(1));
            int    multicasts = Integer.parseInt(m.group(2));
            long   minTime    = Long.parseLong(m.group(3));
            double avgTime    = Double.parseDouble(m.group(4));
            long   maxTime    = Long.parseLong(m.group(5));
            list.add(new NodeStats(
                    rounds, multicasts, minTime, avgTime, maxTime
            ));
        }
        return list;
    }

    private Result aggregate(
            int n, List<NodeStats> stats, long duration
    ) {
        int   maxRounds = stats.stream()
                .mapToInt(s -> s.rounds)
                .max()
                .orElse(0);
        int   totalMC   = stats.stream()
                .mapToInt(s -> s.multicasts)
                .sum();
        long  minTime   = stats.stream()
                .mapToLong(s -> s.minTime)
                .min()
                .orElse(0L);
        long  maxTime   = stats.stream()
                .mapToLong(s -> s.maxTime)
                .max()
                .orElse(0L);
        double avgTime  = stats.stream()
                .mapToDouble(s -> s.avgTime)
                .average()
                .orElse(0.0);
        return new Result(
                n, maxRounds, totalMC,
                minTime, avgTime, maxTime, duration
        );
    }

    private void printSummary(List<Result> results) {
        System.out.println("\n--- Summary ---");
        System.out.printf(
                "%5s | %8s | %10s | %10s | %8s | %8s | %8s | %9s%n",
                "n","Status","MaxRounds","Multicasts",
                "Min","Avg","Max","Dur[s]"
        );
        System.out.println("-".repeat(80));
        for (Result r : results) {
            System.out.printf(
                    "%5d | %8s | %10d | %10d | %8d | %8.2f | %8d | %9.2f%n",
                    r.n, "Success", r.maxRounds, r.totalMulticasts,
                    r.minTime, r.avgTime, r.maxTime,
                    r.duration / 1000.0
            );
        }
        if (!results.isEmpty()) {
            System.out.println(
                    "Maximales erfolgreiches n: " +
                            results.get(results.size()-1).n
            );
        }
    }
}