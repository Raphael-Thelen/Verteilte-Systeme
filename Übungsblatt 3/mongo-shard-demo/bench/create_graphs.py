#!/usr/bin/env python3
import pandas as pd
import matplotlib.pyplot as plt
import os

# 1) Dateipfad anpassen: summary.csv liegt in bench/logs
base = os.path.dirname(__file__)
summary_path = os.path.join(base, 'summary.csv')

# 2) CSV einlesen
df = pd.read_csv(summary_path)

# 3) Spaltentypen konvertieren
df['shards'] = df['shards'].astype(int)
df['req_s']  = df['req_s'].astype(float)
df['p50_ms'] = df['p50_ms'].astype(float)
df['p90_ms'] = df['p90_ms'].astype(float)
df['p99_ms'] = df['p99_ms'].astype(float)

# 4) Einzigartige Werte bestimmen
modes = df['mode'].unique()
tests = df['test'].unique()

# 5) Durchsatz plotten
plt.figure(figsize=(8,5))
for mode in modes:
    for test in tests:
        sub = df[(df['mode']==mode)&(df['test']==test)]
        if sub.empty: continue
        plt.plot(sub['shards'],
                 sub['req_s'],
                 marker='o',
                 label=f'{mode}_{test}')
plt.xlabel('Anzahl Shards')
plt.ylabel('Throughput (ops/s)')
plt.title('Durchsatz vs. Shard-Anzahl')
plt.legend()
plt.grid(True)
plt.tight_layout()
plt.savefig(os.path.join(base, 'throughput_vs_shards.png'))
print("✅ throughput_vs_shards.png gespeichert")

# 6) P90-Latenz plotten
plt.figure(figsize=(8,5))
for mode in modes:
    for test in tests:
        sub = df[(df['mode']==mode)&(df['test']==test)]
        if sub.empty: continue
        plt.plot(sub['shards'],
                 sub['p90_ms'],
                 marker='o',
                 label=f'{mode}_{test}')
plt.xlabel('Anzahl Shards')
plt.ylabel('Latenz P90 (ms)')
plt.title('Latenz P90 vs. Shard-Anzahl')
plt.legend()
plt.grid(True)
plt.tight_layout()
plt.savefig(os.path.join(base, 'latency90_vs_shards.png'))
print("✅ latency90_vs_shards.png gespeichert")