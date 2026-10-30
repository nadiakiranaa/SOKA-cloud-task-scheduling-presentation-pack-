import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * CostGreedy: pilih worker sehat dengan estimasi biaya terendah untuk setiap task.
 * Cost pada demo ini adalah asumsi ilustratif, bukan harga billing Azure.
 */
public class CostGreedyScheduler {
    static class Worker {
        final String id;
        final String baseUrl;
        final double costPerMillionIterations;

        Worker(String id, String baseUrl, double costPerMillionIterations) {
            this.id = id;
            this.baseUrl = baseUrl;
            this.costPerMillionIterations = costPerMillionIterations;
        }

        double estimatedCost(int iterations) {
            return (iterations / 1_000_000.0) * costPerMillionIterations;
        }
    }

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private static final List<Worker> WORKERS = List.of(
        new Worker("worker-1", "http://127.0.0.1:9001", 0.10),
        new Worker("worker-2", "http://127.0.0.1:9002", 0.08),
        new Worker("worker-3", "http://127.0.0.1:9003", 0.12),
        new Worker("worker-4", "http://127.0.0.1:9004", 0.09)
);

    private static boolean isHealthy(Worker worker) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(worker.baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static String execute(Worker worker, String taskId, int iterations) throws Exception {
        String body = "{\"task_id\":\"" + taskId + "\",\"iterations\":" + iterations + "}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(worker.baseUrl + "/run"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Worker returned HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    public static void main(String[] args) throws Exception {
        int taskCount = args.length > 0 ? Integer.parseInt(args[0]) : 6;
        int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 100_000;

        List<Worker> healthy = new ArrayList<>();
        for (Worker worker : WORKERS) {
            if (isHealthy(worker)) healthy.add(worker);
        }
        System.out.println("Algorithm: CostGreedy");
        System.out.println("Available workers: " + healthy.size());
        if (healthy.isEmpty()) {
            System.err.println("No healthy workers available.");
            System.exit(1);
        }

        long batchStart = System.nanoTime();
        for (int i = 1; i <= taskCount; i++) {
            final String taskId = String.format("task-%03d", i);
            Worker selected = healthy.stream()
                    .min((a, b) -> Double.compare(a.estimatedCost(iterations), b.estimatedCost(iterations)))
                    .orElseThrow();
            double cost = selected.estimatedCost(iterations);
            System.out.printf("%n[%s] selected=%s estimatedCost=%.6f (assumed units)%n",
                    taskId, selected.id, cost);
            try {
                System.out.println("Result: " + execute(selected, taskId, iterations));
            } catch (Exception e) {
                System.out.println("Task failed: " + e.getMessage());
            }
        }
        double seconds = (System.nanoTime() - batchStart) / 1_000_000_000.0;
        System.out.printf("%nBatch wall-clock time: %.3f seconds%n", seconds);
        System.out.println("Note: costs are illustrative assumptions, not Azure billing prices.");
    }
}
