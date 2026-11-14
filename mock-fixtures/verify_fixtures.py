"""B04 Mock Fixture 검증기 (표준 라이브러리만 사용).

확인하는 것:
  1. 형식 — B01 v0.2 허용 값, 단위 표기, environment=MOCK, 시각 +09:00
  2. 기대 출력 — 아래 참조 계산 결과가 각 Fixture의 expected와 같은가
  3. 재현성 — 3회 반복 · 같은 시각 이벤트 순서 뒤집기 · 임계값 3~44%p 전 범위에서 결과가 같은가

참조 계산은 Fixture 기대값의 산수를 검산하려는 것이며 B05 RecordEngine 구현을 대신하지 않는다.
실행: python verify_fixtures.py
"""
import json
import sys
from datetime import datetime, timedelta
from pathlib import Path

FIXTURE_DIR = Path(__file__).parent / "fixtures"
KST = timedelta(hours=9)
POWER_SIGNAL = {"ICE": "IGNITION", "EV": "READY"}
LEVEL_SIGNAL = {"ICE": "FUEL_PERCENT", "EV": "SOC_PERCENT"}
NUMERIC = {"ODOMETER_KM", "FUEL_PERCENT", "SOC_PERCENT"}
# 주유·충전 임계값은 미확정(B01). 이 범위 어디서든 기대 출력이 같아야 한다.
THRESHOLD_RANGE = range(3, 45)


def validate(fx):
    errors = []
    for key in ("fixtureId", "schemaVersion", "environment", "vehicleId", "powertrain", "units", "events", "expected"):
        if key not in fx:
            errors.append(f"필수 키 없음: {key}")
    if errors:
        return errors
    if fx["schemaVersion"] != "0.2":
        errors.append("schemaVersion != 0.2")
    if fx["environment"] != "MOCK":
        errors.append("environment != MOCK (출처=mock 표기 누락)")
    pt = fx["powertrain"]
    if pt not in POWER_SIGNAL:
        return errors + [f"powertrain 허용 값 아님: {pt}"]
    allowed = {POWER_SIGNAL[pt], LEVEL_SIGNAL[pt], "ODOMETER_KM"}
    for sig in allowed:
        if sig not in fx["units"]:
            errors.append(f"단위 표기 없음: {sig}")
    ids = [e["eventId"] for e in fx["events"]]
    if len(ids) != len(set(ids)) and not fx.get("intentionalDuplicates"):
        errors.append("eventId 중복 (의도한 중복이면 intentionalDuplicates=true)")
    for e in fx["events"]:
        where = f"{e.get('eventId')}:"
        try:
            at = datetime.fromisoformat(e["at"])
            if at.utcoffset() != KST:
                errors.append(f"{where} 시각이 +09:00 아님")
        except (KeyError, ValueError):
            errors.append(f"{where} at 형식 오류")
        sig, val = e.get("signal"), e.get("value")
        if sig not in allowed:
            errors.append(f"{where} {pt}에 허용되지 않는 signal {sig}")
        elif sig == "IGNITION" and val not in ("ON", "OFF", "ACC"):
            errors.append(f"{where} IGNITION 값 {val}")
        elif sig == "READY" and not isinstance(val, bool):
            errors.append(f"{where} READY 값 {val}")
        elif sig in NUMERIC and val is not None:
            if isinstance(val, bool) or not isinstance(val, (int, float)):
                errors.append(f"{where} 숫자 아님 {val}")
            elif sig != "ODOMETER_KM" and not 0 <= val <= 100:
                errors.append(f"{where} % 범위 밖 {val}")
    return errors


def dedupe(events):
    """같은 eventId 재전송, 또는 signal·at·value가 같은 중복 관측은 첫 건만 남긴다."""
    seen_ids, seen_obs, out = set(), set(), []
    for e in events:
        obs = (e["signal"], datetime.fromisoformat(e["at"]), json.dumps(e["value"]))
        if e["eventId"] in seen_ids or obs in seen_obs:
            continue
        seen_ids.add(e["eventId"])
        seen_obs.add(obs)
        out.append(e)
    return out


def is_on(pt, value):
    return value == "ON" if pt == "ICE" else value is True


def energy_event(pt, n, at, odo, before, after):
    if pt == "ICE":
        kind, prefix = "REFUEL", "fuel"
        extra = {"quantityLiters": None, "amountKrw": None}
    else:
        kind, prefix = "CHARGE", "charge"
        extra = {"batteryAddedKwh": None, "billedKwh": None, "amountKrw": None}
    fields = {
        "levelBeforePercent": {"value": before, "provenance": "OBSERVED"},
        "levelAfterPercent": {"value": after, "provenance": "OBSERVED"},
    }
    fields.update({k: {"value": None, "provenance": "UNKNOWN"} for k in extra})
    return {
        "id": f"{prefix}-{n}", "kind": kind, "detectedAt": at, "occurredAt": None,
        "odometerKm": odo, "recordStatus": "DETECTED_UNCONFIRMED", "fields": fields,
    }


