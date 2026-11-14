#!/usr/bin/env python3
"""Quester perf report: render the Performance section of qa-reports/<date>.md.

Reads k6 NDJSON summaries (one per journey, written by k6/run-all.sh with
`k6 run --out json=...`) and Lighthouse filesystem-upload reports
(*.report.json under the LHCI output dir), then appends (or replaces, if
the date's section already exists) a `## Performance — <date>` section in
the QA report file. If the report file does not exist yet (nightly.yml has
not written it), a minimal header is created first.

k6 NDJSON shape (one JSON object per line):
  {"type":"Point","metric":"http_req_duration","data":{"value":123.4,
   "tags":{"warmup":"true", ...}}}
Warmup-tagged points (setup() calls) are excluded from p95, so the numbers
describe steady-state VUs only. http_req_failed points are 1/0; the checks
rate comes from the "checks" metric points.

Usage (as run by quester-perf.yml):
    python3 scripts/perf-report.py --k6-dir k6-results --lhci-dir lighthouse-results \\
        --date 2026-11-14 --run-url <run-url> --commit <sha> \\
        --out qa-reports/2026-11-14.md

Self-test (no real results needed; uses embedded fake data):
    python3 scripts/perf-report.py --self-test
"""
import argparse
import glob
import json
import math
import os
import sys
import tempfile

JOURNEYS = ["login", "profile", "jobs", "matches", "tracker", "coach"]
# Thresholds mirrored from k6/*.js — if a script threshold changes, change
# the matching entry here too (the report only DISPLAYS them; k6 enforces).
THRESHOLDS = {
    "login": "p95 < 500ms, errors < 1%",
    "profile": "p95 < 500ms, errors < 1%",
    "jobs": "p95 < 500ms, errors < 1%",
    "matches": "p95 < 500ms, errors < 1%",
    "tracker": "p95 < 500ms, errors < 1%",
    "coach": "p95 < 5000ms, 200 or 503 accepted (no Ollama in CI)",
}


def percentile(values, pct):
    if not values:
        return None
    ordered = sorted(values)
    idx = math.ceil(pct / 100.0 * len(ordered)) - 1
    return ordered[max(0, min(idx, len(ordered) - 1))]


def parse_k6_file(path):
    """Return (p95_ms, error_rate, checks_rate, n_requests) or None."""
    durations, failed, checks = [], [], []
    try:
        with open(path) as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    obj = json.loads(line)
                except json.JSONDecodeError:
                    continue
                if obj.get("type") != "Point":
                    continue
                metric = obj.get("metric")
                data = obj.get("data", {})
                tags = data.get("tags") or {}
                if metric == "http_req_duration":
                    if tags.get("warmup") == "true":
                        continue
                    durations.append(float(data["value"]))
                elif metric == "http_req_failed":
                    if tags.get("warmup") == "true":
                        continue
                    failed.append(float(data["value"]))
                elif metric == "checks":
                    checks.append(float(data["value"]))
    except OSError:
        return None
    if not durations and not failed:
        return None
    p95 = percentile(durations, 95)
    err = (sum(failed) / len(failed)) if failed else None
    chk = (sum(checks) / len(checks)) if checks else None
    return {
        "p95": p95,
        "error_rate": err,
        "checks_rate": chk,
        "n": len(durations),
    }


def read_status(k6_dir):
    """status.txt lines '<name> PASS|FAIL' written by k6/run-all.sh."""
    status = {}
    try:
        with open(os.path.join(k6_dir, "status.txt")) as f:
            for line in f:
                parts = line.split()
                if len(parts) == 2:
                    status[parts[0]] = parts[1]
    except OSError:
        pass
    return status


def parse_lhci_dir(lhci_dir):
    """Return {category: score_percent} from the first *.report.json found."""
    files = sorted(glob.glob(os.path.join(lhci_dir, "*.report.json")))
    if not files:
        return None
    try:
        with open(files[0]) as f:
            report = json.load(f)
    except (OSError, json.JSONDecodeError):
        return None
    cats = report.get("categories") or {}
    out = {}
    for name, cat in cats.items():
        score = cat.get("score")
        if isinstance(score, (int, float)):
            out[name] = round(score * 100, 1)
    return out or None


def fmt(value, decimals=1, suffix=""):
    if value is None:
        return "n/a"
    return f"{value:.{decimals}f}{suffix}"


def render_section(date, k6_dir, lhci_dir, run_url, commit):
    status = read_status(k6_dir)
    lines = [f"## Performance — {date}", ""]
    lines.append("### k6 API journeys (5 VUs x 30s, gateway :8080)")
    lines.append("")
    lines.append("| Journey | p95 | Error rate | Checks | Result | Threshold |")
    lines.append("|---|---|---|---|---|---|")
    any_missing = False
    for journey in JOURNEYS:
        result = parse_k6_file(os.path.join(k6_dir, f"{journey}.json"))
        verdict = status.get(journey, "UNKNOWN")
        if result is None:
            any_missing = True
            lines.append(f"| {journey} | n/a | n/a | n/a | {verdict} | {THRESHOLDS[journey]} |")
            continue
        lines.append(
            f"| {journey} | {fmt(result['p95'])} ms | "
            f"{fmt(result['error_rate'] * 100 if result['error_rate'] is not None else None, 2, '%')} | "
            f"{fmt(result['checks_rate'] * 100 if result['checks_rate'] is not None else None, 1, '%')} | "
            f"{verdict} | {THRESHOLDS[journey]} |"
        )
    if any_missing:
        lines.append("")
        lines.append("One or more k6 result files were missing — that journey did not run.")
    lines.append("")
    lines.append("### Lighthouse (web :3000/login, median of 3 runs)")
    lines.append("")
    scores = parse_lhci_dir(lhci_dir)
    if scores is None:
        lines.append("Lighthouse: no results (lighthouse-results/*.report.json missing).")
    else:
        lines.append("| Category | Score | Gate |")
        lines.append("|---|---|---|")
        gates = {"performance": ">= 80", "accessibility": "100 (zero violations)",
                 "best-practices": ">= 80 (warn)", "seo": ">= 80 (warn)"}
        for cat in ["performance", "accessibility", "best-practices", "seo"]:
            if cat in scores:
                lines.append(f"| {cat} | {fmt(scores[cat], 1)} | {gates.get(cat, '')} |")
    lines.append("")
    lines.append(f"- Run: {run_url}")
    lines.append(f"- Commit: {commit}")
    lines.append("")
    return "\n".join(lines)


