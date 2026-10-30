#!/usr/bin/env python3
"""Baca CSV hasil TaskScheduler -> tabel metrik + grafik PNG.
Pemakaian: python analyze.py <folder_csv> <folder_output>"""
import sys, glob, os
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

src = sys.argv[1] if len(sys.argv) > 1 else "results/runs"
dst = sys.argv[2] if len(sys.argv) > 2 else "results"
os.makedirs(dst, exist_ok=True)

rows = []
dists = {}
runs_ms = {}
task_lat = {}
for f in sorted(glob.glob(os.path.join(src, "*.csv"))):
    lines = open(f).read().strip().splitlines()
    summ = [l for l in lines if l.startswith("#SUMMARY")][0].split(",")
    algo, n, it, makespan, nworkers = summ[1], int(summ[2]), int(summ[3]), float(summ[4]), int(summ[5])
    df = pd.read_csv(f, comment="#")
    ok = df[df.status == "completed"]
    # Utilization = porsi waktu worker "sibuk" (gabungan interval task, tidak dobel hitung)
    busy = 0.0
    for _, g in ok.groupby("worker"):
        iv = sorted(zip(g.start_offset, g.start_offset + g.e2e_seconds))
        cur_s, cur_e = iv[0]
        for st, en in iv[1:]:
            if st > cur_e:
                busy += cur_e - cur_s; cur_s, cur_e = st, en
            else:
                cur_e = max(cur_e, en)
        busy += cur_e - cur_s
    rows.append(dict(
        algorithm=algo, tasks=n, iterations=it, makespan_s=makespan,
        throughput=len(ok) / makespan, est_cost=ok.est_cost.sum(),
        utilization_pct=100 * busy / (makespan * nworkers),
        mean_latency_s=ok.e2e_seconds.mean(), completed=len(ok)))
    dists[algo] = ok.worker.value_counts().to_dict()
    runs_ms.setdefault(algo, []).append(makespan)
    task_lat.setdefault(algo, []).append(ok.sort_values("task_id").e2e_seconds.astype(float).tolist())

res = pd.DataFrame(rows)
# Rata-rata jika satu algoritma dijalankan berkali-kali
agg = res.groupby("algorithm").mean(numeric_only=True).round(4)
agg.to_csv(os.path.join(dst, "summary_metrics.csv"))
print(agg.to_string())

order = [a for a in ["costgreedy", "roundrobin", "random", "leastloaded", "costload"] if a in agg.index]
agg = agg.loc[order]
colors = ["#c0392b" if a == "costgreedy" else "#2e86c1" for a in order]

def bar(col, title, ylabel, fname):
    plt.figure(figsize=(7, 4))
    ax = agg[col].plot(kind="bar", color=colors, edgecolor="black")
    ax.set_title(title); ax.set_ylabel(ylabel); ax.set_xlabel("Algoritma")
    for p in ax.patches:
        ax.annotate(f"{p.get_height():.3g}", (p.get_x() + p.get_width() / 2, p.get_height()),
                    ha="center", va="bottom", fontsize=9)
    plt.xticks(rotation=20); plt.tight_layout()
    plt.savefig(os.path.join(dst, fname), dpi=150); plt.close()

bar("makespan_s", "Makespan (lebih kecil lebih baik)", "detik", "chart_makespan.png")
bar("throughput", "Throughput (lebih besar lebih baik)", "task / detik", "chart_throughput.png")
bar("est_cost", "Estimasi biaya total (unit asumsi)", "unit asumsi", "chart_cost.png")
bar("utilization_pct", "Utilization rata-rata worker", "%", "chart_utilization.png")

# Distribusi task per worker (stacked)
dd = pd.DataFrame(dists).T.fillna(0).loc[order]
ax = dd.plot(kind="bar", stacked=True, figsize=(7, 4), edgecolor="black")
ax.set_title("Distribusi task per worker (run terakhir)"); ax.set_ylabel("jumlah task"); ax.set_xlabel("Algoritma")
ax.legend(loc="upper left", bbox_to_anchor=(1.01, 1))
plt.xticks(rotation=20); plt.tight_layout()
plt.savefig(os.path.join(dst, "chart_distribution.png"), dpi=150); plt.close()
print("Grafik tersimpan di", dst)

# ---- Dashboard HTML otomatis (data terbaru disuntikkan ke template) ----
import json, re
tpl_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "dashboard_template.html")
if os.path.exists(tpl_path):
    def mean_cols(lists):
        n = min(len(l) for l in lists)
        return [round(sum(l[i] for l in lists) / len(lists), 4) for i in range(n)]
    data = {
        "meta": {"tasks": int(res.tasks.iloc[0]), "iterations": int(res.iterations.iloc[0]),
                 "runs": int(len(res) / res.algorithm.nunique()), "taskLabel": "rata-rata semua run"},
        "metrics": agg.reset_index().to_dict(orient="records"),
        "runs": {a: [round(x, 4) for x in v] for a, v in runs_ms.items()},
        "tasks": {a: mean_cols(v) for a, v in task_lat.items()},
        "dist": {a: {w: int(n) for w, n in d.items()} for a, d in dists.items()},
    }
    html = open(tpl_path, encoding="utf-8").read()
    block = "/*DATA*/\nconst DATA = " + json.dumps(data) + ";\n/*END*/"
    html = re.sub(r"/\*DATA\*/.*?/\*END\*/", lambda m: block, html, flags=re.S)
    open(os.path.join(dst, "dashboard.html"), "w", encoding="utf-8").write(html)
    print("Dashboard tersimpan di", os.path.join(dst, "dashboard.html"))
else:
    print("dashboard_template.html tidak ditemukan di folder yang sama dengan analyze.py")
