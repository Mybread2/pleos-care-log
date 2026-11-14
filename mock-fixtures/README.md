# B04 · ICE/EV Mock 이벤트·예외 Fixture (v0.2, 2026-11-14)

B01 공통 데이터 계약 v0.2를 따르는 재현용 차량 이벤트 세트. 백엔드 B(B05 기록 로직)와 풀스택(Mock adapter)이 같은 입력·같은 기대 출력으로 맞춰 보기 위한 것이다.

> **모든 값은 가상 Mock 데이터다.** 실제 차량에서 관측한 이벤트가 아니다. `environment=MOCK`이 출처 표기이고, `provenance=OBSERVED`는 「Mock 시나리오의 가상 신호로 관측」이라는 뜻이다(B01 §3).

## 파일 구조

Fixture 하나가 JSON 파일 하나다. B01 §4 입력 형식(`schemaVersion`·`environment`·`vehicleId`·`powertrain`·`events`)에 아래 키를 더했다.

| 키 | 내용 |
|---|---|
| `fixtureId`, `title`, `covers` | 식별자 · 시나리오 · 덮는 항목 |
| `units` | 신호별 단위와 `at`의 의미 |
| `notes` | 가정과 판정 근거 |
| `expected` | RecordEngine 기대 출력 (11/15 시연 시나리오 v0.2 §3 형식) |
| `mockFeed.samplingSec` | F04만. Mock adapter의 샘플 주기 |
| `intentionalDuplicates` | F05만. eventId 중복이 의도된 것임을 표시 |

**Mock adapter**는 `events`만 읽어 재생하면 되고, **기록 로직**은 `events` → 출력을 만들어 `expected`와 비교하면 된다.

## Fixture 목록

| ID | 차종 | 시나리오 | 기대 결과 |
|---|---|---|---|
| F01 | ICE | B01 §4 원본. 집→주유소→회사 | Leg 2 / Journey 1 / 35분 / 12km / 주유 후보 1(L·금액 미확인) |
| F02 | EV | READY 기반 주행 + 30분 정차 중 SOC 36→80 | Leg 2 / 35분 / 23km / 충전 후보 1(kWh·금액 미확인) |
| F03 | ICE | 주행 중 연료 +6%p, 5분 OFF 뒤 +2%p | Leg 2 / 35분 / 14km / **주유 후보 없음** |
| F04 | ICE | 시동 ON 중 10분 수신 공백 | Leg 1 / **20분**(30분−공백 10분) / 13km / 공백 1건 |
| F05 | ICE | F01 + 재전송·중복 관측·시동 ON 반복 4건 | **F01과 완전히 같음** |
| F06 | EV | READY false 5분 뒤 SOC +1%p | Leg 2 / 35분 / 18km / **충전 후보 없음** |

과제 산출물 항목과의 대응:

| 과제 항목 | Fixture |
|---|---|
| 시동 ON/OFF · EV READY | F01·F03·F04·F05 / F02·F06 |
| 거리 | 전부 |
| Fuel/SOC 증가 | F01·F02(후보 생성), F03·F06(증가했지만 후보 아님) |
| 주유 금액 미확인 | F01·F05(`amountKrw`·`quantityLiters` = null/UNKNOWN), F02(EV 금액) |
| 짧은 OFF 정차 | F01(10분), F03(5분), F06(READY false 5분) |
| 수신 공백 / 중복 입력 | F04 / F05 |

## 검증 (반복 실행 결과 동일)

```
python verify_fixtures.py
```

| 검사 | 내용 |
|---|---|
| 형식 | B01 허용 값 · `environment=MOCK` · 단위 표기 · 시각 +09:00 · % 0~100 |
| 기대 출력 | 참조 계산 결과가 `expected`와 같음 |
| 재현성 | 3회 반복 · 같은 시각 이벤트 순서를 뒤집음 · 임계값 3~44%p 전 범위 → 모두 같은 결과 |

2026-11-14 결과: **6/6 PASS**. 일부러 틀린 사본(운행시간 2700초, F03 연료 +4%p)은 FAIL로 잡히는 것도 확인했다.

참조 계산은 기대값을 검산하려는 것이며 B05 RecordEngine을 대신하지 않는다. 주유·충전 임계값은 미확정이라(B01) 기대 출력이 **3~44%p 어디서든 같도록** Fixture 값을 골랐고, 스크립트가 전 범위를 돌려 이를 확인한다.

## Fixture가 둔 가정 (B05/B06 합의 필요)

B01에 없는 부분은 아래처럼 가정했다. 팀 결정이 다르면 해당 Fixture의 `expected`만 고치면 된다.

1. **주유·충전 후보는 전원 OFF 구간을 사이에 둔 증가만.** OFF 시점 잔량과 ON 뒤 첫 잔량을 비교한다. 주행 중 증가(F03 +6%p)는 후보로 만들지 않는다. 후보는 항상 `DETECTED_UNCONFIRMED`이며 자동 확정은 없다.
2. **Journey 묶음:** `MOCK_SCENARIO`는 Fixture 하나의 모든 Leg를 Journey 하나로 묶는다. 자동 병합 규칙이 아니다.
3. **수신 공백:** 시동 ON 중 연속 이벤트 간격이 `mockFeed.samplingSec`보다 길면 그 사이 전체를 공백으로 보고, 운행시간에 보충하지 않는다. 거리는 누적 주행거리 양 끝의 관측값 차이라 공백 구간의 이동도 포함한다.
4. **중복:** 같은 `eventId`이거나 `signal`·`at`·`value`가 모두 같으면 첫 건만 처리한다. 이미 ON인데 ON이 다시 오면 무시한다.
5. **같은 시각 이벤트:** 잔량·주행거리 값을 먼저 반영하고 전원 상태 전환을 처리한다. 그래서 순서가 바뀌어도 결과가 같다.

## B01 확장 제안 (B01 v0.2에 없는 이름)

| 이름 | 쓰는 곳 | 이유 |
|---|---|---|
| `kind=CHARGE` | F02 | B01 §5에 EV 확장 가능성만 언급됨 |
| `fields.batteryAddedKwh` / `fields.billedKwh` | F02 | B01 §5: 배터리 증가 에너지와 충전기 과금 에너지는 별개 항목. B01 신호에 용량·Wh가 없어 둘 다 null/UNKNOWN |
| `observationGaps[]` (`from`·`to`·`durationSec`) | F04 | FR-04 수집 공백 표시. `mockFeed`가 있는 Fixture에서만 출력해서, 나머지 출력은 B01 형태 그대로 |

## 넣지 않은 것 (정책 미결정)

- `IGNITION=ACC`의 운행 포함 여부
- 같은 `eventId`에 다른 내용이 오는 충돌 처리
- 수신 공백 중 연료가 크게 오른 경우(OFF를 못 받았을 가능성)의 후보 처리 — PRD 6.5 「근거 부족 후보는 보존, 부족 수치는 공란」 방향만 있음
- 사용자 금액 정정(`UserCorrection`) — 시연 시나리오 v0.2 §4에 이미 예시가 있음