def write_report(path, date, section):
    existing = ""
    if os.path.exists(path):
        with open(path) as f:
            existing = f.read()
    header = f"# Nightly QA — {date}\n\n"
    if not existing:
        # Nightly.yml has not written its report yet; create a minimal one.
        existing = header + "- Verdict: n/a (nightly report not present yet — perf section only)\n\n"
    marker = f"## Performance — {date}"
    if marker in existing:
        # Replace the previous Performance section for this date.
        before, rest = existing.split(marker, 1)
        # Drop everything up to the next "## " heading (or EOF).
        idx = rest.find("\n## ")
        after = rest[idx + 1:] if idx != -1 else ""
        existing = before + section + (after if after.startswith("## ") else "")
        if after and not after.startswith("## "):
            existing = before + section
    else:
        if not existing.endswith("\n"):
            existing += "\n"
        existing += section
    with open(path, "w") as f:
        f.write(existing)


def self_test():
    """Render a section from embedded fake data; exit 0 on success."""
    with tempfile.TemporaryDirectory() as tmp:
        k6_dir = os.path.join(tmp, "k6-results")
        lhci_dir = os.path.join(tmp, "lighthouse-results")
        os.makedirs(k6_dir)
        os.makedirs(lhci_dir)
        # Fake k6 NDJSON: 100 requests per journey, p95 ~ 120ms, no errors.
        for journey in JOURNEYS:
            with open(os.path.join(k6_dir, f"{journey}.json"), "w") as f:
                for i in range(100):
                    dur = 50.0 + (i % 40) * 3.0  # 50..167ms, p95 = 164ms
                    f.write(json.dumps({"type": "Point", "metric": "http_req_duration",
                                        "data": {"value": dur, "tags": {}}}) + "\n")
                    f.write(json.dumps({"type": "Point", "metric": "http_req_failed",
                                        "data": {"value": 0, "tags": {}}}) + "\n")
                    f.write(json.dumps({"type": "Point", "metric": "checks",
                                        "data": {"value": 1, "tags": {}}}) + "\n")
                # One warmup point that must NOT affect p95.
                f.write(json.dumps({"type": "Point", "metric": "http_req_duration",
                                    "data": {"value": 9000.0, "tags": {"warmup": "true"}}}) + "\n")
            with open(os.path.join(k6_dir, "status.txt"), "w") as f:
                for journey in JOURNEYS:
                    f.write(f"{journey} PASS\n")
        with open(os.path.join(lhci_dir, "localhost-3000-login.report.json"), "w") as f:
            json.dump({"categories": {
                "performance": {"score": 0.92},
                "accessibility": {"score": 1.0},
                "best-practices": {"score": 0.95},
                "seo": {"score": 0.9},
            }}, f)
        section = render_section("2026-11-14", k6_dir, lhci_dir,
                                 "https://github.com/TechBalachandar/epitomejob/actions/runs/1",
                                 "abc1234")
        print(section)
        # Assertions on the rendered output. Fake durations are
        # 50 + (i % 40) * 3 ms over 100 points; p95 lands on 161.0 ms.
        assert "| login | 161.0 ms | 0.00% | 100.0% | PASS |" in section, "login row wrong"
        assert "| performance | 92.0 | >= 80 |" in section, "perf score wrong"
        assert "| accessibility | 100.0 | 100 (zero violations) |" in section, "a11y score wrong"
        # Warmup exclusion, proven directly: 9 points at 100ms + 1 warmup
        # point at 9000ms must give p95 == 100.0 (if the warmup point were
        # counted, p95 would be 9000.0).
        warmup_path = os.path.join(k6_dir, "warmup-probe.json")
        with open(warmup_path, "w") as f:
            for _ in range(9):
                f.write(json.dumps({"type": "Point", "metric": "http_req_duration",
                                    "data": {"value": 100.0, "tags": {}}}) + "\n")
            f.write(json.dumps({"type": "Point", "metric": "http_req_duration",
                                "data": {"value": 9000.0, "tags": {"warmup": "true"}}}) + "\n")
        assert parse_k6_file(warmup_path)["p95"] == 100.0, "warmup point leaked into p95"
        print("SELF-TEST OK — warmup points excluded from p95 (161.0ms, not 9000ms).")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--k6-dir", default="k6-results")
    parser.add_argument("--lhci-dir", default="lighthouse-results")
    parser.add_argument("--date", default="")
    parser.add_argument("--run-url", default="")
    parser.add_argument("--commit", default="")
    parser.add_argument("--out", default="")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        return self_test()
    if not (args.date and args.out):
        parser.error("--date and --out are required (or use --self-test)")
    section = render_section(args.date, args.k6_dir, args.lhci_dir,
                             args.run_url, args.commit)
    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    write_report(args.out, args.date, section)
    print(f"Wrote Performance section for {args.date} to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
