#!/usr/bin/env bash
# Verifica que a camada de análise em Kotlin é numericamente idêntica à
# implementação JavaScript da versão web. Requer o compilador Kotlin.
set -euo pipefail
cd "$(dirname "$0")/.."

KOTLINC="${KOTLINC:-/tmp/kt/package/bin/kotlinc}"
export JAVA_TOOL_OPTIONS="" LANG=C.UTF-8 LC_ALL=C.UTF-8

# O catálogo é a única fonte: regenera as fixtures a partir dele.
python3 - <<'PY'
import json
c = json.load(open('app/src/main/assets/catalog.json'))
k = lambda s: json.dumps(s, ensure_ascii=False)
out = ['package com.treineai.parity', '', 'import com.treineai.app.data.Exercise',
       'import com.treineai.app.data.RepRange', '', 'val FIXTURES: List<Exercise> = listOf(']
for e in c['exercises']:
    r = e['rep']
    out.append(f'    Exercise(id={k(e["id"])}, name={k(e["name"])}, pattern={k(e["pattern"])}, '
               f'view={k(e.get("view","front"))}, rep=RepRange({k(r["joint"])}, {float(r["top"])}, '
               f'{float(r["bottom"])}), hold={str(bool(e.get("hold",False))).lower()}, '
               f'checks=listOf({", ".join(k(x) for x in e.get("checks", []))})),')
out.append(')')
open('parity/Fixtures.kt', 'w').write('\n'.join(out) + '\n')
PY

SRC="app/src/main/java/com/treineai/app/motion"
fail=0
for stage in 1 2 3; do
  main="parity/Main$([ $stage -eq 1 ] && echo '' || echo $stage).kt"
  echo "── etapa $stage ──────────────────────────────"
  "$KOTLINC" $SRC/*.kt parity/Stub.kt parity/Fixtures.kt "$main" \
    -include-runtime -d "/tmp/parity$stage.jar" 2>&1 | grep -E "error:" && exit 1
  java -Dfile.encoding=UTF-8 -jar "/tmp/parity$stage.jar" \
    > "/tmp/kotlin$([ $stage -eq 1 ] && echo '' || echo $stage).json" 2>/dev/null
  node "parity/js_dump$([ $stage -eq 1 ] && echo '' || echo $stage).js" \
    > "/tmp/js$([ $stage -eq 1 ] && echo '' || echo $stage).json"
  python3 "parity/compare$([ $stage -eq 1 ] && echo '' || echo $stage).py" || fail=1
  echo
done

[ $fail -eq 0 ] && echo "TODAS AS ETAPAS PASSARAM." || { echo "FALHOU."; exit 1; }
