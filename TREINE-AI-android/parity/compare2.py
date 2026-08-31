#!/usr/bin/env python3
"""Etapa 2: posicionamento degradado e motor de repetição, Kotlin vs JavaScript."""
import json, sys, collections

kt = json.load(open('/tmp/kotlin2.json'))
js = json.load(open('/tmp/js2.json'))

fails = collections.defaultdict(list)


def cmp_rows(a_rows, b_rows, keys, tol_keys, label, ident):
    assert len(a_rows) == len(b_rows), f'{label}: {len(a_rows)} vs {len(b_rows)}'
    for a, b in zip(a_rows, b_rows):
        tag = ' / '.join(str(a[i]) for i in ident)
        for k in keys:
            va, vb = a[k], b[k]
            if k in tol_keys and isinstance(va, (int, float)) and isinstance(vb, (int, float)):
                if abs(va - vb) > 1e-3:
                    fails[f'{label}.{k}'].append(f'{tag}: kt={va} js={vb}')
            elif va != vb:
                fails[f'{label}.{k}'].append(f'{tag}: kt={va!r} js={vb!r}')


cmp_rows(kt['setup'], js['setup'],
         ['hint', 'body', 'light', 'distance', 'full', 'framing', 'ready', 'size'],
         {'size'}, 'setup', ['ex', 'case'])

cmp_rows(kt['engine'], js['engine'],
         ['reps', 'valid', 'score', 'best', 'worst', 'avgDepth', 'avgTempo', 'mainError',
          'errors', 'blocked', 'targetAt', 'quality', 'feedback', 'repScores', 'repDepth'],
         {'avgDepth', 'avgTempo'}, 'engine', ['ex'])

n_setup, n_eng = len(kt['setup']), len(kt['engine'])
print(f'{n_setup} verificações de posicionamento · {n_eng} sessões completas de treino\n')

hints = collections.Counter(r['hint'] for r in kt['setup'])
print('avisos de posicionamento exercitados:')
for h, c in sorted(hints.items(), key=lambda x: -x[1]):
    print(f'  {h:<16} {c}')

reps = collections.Counter(r['reps'] for r in kt['engine'])
print(f'\nrepetições detectadas por sessão: {dict(sorted(reps.items()))}')
codes = sorted({c for r in kt['engine'] for c in r['feedback']})
print(f'regras disparadas ao longo do tempo: {codes}')
print(f"exercícios com alerta nível 4: {sum(1 for r in kt['engine'] if r['blocked'])}")

total = sum(len(v) for v in fails.values())
if total:
    print(f'\n{total} DIVERGÊNCIAS:')
    for k, v in fails.items():
        print(f'\n  [{k}] {len(v)} casos')
        for line in v[:8]:
            print(f'    {line}')
        if len(v) > 8:
            print(f'    ... e mais {len(v)-8}')
    sys.exit(1)

print('\nPARIDADE TOTAL: posicionamento e motor de repetição idênticos.')
