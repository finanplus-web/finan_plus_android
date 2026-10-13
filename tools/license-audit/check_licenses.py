#!/usr/bin/env python3
# Finan+ — Copyright (C) 2026 Juscelino Be
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Confere as licenças das dependências do Finan+ contra a política revisada em
third_party/license-policy.json. Só usa a biblioteca padrão do Python.

Entradas (geradas por tools/license-audit/audit.init.gradle.kts):
  app/build/reports/cyclonedx-direct/bom.json     SBOM CycloneDX (licenças declaradas nos POMs)
  app/build/reports/license-audit/resolved.json   versões resolvidas por classpath

Falha (código 1) quando:
  - aparece uma dependência que nenhuma regra da política cobre (dependência nova sem revisão);
  - uma dependência aparece num escopo que a regra não permite (ex.: JUnit dentro do APK);
  - a licença declarada no POM é desconhecida, ausente sem justificativa, ou diferente da revisada;
  - um componente distribuído (release/debug) tem licença marcada como incompatível com a GPL-3.0;
  - um componente do APK de release não tem a seção de avisos correspondente no app
    (app/src/main/assets/licenca/TERCEIROS.txt).
Avisa (sem falhar) quando uma regra da política não corresponde a nenhuma dependência (política velha).

Uso: python3 tools/license-audit/check_licenses.py [--report caminho.md]
"""
import argparse
import fnmatch
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DEFAULTS = {
    "sbom": "app/build/reports/cyclonedx-direct/bom.json",
    "resolved": "app/build/reports/license-audit/resolved.json",
    "policy": "third_party/license-policy.json",
    "notices": "app/src/main/assets/licenca/TERCEIROS.txt",
}
SCOPE_OF_CONFIG = {
    "releaseRuntimeClasspath": "release",
    "debugRuntimeClasspath": "debug",
    "debugUnitTestRuntimeClasspath": "test",
}
SCOPE_LABEL = {"release": "APK de release", "debug": "só APK de teste (debug)", "test": "só testes"}


def load(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as f:
        return json.load(f)


def norm_key(s):
    return re.sub(r"[^a-z0-9]+", " ", s.lower()).strip()


def licenses_of(component, aliases):
    """Licenças declaradas no SBOM, normalizadas para identificadores SPDX quando possível."""
    found = []
    for entry in component.get("licenses", []) or []:
        if "expression" in entry:
            found.append(entry["expression"].strip())
            continue
        lic = entry.get("license", {})
        if lic.get("id"):
            found.append(lic["id"])
        else:
            name = (lic.get("name") or "").strip()
            url = (lic.get("url") or "").strip()
            mapped = (aliases.get(norm_key(name)) if name else None) or \
                (aliases.get(url.rstrip("/").lower()) if url else None)
            found.append(mapped or f"?{name or url}")
    return sorted(set(found))


def main():
    ap = argparse.ArgumentParser()
    for k, v in DEFAULTS.items():
        ap.add_argument(f"--{k}", default=v)
    ap.add_argument("--report", default="app/build/reports/license-audit/license-report.md")
    a = ap.parse_args()

    policy = load(a.policy)
    sbom = load(a.sbom)
    resolved = load(a.resolved)
    aliases = {norm_key(k) if not k.startswith("http") else k.rstrip("/").lower(): v
               for k, v in policy["license_aliases"].items()}
    known = policy["licenses"]
    notices_path = os.path.join(ROOT, a.notices)
    notices = open(notices_path, encoding="utf-8").read() if os.path.exists(notices_path) else ""

    # escopo mais "forte" em que cada componente aparece
    scope = {}
    for cfg, sc in SCOPE_OF_CONFIG.items():
        for coord in resolved.get(cfg, []):
            if coord not in scope:
                scope[coord] = sc
    sbom_by_coord = {f"{c.get('group', '')}:{c['name']}:{c.get('version', '')}": c
                     for c in sbom.get("components", [])}

    errors, warnings, rows = [], [], []
    used_rules = set()
    missing_notice = set()
    for coord in sorted(scope, key=lambda c: (["release", "debug", "test"].index(scope[c]), c)):
        sc = scope[coord]
        ga = coord.rsplit(":", 1)[0]
        rule = next((r for r in policy["components"] if fnmatch.fnmatchcase(ga, r["match"])), None)
        declared = licenses_of(sbom_by_coord.get(coord, {}), aliases)
        if rule is None:
            errors.append(f"`{coord}` ({SCOPE_LABEL[sc]}): dependência nova, sem regra em {a.policy}. "
                          f"Revise a licença e as obrigações e acrescente uma regra.")
            rows.append((coord, sc, ", ".join(declared) or "—", "—", "SEM REVISÃO"))
            continue
        used_rules.add(rule["match"])
        expected = rule["license"]
        status = "ok"
        if sc not in rule["scopes"]:
            errors.append(f"`{coord}` aparece em **{SCOPE_LABEL[sc]}**, mas a regra `{rule['match']}` só permite "
                          f"{', '.join(SCOPE_LABEL[s] for s in rule['scopes'])}.")
            status = "ESCOPO"
        if not declared:
            if not rule.get("license_evidence"):
                errors.append(f"`{coord}`: o POM não declara licença e a regra `{rule['match']}` não traz "
                              f"`license_evidence` (fonte primária que comprove a licença).")
                status = "DESCONHECIDA"
        elif any(d.startswith("?") for d in declared):
            errors.append(f"`{coord}`: licença não reconhecida no POM: {', '.join(declared)}. "
                          f"Confira e, se for o caso, acrescente o nome em `license_aliases`.")
            status = "DESCONHECIDA"
        elif expected not in declared and not rule.get("license_evidence"):
            errors.append(f"`{coord}`: o POM declara {', '.join(declared)}, mas a política revisou {expected}. "
                          f"A licença mudou? Revise antes de aceitar.")
            status = "MUDOU"
        if expected not in known:
            errors.append(f"Regra `{rule['match']}`: licença {expected} não está em `licenses` da política.")
            status = "DESCONHECIDA"
        elif sc in ("release", "debug") and not known[expected]["gpl3_compatible"]:
            errors.append(f"`{coord}` ({SCOPE_LABEL[sc]}) é {expected}, marcada como incompatível com a "
                          f"GPL-3.0-or-later para distribuição.")
            status = "INCOMPATÍVEL"
        if sc == "release":
            headings = rule.get("notice") or []
            if isinstance(headings, str):
                headings = [headings]
            if not headings:
                errors.append(f"Regra `{rule['match']}`: componente do APK sem `notice` (seção dos avisos no app).")
                status = "SEM AVISO"
            for heading in headings:
                if heading not in notices:
                    if heading not in missing_notice:
                        missing_notice.add(heading)
                        errors.append(f"A seção \"{heading}\" (ex.: `{coord}`) não está em {a.notices} (avisos dentro do app).")
                    status = "SEM AVISO"
        rows.append((coord, sc, ", ".join(declared) or f"(sem POM) {expected}", expected, status))

    for r in policy["components"]:
        if r["match"] not in used_rules:
            warnings.append(f"Regra `{r['match']}` não corresponde a nenhuma dependência resolvida (política desatualizada?).")

    counts = {s: sum(1 for c in scope.values() if c == s) for s in ("release", "debug", "test")}
    out = ["# Auditoria de licenças — Finan+ Android", "",
           f"Componentes resolvidos: **{len(scope)}** — APK de release: {counts['release']}, "
           f"só debug: {counts['debug']}, só testes: {counts['test']}.", "",
           f"Resultado: **{'FALHOU' if errors else 'OK'}** ({len(errors)} erro(s), {len(warnings)} aviso(s)).", ""]
    if errors:
        out += ["## Erros", ""] + [f"- {e}" for e in errors] + [""]
    if warnings:
        out += ["## Avisos", ""] + [f"- {w}" for w in warnings] + [""]
    out += ["## Componentes", "", "| Componente | Escopo | Licença no POM | Licença revisada | Situação |",
            "|---|---|---|---|---|"]
    out += [f"| `{c}` | {SCOPE_LABEL[s]} | {d} | {e} | {st} |" for c, s, d, e, st in rows]
    text = "\n".join(out) + "\n"

    rp = os.path.join(ROOT, a.report)
    os.makedirs(os.path.dirname(rp), exist_ok=True)
    with open(rp, "w", encoding="utf-8") as f:
        f.write(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(text)
    for e in errors:
        print(f"::error::{re.sub(r'[`*]', '', e)}")
    for w in warnings:
        print(f"::warning::{re.sub(r'[`*]', '', w)}")
    print(f"Relatório: {rp}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
