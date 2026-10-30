import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.*;

/**
 * Perbandingan algoritma penjadwalan task ke worker cloud.
 * Algoritma: costgreedy | roundrobin | random | leastloaded | costload
 * Pemakaian: java TaskScheduler <algoritma> <jumlahTask> <iterasi> [fileCsv]
 * Semua task dikirim bersamaan (paralel) supaya makespan dan utilization bermakna.
 * Biaya = asumsi ilustratif, bukan harga billing cloud.
 */
public class TaskScheduler {
    static class Worker {
        final String id, baseUrl;
        final double costPerM;
        final AtomicInteger inflight = new AtomicInteger();
        Worker(String id, String baseUrl, double costPerM) {
            this.id = id; this.baseUrl = baseUrl; this.costPerM = costPerM;
        }
        double cost(int it) { return it / 1_000_000.0 * costPerM; }
    }

    static final List<Worker> WORKERS = List.of(
        new Worker("worker-1", "http://127.0.0.1:9001", 0.10),
        new Worker("worker-2", "http://127.0.0.1:9002", 0.08),
        new Worker("worker-3", "http://127.0.0.1:9003", 0.12),
        new Worker("worker-4", "http://127.0.0.1:9004", 0.09));

    static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    static boolean healthy(Worker w) {
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(w.baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            return CLIENT.send(r, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
        } catch (Exception e) { return false; }
    }

    static Worker pick(String algo, List<Worker> hs, int idx, int it, Random rnd) {
        switch (algo) {
            case "roundrobin":  return hs.get(idx % hs.size());
            case "random":      return hs.get(rnd.nextInt(hs.size()));
            case "leastloaded": return Collections.min(hs, Comparator.comparingInt(w -> w.inflight.get()));
            case "costload":    // usulan perbaikan: biaya dikalikan (beban + 1)
                return Collections.min(hs, Comparator.comparingDouble(w -> w.cost(it) * (w.inflight.get() + 1)));
            case "costgreedy":
            default:            return Collections.min(hs, Comparator.comparingDouble(w -> w.cost(it)));
        }
    }

    public static void main(String[] a) throws Exception {
        String algo = a.length > 0 ? a[0].toLowerCase() : "costgreedy";
        int n = a.length > 1 ? Integer.parseInt(a[1]) : 12;
        int it = a.length > 2 ? Integer.parseInt(a[2]) : 1_000_000;
        String csv = a.length > 3 ? a[3] : null;

        List<Worker> hs = new ArrayList<>();
        for (Worker w : WORKERS) if (healthy(w)) hs.add(w);
        System.out.println("Algorithm: " + algo + " | tasks=" + n + " | iterations=" + it
                + " | healthy workers=" + hs.size());
        if (hs.isEmpty()) { System.err.println("No healthy workers."); System.exit(1); }

        Random rnd = new Random(42);               // seed tetap -> hasil bisa diulang
        ExecutorService pool = Executors.newFixedThreadPool(n);
        List<Future<String[]>> fs = new ArrayList<>();
        long t0 = System.nanoTime();

        for (int i = 1; i <= n; i++) {
            final String tid = String.format("task-%03d", i);
            final Worker w = pick(algo, hs, i - 1, it, rnd);   // dipilih secara berurutan
            w.inflight.incrementAndGet();
            fs.add(pool.submit(() -> {
                long s = System.nanoTime();
                String body = "{\"task_id\":\"" + tid + "\",\"iterations\":" + it + "}";
                String exec = "NaN", status = "failed";
                try {
                    HttpRequest r = HttpRequest.newBuilder(URI.create(w.baseUrl + "/run"))
                            .timeout(Duration.ofSeconds(300))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
                    HttpResponse<String> resp = CLIENT.send(r, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        status = "completed";
                        Matcher m = Pattern.compile("\"execution_seconds\": ([0-9.eE-]+)").matcher(resp.body());
                        if (m.find()) exec = m.group(1);
                    }
                } catch (Exception e) { status = "failed"; }
                finally { w.inflight.decrementAndGet(); }
                double e2e = (System.nanoTime() - s) / 1e9;
                double startRel = (s - t0) / 1e9;
                return new String[]{algo, tid, w.id, status, exec,
                        String.format(Locale.US, "%.6f", e2e),
                        String.format(Locale.US, "%.6f", startRel),
                        String.format(Locale.US, "%.6f", w.cost(it))};
            }));
        }

        List<String> rows = new ArrayList<>();
        rows.add("algorithm,task_id,worker,status,exec_seconds,e2e_seconds,start_offset,est_cost");
        Map<String, Integer> dist = new TreeMap<>();
        double cost = 0; int ok = 0;
        for (Future<String[]> f : fs) {
            String[] r = f.get();
            rows.add(String.join(",", r));
            dist.merge(r[2], 1, Integer::sum);
            if (r[3].equals("completed")) { ok++; cost += Double.parseDouble(r[7]); }
            System.out.println(String.join(" | ", r));
        }
        pool.shutdown();
        double makespan = (System.nanoTime() - t0) / 1e9;
        System.out.printf(Locale.US, "%nMakespan: %.3f s | Throughput: %.3f task/s | Completed: %d/%d | Est. cost: %.6f (assumed units)%n",
                makespan, ok / makespan, ok, n, cost);
        System.out.println("Distribution: " + dist);
        rows.add("#SUMMARY," + algo + "," + n + "," + it + "," + String.format(Locale.US, "%.6f", makespan) + "," + hs.size());
        if (csv != null) Files.write(Path.of(csv), rows);
    }
}
