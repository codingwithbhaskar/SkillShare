#!/usr/bin/env python3
"""
SkillShare benchmark — parses the EXPLAIN (ANALYZE, FORMAT JSON) plan files
run_benchmark.ps1 writes into results/*.json and builds the "Normal vs
Indexed" comparison table (results/comparison_table.md + .csv).

Stdlib only — no install needed. Run from anywhere:
    python3 parse_results.py
"""
import csv
import glob
import json
import os
import re

RESULTS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "results")


def _index_names(plan, acc=None):
    if acc is None:
        acc = []
    if "Index Name" in plan:
        acc.append(plan["Index Name"])
    for child in plan.get("Plans", []):
        _index_names(child, acc)
    return acc


def _node_types(plan, acc=None):
    if acc is None:
        acc = []
    acc.append(plan.get("Node Type"))
    for child in plan.get("Plans", []):
        _node_types(child, acc)
    return acc


def _read_text(path):
    """Auto-detect encoding: run_benchmark.ps1's *> redirection writes
    UTF-16LE (Windows PowerShell 5.1's default for native-command output
    redirection) with a BOM; plain UTF-8 (with or without BOM) is accepted
    too in case that ever changes."""
    with open(path, "rb") as f:
        raw = f.read()
    if raw.startswith(b"\xff\xfe"):
        return raw[2:].decode("utf-16-le")
    if raw.startswith(b"\xfe\xff"):
        return raw[2:].decode("utf-16-be")
    if raw.startswith(b"\xef\xbb\xbf"):
        return raw[3:].decode("utf-8")
    return raw.decode("utf-8")


def load(path):
    raw = _read_text(path).strip()
    if not raw:
        return None
    data = json.loads(raw)
    root = data[0]
    plan = root["Plan"]
    idx_names = [n for n in _index_names(plan) if n]
    return {
        "execution_time_ms": root.get("Execution Time"),
        "planning_time_ms": root.get("Planning Time"),
        "total_cost": plan.get("Total Cost"),
        "plan_rows": plan.get("Plan Rows"),
        "actual_rows": plan.get("Actual Rows"),
        "top_node": plan.get("Node Type"),
        "node_types": ",".join(sorted(set(n for n in _node_types(plan) if n))),
        "indexes_used": ",".join(sorted(set(idx_names))) or "-",
    }


def main():
    rows = []
    skipped = []
    for path in sorted(glob.glob(os.path.join(RESULTS_DIR, "*.json"))):
        name = os.path.basename(path)[:-5]
        m = re.match(r"(.+)_(\d+)_(indexed|noindex)$", name)
        if not m:
            continue
        query, scale, mode = m.group(1), int(m.group(2)), m.group(3)
        try:
            parsed = load(path)
        except (json.JSONDecodeError, KeyError, IndexError) as e:
            skipped.append((name, str(e)))
            continue
        if parsed is None:
            skipped.append((name, "empty file — the psql step likely failed; check run_log_*.txt"))
            continue
        parsed.update({"query": query, "scale": scale, "mode": mode})
        rows.append(parsed)

    rows.sort(key=lambda r: (r["query"], r["scale"], r["mode"]))

    cols = ["query", "scale", "mode", "execution_time_ms", "planning_time_ms",
            "total_cost", "plan_rows", "actual_rows", "top_node", "indexes_used"]

    out_md = os.path.join(RESULTS_DIR, "comparison_table.md")
    with open(out_md, "w") as f:
        f.write("| " + " | ".join(cols) + " |\n")
        f.write("|" + "|".join(["---"] * len(cols)) + "|\n")
        for r in rows:
            f.write("| " + " | ".join(str(r[c]) for c in cols) + " |\n")

    out_csv = os.path.join(RESULTS_DIR, "comparison_table.csv")
    with open(out_csv, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=cols)
        w.writeheader()
        for r in rows:
            w.writerow({c: r[c] for c in cols})

    print(f"Parsed {len(rows)} result file(s).")
    if skipped:
        print(f"Skipped {len(skipped)} file(s):")
        for name, reason in skipped:
            print(f"  {name}: {reason}")
    print(f"Wrote {out_md}")
    print(f"Wrote {out_csv}")

    print("\n--- Speedup (noindex time / indexed time) ---")
    by_key = {}
    for r in rows:
        by_key.setdefault((r["query"], r["scale"]), {})[r["mode"]] = r["execution_time_ms"]
    for (query, scale), modes in sorted(by_key.items()):
        if modes.get("indexed") and modes.get("noindex"):
            speedup = modes["noindex"] / modes["indexed"]
            print(f"{query:22s} scale={scale:>7} indexed={modes['indexed']:>10.3f}ms "
                  f"noindex={modes['noindex']:>10.3f}ms speedup={speedup:6.2f}x")


if __name__ == "__main__":
    main()
