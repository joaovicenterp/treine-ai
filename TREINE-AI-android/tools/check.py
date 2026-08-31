#!/usr/bin/env python3
"""
Conferência estática do projeto nativo, feita sem o SDK do Android.

Não substitui o compilador — serve para pegar cedo o que mais custa
num build de CI: função inexistente, ícone que não existe, parâmetro
com nome errado, chave duplicada e linguagem de autoridade médica.
"""
import re, sys, collections
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/com/treineai/app"
files = sorted(SRC.rglob("*.kt"))
problems = collections.defaultdict(list)


def add(kind, msg):
    problems[kind].append(msg)


# ---------- 1. delimitadores equilibrados ----------
def balanced(text):
    """Ignora comentários, strings e caracteres escapados."""
    depth = {"(": 0, "{": 0, "[": 0}
    close = {")": "(", "}": "{", "]": "["}
    i, n = 0, len(text)
    in_s = in_c = in_line = in_block = False
    triple = False
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if in_line:
            if c == "\n":
                in_line = False
        elif in_block:
            if c == "*" and nxt == "/":
                in_block = False
                i += 1
        elif triple:
            if text[i:i + 3] == '"""':
                triple = False
                i += 2
        elif in_s:
            if c == "\\":
                i += 1
            elif c == '"':
                in_s = False
        elif in_c:
            if c == "\\":
                i += 1
            elif c == "'":
                in_c = False
        else:
            if c == "/" and nxt == "/":
                in_line = True
                i += 1
            elif c == "/" and nxt == "*":
                in_block = True
                i += 1
            elif text[i:i + 3] == '"""':
                triple = True
                i += 2
            elif c == '"':
                in_s = True
            elif c == "'":
                in_c = True
            elif c in depth:
                depth[c] += 1
            elif c in close:
                depth[close[c]] -= 1
                if depth[close[c]] < 0:
                    return f"'{c}' sem abertura"
        i += 1
    for k, v in depth.items():
        if v:
            return f"{v} '{k}' sem fechamento"
    return None


for f in files:
    err = balanced(f.read_text(encoding="utf-8"))
    if err:
        add("delimitadores", f"{f.relative_to(ROOT)}: {err}")

# ---------- 2. inventário de declarações ----------
declared = {}          # nome -> arquivo
private_decl = collections.defaultdict(set)
for f in files:
    txt = f.read_text(encoding="utf-8")
    for m in re.finditer(r"^\s*(private\s+|internal\s+)?(?:@Composable\s+)?fun\s+(?:<[^>]+>\s+)?"
                         r"(?:[\w.]+\.)?(\w+)\s*\(", txt, re.M):
        name = m.group(2)
        if m.group(1) and m.group(1).strip() == "private":
            private_decl[name].add(f)
        else:
            declared.setdefault(name, f)
    for m in re.finditer(r"^\s*(?:private\s+|internal\s+)?(?:data\s+|sealed\s+|abstract\s+|open\s+|enum\s+)*"
                         r"(?:class|object|interface)\s+(\w+)", txt, re.M):
        declared.setdefault(m.group(1), f)
    for m in re.finditer(r"^\s*(?:private\s+|internal\s+)?va[lr]\s+(\w+)", txt, re.M):
        declared.setdefault(m.group(1), f)

# ---------- 3. ícones ----------
icons_file = (SRC / "ui/Icons.kt").read_text(encoding="utf-8")
ICONS = set(re.findall(r'^\s*"(\w+)" to listOf', icons_file, re.M))
for f in files:
    if f.name == "Icons.kt":
        continue
    txt = f.read_text(encoding="utf-8")
    for m in re.finditer(r'\bIcon\(\s*"(\w+)"', txt):
        if m.group(1) not in ICONS:
            add("ícone inexistente", f'{f.relative_to(ROOT)}: Icon("{m.group(1)}")')
    for m in re.finditer(r'\bicon\s*=\s*"(\w+)"', txt):
        if m.group(1) not in ICONS:
            add("ícone inexistente", f'{f.relative_to(ROOT)}: icon = "{m.group(1)}"')
    for m in re.finditer(r'\bEmptyState\(\s*"(\w+)"', txt):
        if m.group(1) not in ICONS:
            add("ícone inexistente", f'{f.relative_to(ROOT)}: EmptyState("{m.group(1)}")')

# ---------- 4. telas exigidas pelo shell ----------
shell = (SRC / "ui/App.kt").read_text(encoding="utf-8")
for m in re.finditer(r"->\s*(\w+Screen)\(", shell):
    if m.group(1) not in declared:
        add("tela ausente", f"App.kt chama {m.group(1)}() mas ela não existe")

# ---------- 5. parâmetros nomeados contra as assinaturas ----------
def signatures(path):
    txt = path.read_text(encoding="utf-8")
    out = {}
    for m in re.finditer(r"(?:@Composable\s+)?fun\s+(\w+)\s*\(", txt):
        start = m.end() - 1
        depth, i = 0, start
        while i < len(txt):
            if txt[i] == "(":
                depth += 1
            elif txt[i] == ")":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        params = txt[start + 1:i]
        names = re.findall(r"(?:^|,)\s*(?:vararg\s+)?(\w+)\s*:", params)
        out.setdefault(m.group(1), set()).update(names)
    return out


