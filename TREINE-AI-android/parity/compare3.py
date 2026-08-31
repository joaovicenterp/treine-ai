#!/usr/bin/env python3
"""Etapa 3: as 18 regras de execução, quadro a quadro, Kotlin vs JavaScript."""
import json, collections, sys

kt = json.load(open('/tmp/kotlin3.json'))
js = json.load(open('/tmp/js3.json'))
assert len(kt) == len(js), (len(kt), len(js))

ALL = ['depth', 'rom', 'tempo', 'lockout', 'symmetry', 'torsoLean', 'backNeutral',
       'kneeValgus', 'hipSag', 'headPos', 'torsoStable', 'momentum', 'elbowDrift',
       'hipShoot', 'hipLockout', 'kneeLock', 'hipStable', 'shoulderDepth']

bad = []
for a, b in zip(kt, js):
    ka = sorted((i['c'], i['l'], i['w'], i['m']) for i in a['issues'])
    kb = sorted((i['c'], i['l'], i['w'], i['m']) for i in b['issues'])
    if ka != kb:
        bad.append((a['ex'], a['f'], ka, kb))

cov = collections.Counter(i['c'] for r in kt for i in r['issues'])
print(f'{len(kt)} quadros · {len({r["ex"] for r in kt})} cenários\n')
print('cobertura das 18 regras (código · nível · peso · texto):')
for r in ALL:
    print(f'  {"OK " if cov[r] else "-- "}{r:<15} {cov[r]}')
missing = [r for r in ALL if not cov[r]]
print(f'\nregras nunca disparadas: {missing or "nenhuma"}')

if bad:
    print(f'\n{len(bad)} DIVERGÊNCIAS:')
    for e, f, ka, kb in bad[:6]:
        print(f'  {e}@{f}\n    kt={ka}\n    js={kb}')
    sys.exit(1)
if missing:
    print('\nAVISO: nem todas as regras foram exercitadas.')
    sys.exit(1)
print('\nPARIDADE TOTAL: as 18 regras produzem alertas idênticos.')
