"""F14 phone reports (research R4 and R14, contract H2): the JSON the benches leave in files/reports, as the
Markdown tables of specs/014-ondevice-performance/benchmarks.md, so no figure is copied by hand.

The generated part of benchmarks.md sits between two markers; the text around it is written by hand.
"""

import json
from pathlib import Path

START = "<!-- device-report:start -->"
END = "<!-- device-report:end -->"


def load(folder):
    """The reports in [folder], oldest first by their start time."""
    reports = [json.loads(p.read_text(encoding="utf-8")) for p in sorted(Path(folder).glob("f14-*.json"))]
    return sorted(reports, key=lambda r: r.get("started", ""))


def _ms(value):
    return f"{value:,.1f}".replace(",", " ").replace(".", ",").replace(" ", ".")


def _conditions(report):
    conditions = report.get("conditions", {})
    start, end = conditions.get("start", {}), conditions.get("end", {})
    thermal = f"{start.get('thermal', '?')} → {end.get('thermal', '?')}"
    battery = f"{start.get('battery', '?')} → {end.get('battery', '?')} %"
    charging = "cargando" if start.get("charging") else "sin cargar"
    peak = end.get("rssPeakMb")
    return thermal, f"{battery} · {charging}", f"{peak} MB" if peak is not None else "—"


def _value(v):
    if isinstance(v, bool) or not isinstance(v, (int, float)):
        return str(v).lower() if isinstance(v, bool) else str(v)
    if isinstance(v, float):
        return f"{v:.1f}" if abs(v) >= 10 else f"{v:.3f}"
    return str(v)


def _extra(extra):
    """Numbers only: the answers a bench keeps for the desktop are not shown."""
    return " · ".join(f"{k}={_value(v)}" for k, v in extra.items() if not isinstance(v, (list, dict))) if extra else ""


def _label(variant):
    parameters = variant.get("parameters") or {}
    return variant["name"] + (f" ({', '.join(f'{k}={v}' for k, v in parameters.items())})" if parameters else "")


def table(report):
    """One bench: its session line and a row per variant and phase."""
    thermal, battery, peak = _conditions(report)
    runtimes = report.get("runtimes", {})
    models = ", ".join(f"{m['id']} ({m.get('backend', '?')}, {m.get('precision', 'por defecto')})" for m in report.get("models", []))
    lines = [
        f"### {report['bench']} · {report.get('started', '')[:16].replace('T', ' ')}",
        "",
        f"{report.get('device', '?')}, Android API {report.get('android', '?')} · LiteRT-LM {runtimes.get('litertlm', '?')} · "
        f"SQLite-Vector {runtimes.get('sqliteVector', '?')}" + (f" · {models}" if models else ""),
        f"Térmico {thermal} · batería {battery} · pico de RSS {peak} · estado **{report.get('status', '?')}**"
        + (f" ({report['reason']})" if report.get("reason") else ""),
        "",
        "| Variante | Fase | p50 (ms) | p95 (ms) | p99 (ms) | n | Extra |",
        "|---|---|---:|---:|---:|---:|---|",
    ]
    for variant in report.get("variants", []):
        summary = variant.get("summary", {})
        extra = _extra(variant.get("extra", {}))
        if not summary:
            lines.append(f"| {_label(variant)} | — | — | — | — | 0 | {extra} |")
        for i, (phase, figures) in enumerate(summary.items()):
            lines.append(f"| {_label(variant)} | {phase} | {_ms(figures['p50'])} | {_ms(figures['p95'])} | {_ms(figures['p99'])} | "
                         f"{int(figures['n'])} | {extra if i == 0 else ''} |")
    return "\n".join(lines)


def render(reports):
    return "\n\n".join(table(r) for r in reports)


def write(folder, benchmarks):
    """Replaces the generated part of [benchmarks] (creating the markers at its end if missing)."""
    body = render(load(folder))
    path = Path(benchmarks)
    text = path.read_text(encoding="utf-8") if path.exists() else ""
    block = f"{START}\n\n{body}\n\n{END}"
    if START in text and END in text:
        text = text[: text.index(START)] + block + text[text.index(END) + len(END):]
    else:
        text = (text.rstrip() + "\n\n" if text else "") + block + "\n"
    path.write_text(text, encoding="utf-8")
    return path