def compute(fx, threshold, reverse_same_time=False):
    pt = fx["powertrain"]
    power_sig, level_sig = POWER_SIGNAL[pt], LEVEL_SIGNAL[pt]
    sampling = fx.get("mockFeed", {}).get("samplingSec")

    events = dedupe(fx["events"])
    if reverse_same_time:
        events = list(reversed(events))
    events.sort(key=lambda e: datetime.fromisoformat(e["at"]))  # stable: 같은 시각은 입력 순서

    groups = []  # [(datetime, at 문자열, [이벤트])]
    for e in events:
        t = datetime.fromisoformat(e["at"])
        if groups and groups[-1][0] == t:
            groups[-1][2].append(e)
        else:
            groups.append((t, e["at"], [e]))

    powered, odo, level = False, None, None
    leg, legs, gaps, energy = None, [], [], []
    level_at_off, compare_pending, last_t = None, False, None

    for t, at, evs in groups:
        level_seen = False
        for e in evs:  # 값 먼저 반영 → 같은 시각 순서와 무관
            if e["signal"] == "ODOMETER_KM":
                odo = e["value"]
            elif e["signal"] == level_sig:
                level, level_seen = e["value"], True

        if powered and sampling and last_t is not None and (t - last_t[0]).total_seconds() > sampling:
            gaps.append({"from": last_t[1], "to": at, "durationSec": int((t - last_t[0]).total_seconds())})

        power_vals = [is_on(pt, e["value"]) for e in evs if e["signal"] == power_sig]
        if power_vals and power_vals[-1] != powered:
            powered = power_vals[-1]
            if powered:
                leg = {"startT": t, "startedAt": at, "startOdometerKm": odo, "gapStart": len(gaps)}
                compare_pending = level_at_off is not None
            else:
                leg_gap = sum(g["durationSec"] for g in gaps[leg["gapStart"]:])
                legs.append({
                    "id": f"leg-{len(legs) + 1}",
                    "startedAt": leg["startedAt"], "endedAt": at,
                    "startOdometerKm": leg["startOdometerKm"], "endOdometerKm": odo,
                    "activeDurationSec": int((t - leg["startT"]).total_seconds()) - leg_gap,
                    "distanceKm": odo - leg["startOdometerKm"],
                })
                leg, level_at_off, compare_pending = None, level, False

        # 주유·충전 후보: 전원 OFF 구간을 사이에 두고, ON 이후 첫 잔량이 OFF 시점보다 threshold 이상 높을 때만
        if powered and compare_pending and level_seen:
            if level is not None and level - level_at_off >= threshold:
                energy.append(energy_event(pt, len(energy) + 1, at, odo, level_at_off, level))
            compare_pending = False

        last_t = (t, at) if powered else None

    out = {"schemaVersion": fx["schemaVersion"], "environment": fx["environment"], "vehicleId": fx["vehicleId"], "legs": legs}
    out["journeys"] = [{
        "id": "journey-1", "legIds": [l["id"] for l in legs], "groupingBasis": "MOCK_SCENARIO",
        "activeDurationSec": sum(l["activeDurationSec"] for l in legs),
        "distanceKm": sum(l["distanceKm"] for l in legs),
    }] if legs else []
    out["energyEvents"] = energy
    if sampling:
        out["observationGaps"] = gaps
    return out


def canon(obj):
    return json.dumps(obj, ensure_ascii=False, sort_keys=True)


def check(path):
    fx = json.loads(path.read_text(encoding="utf-8-sig"))  # 메모장 저장(BOM)도 허용
    problems = validate(fx)
    if problems:
        return problems
    expected = canon(fx["expected"])
    first = canon(compute(fx, THRESHOLD_RANGE[0]))
    if first != expected:
        return [f"기대 출력 불일치\n  expected: {expected}\n  computed: {first}"]
    if any(canon(compute(fx, THRESHOLD_RANGE[0])) != first for _ in range(3)):
        problems.append("반복 실행 결과가 다름")
    if canon(compute(fx, THRESHOLD_RANGE[0], reverse_same_time=True)) != first:
        problems.append("같은 시각 이벤트 순서에 따라 결과가 다름")
    bad = [th for th in THRESHOLD_RANGE if canon(compute(fx, th)) != first]
    if bad:
        problems.append(f"임계값 {bad[0]}~{bad[-1]}%p에서 결과가 달라짐")
    return problems


def main():
    paths = sorted(FIXTURE_DIR.glob("F*.json"))
    failed = 0
    for path in paths:
        problems = check(path)
        failed += bool(problems)
        print(f"[{'FAIL' if problems else 'PASS'}] {path.name}")
        for p in problems:
            print(f"    - {p}")
    print(f"\n{len(paths) - failed}/{len(paths)} PASS "
          f"(검사: 형식 · 기대 출력 · 3회 반복 · 같은 시각 순서 뒤집기 · 임계값 {THRESHOLD_RANGE[0]}~{THRESHOLD_RANGE[-1]}%p)")
    return 1 if failed or not paths else 0


if __name__ == "__main__":
    sys.exit(main())
