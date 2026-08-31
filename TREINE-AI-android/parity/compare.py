#!/usr/bin/env python3
"""Compara a saída da camada de movimento Kotlin com a da versão JavaScript."""
import json, sys, collections

kt = json.load(open('/tmp/kotlin.json'))
js = json.load(open('/tmp/js.json'))

NUM = ['joint', 'knee', 'elbow', 'hip', 'shoulder', 'ankle', 'trunk', 'torsoLean',
       'shrug', 'hipAbd', 'headFwd', 'elbowOff', 'hipDev', 'valgus', 'size']
STR = ['hint']
BOOL = ['ready']
LIST = ['live', 'rep']
TOL = 1e-3

assert len(kt) == len(js), f'linhas diferentes: kotlin={len(kt)} js={len(js)}'

fails = collections.defaultdict(list)
worst = collections.defaultdict(float)

for a, b in zip(kt, js):
    assert a['ex'] == b['ex'] and abs(a['p'] - b['p']) < 1e-9, f"desalinhado {a['ex']} {b['ex']}"
    tag = f"{a['ex']}@p={a['p']}"
    for k in NUM:
        d = abs(a[k] - b[k])
        worst[k] = max(worst[k], d)
        if d > TOL:
            fails[k].append(f"{tag}: kt={a[k]} js={b[k]} (Δ{d:.4f})")
    for k in STR + BOOL:
        if a[k] != b[k]:
            fails[k].append(f"{tag}: kt={a[k]!r} js={b[k]!r}")
    for k in LIST:
        if sorted(a[k]) != sorted(b[k]):
            fails[k].append(f"{tag}: kt={sorted(a[k])} js={sorted(b[k])}")

print(f"{len(kt)} amostras · {len({r['ex'] for r in kt})} exercícios · 21 fases cada\n")
print("desvio máximo por métrica:")
for k in NUM:
    flag = 'OK ' if worst[k] <= TOL else 'X  '
    print(f"  {flag}{k:<11} {worst[k]:.2e}")

total = sum(len(v) for v in fails.values())
if total:
    print(f"\n{total} DIVERGÊNCIAS:")
    for k, v in fails.items():
        print(f"\n  [{k}] {len(v)} casos")
        for line in v[:6]:
            print(f"    {line}")
        if len(v) > 6:
            print(f"    ... e mais {len(v)-6}")
    sys.exit(1)

hints = collections.Counter(r['hint'] for r in kt)
print(f"\nhints exercitados: {dict(hints)}")
print(f"regras disparadas: {sorted({c for r in kt for c in r['live'] + r['rep']})}")
print("\nPARIDADE TOTAL: Kotlin e JavaScript produzem resultados idênticos.")