api = {}
for f in [SRC / "ui/Components.kt", SRC / "ui/Icons.kt", SRC / "ui/Nav.kt",
          SRC / "ui/PoseView.kt", SRC / "ui/Theme.kt"]:
    for k, v in signatures(f).items():
        api.setdefault(k, set()).update(v)

CALLABLE = {"Btn", "Card", "Chip", "SectionTitle", "Muted", "StatBlock", "ScoreRing", "Bar",
            "ToggleRow", "Field", "Note", "LineChart", "BarChart", "WeekStrip", "TopBar",
            "ProviderBadge", "EmptyState", "Icon", "Wordmark", "ExerciseDemo", "PoseThumb",
            "BottomNav"}

for f in files:
    txt = f.read_text(encoding="utf-8")
    for name in CALLABLE:
        if name not in api:
            continue
        for m in re.finditer(rf"\b{name}\(", txt):
            start = m.end() - 1
            depth, i = 0, start
            while i < len(txt):
                if txt[i] == "(":
                    depth += 1
                elif txt[i] == ")":
                    depth -= 1
                    if depth == 0:
                        break
                i += 1
            args = txt[start + 1:i]
            # só nomes no nível superior da chamada
            level = 0
            cleaned = []
            for ch in args:
                if ch in "([{":
                    level += 1
                elif ch in ")]}":
                    level -= 1
                cleaned.append(ch if level == 0 else " ")
            for a in re.finditer(r"(?:^|,)\s*(\w+)\s*=(?!=)", "".join(cleaned)):
                if a.group(1) not in api[name]:
                    line = txt[:m.start()].count("\n") + 1
                    add("parâmetro inexistente",
                        f"{f.relative_to(ROOT)}:{line} {name}(..., {a.group(1)} = ...) — "
                        f"aceita {sorted(api[name])}")

# ---------- 6. colisão de nomes privados entre arquivos do mesmo pacote ----------
pkg_private = collections.defaultdict(lambda: collections.defaultdict(set))
for f in files:
    txt = f.read_text(encoding="utf-8")
    pkg = re.search(r"^package\s+([\w.]+)", txt, re.M)
    pkg = pkg.group(1) if pkg else "?"
    for m in re.finditer(r"^\s*private\s+(?:@Composable\s+)?(?:fun|val|var)\s+(\w+)", txt, re.M):
        pkg_private[pkg][m.group(1)].add(f.name)
# privados são de escopo de arquivo em Kotlin: colisão só importa se forem internal/public
# (mantido como informação, não como erro)

# ---------- 7. linguagem de autoridade médica ----------
BANNED = [
    (r"\bseguro\b", "seguro"), (r"\bsegura\b", "segura"),
    (r"\bperigos", "perigoso"), (r"\blesã", "lesão"), (r"\blesõ", "lesões"),
    (r"\bmachuc", "machucar"), (r"\bdiagnóstic", "diagnóstico"),
]
for f in files:
    for i, line in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
        if line.strip().startswith(("//", "*", "/*")):
            continue
        for rx, label in BANNED:
            for m in re.finditer(rx, line, re.I):
                # só dentro de strings visíveis ao usuário
                if '"' in line:
                    add("linguagem proibida", f"{f.relative_to(ROOT)}:{i} “{label}” → {line.strip()[:90]}")
                break

# ---------- 8. chamadas a APIs do estado que não existem ----------
state_api = signatures(SRC / "ui/AppState.kt")
repo_api = signatures(SRC / "data/Store.kt")
known_app_members = set(state_api) | {
    "catalog", "repo", "feedback", "voice", "motion", "route", "canGoBack", "revision",
    "settings", "user", "toast", "celebrating", "sessionResults", "lastXp",
}
for f in files:
    if f.name in ("AppState.kt",):
        continue
    txt = f.read_text(encoding="utf-8")
    for m in re.finditer(r"(?<![\w.])app\.(\w+)", txt):
        if m.group(1) not in known_app_members:
            line = txt[:m.start()].count("\n") + 1
            add("membro de AppState inexistente", f"{f.relative_to(ROOT)}:{line} app.{m.group(1)}")

known_repo = set(repo_api) | {
    "catalog", "currentUserId", "account", "seenIntro",
}
for f in files:
    txt = f.read_text(encoding="utf-8")
    for m in re.finditer(r"(?<![\w.])repo\.(\w+)", txt):
        if m.group(1) not in known_repo:
            line = txt[:m.start()].count("\n") + 1
            add("membro de Repo inexistente", f"{f.relative_to(ROOT)}:{line} repo.{m.group(1)}")

# ---------- relatório ----------
print(f"{len(files)} arquivos · {sum(len(f.read_text(encoding='utf-8').splitlines()) for f in files)} linhas\n")
print(f"ícones definidos: {len(ICONS)}")
n_screens = len(set(re.findall(r"->\s*(\w+Screen)\(", shell)))
print(f"telas exigidas pelo shell: {n_screens}\n")

total = sum(len(v) for v in problems.values())
if not total:
    print("Nenhum problema encontrado.")
    sys.exit(0)

for kind in sorted(problems):
    v = problems[kind]
    print(f"[{kind}] {len(v)}")
    for line in v[:14]:
        print(f"   {line}")
    if len(v) > 14:
        print(f"   ... e mais {len(v) - 14}")
    print()
sys.exit(1)
