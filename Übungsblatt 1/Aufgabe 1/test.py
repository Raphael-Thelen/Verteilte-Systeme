import ast
import re
import subprocess
import time

RING_PROGRAM = "ring.py"
n_values = [2, 4, 8, 16, 32, 64, 128, 256, 512, 1024]
results = []

def parse_stats(output):
    stats = []
    for line in output.splitlines():
        m = re.match(
            r"Node (\d+) final: rounds=(\d+), multicasts=(\d+), round_times=(\[[^\]]*\])",
            line.strip()
        )
        if m:
            node_id = int(m.group(1))
            rounds = int(m.group(2))
            multicasts = int(m.group(3))
            round_times = ast.literal_eval(m.group(4))
            stats.append({
                "node_id": node_id,
                "rounds": rounds,
                "multicasts": multicasts,
                "round_times": round_times
            })
    if not stats:
        return None
    total_rounds = sum(s["rounds"] for s in stats)
    total_multicasts = sum(s["multicasts"] for s in stats)
    all_times = [t for s in stats for t in s["round_times"]]
    if all_times:
        min_time = min(all_times)
        max_time = max(all_times)
        avg_time = sum(all_times) / len(all_times)
    else:
        min_time = max_time = avg_time = 0
    return {
        "nodes": len(stats),
        "total_rounds": total_rounds,
        "total_multicasts": total_multicasts,
        "min_time": min_time,
        "avg_time": avg_time,
        "max_time": max_time
    }

for n in n_values:
    print(f"\n--- Starte Experiment mit n={n} ---")
    start_time = time.time()
    try:
        proc = subprocess.Popen(
            ["python3", "-u", RING_PROGRAM, str(n)],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        out, err = proc.communicate(timeout=600)
    except subprocess.TimeoutExpired:
        proc.kill()
        print("Timeout! Ring zu groß?")
        break

    duration = time.time() - start_time

    # Debug-Ausgaben
    if err.strip():
        print(f"Fehlerausgabe (stderr):\n{err}")
    # print(f"Programmausgabe (stdout):\n{out}")

    stats = parse_stats(out)
    if stats is None:
        print(f"n={n}: Keine Statistik gefunden, vermutlich zu groß oder Fehler.")
        break

    results.append({
        "n": n,
        "success": True,
        "total_rounds": stats["total_rounds"],
        "total_multicasts": stats["total_multicasts"],
        "min_time": stats["min_time"],
        "avg_time": stats["avg_time"],
        "max_time": stats["max_time"],
        "duration": duration
    })
    print(f"Succeeded in {round(duration, 2)}sec")


print("\n--- Summary ---")
print(f"{'Nodes':>5} | {'Status':^8} | {'Rounds':>8} | {'Multicasts':>10} | {'Min':>8} | {'Avg':>8} | {'Max':>8} | {'Duration':>8}")
print("-" * 80)
for res in results:
    print(
        f"{res['n']:>5} | "
        f"{'Success' if res['success'] else 'Failed':^8} | "
        f"{res['total_rounds']:>8} | "
        f"{res['total_multicasts']:>10} | "
        f"{res['min_time']:>8.4f} | "
        f"{res['avg_time']:>8.4f} | "
        f"{res['max_time']:>8.4f} | "
        f"{res['duration']:>8.2f}s"
    )

if results:
    print(f"\nMaximales erfolgreiches n: {results[-1]['n']}")
else:
    print("Kein Experiment erfolgreich abgeschlossen.")
