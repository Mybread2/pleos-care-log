# Pleos Care & Log

Pleos Connect(AAOS) 차량용 자동 차계부. 기획: PRD v2.7

## 저장소 구조

| 폴더 | 내용 |
|---|---|
| `app/` | Android(AAOS) 앱 소스 |
| `docs/` | 조사·계약·기획 문서. 색인은 [docs/README.md](docs/README.md) |
| `mock-fixtures/` | 11/15 Mock 시연용 차량 이벤트 Fixture와 검증 스크립트 |

## 현재 구현 (백엔드 MVP)

앱 시작 → 차량 값 스냅샷 저장 → 직전 스냅샷과 잔량 비교 → 주유·충전 **추정 기록** 생성 → 60초마다 중간 저장

새 추정 기록이 생기면 정차 중일 때 **음성으로 확인** (TTS 질문 → STT 응답). 5초마다 **케어 넛지** 판단 (정차 중에만).

| 패키지 | 역할 | PRD |
|---|---|---|
| `vehicle/VehicleGateway` | Vehicle SDK 콜백 API → suspend 함수. 연료 %, SoC %, 누적 주행거리 조회 | 기능 1 |
| `core/RefillDetector` | 잔량 차분으로 주유·충전 후보 판정 (순수 Kotlin, 단위 테스트) | 6.5 |
| `data/` | Room: `snapshot`(원시값), `refill_record`(추정 기록 + 사용자 확인 상태), `consumable_reset`(소모품 교체 기준점) | 6.4 |
| `data/TripSummary` | 오늘 주행거리·운행 시간·전비(배터리 감소분 기준), 기록 공백 수 (순수 Kotlin, 단위 테스트) | 5.3, 기능 3 |
| `data/Consumables` | 소모품 주기(예시값)·남은 거리·기한. 기준점 없으면 남은 거리 공란 (순수 Kotlin, 단위 테스트) | 기능 3·4 |
| `collector/SnapshotRecorder` | 스냅샷 저장 + 비교 + 기록 저장을 한 트랜잭션으로 | 6.4 |
| `collector/CollectorService` | 포그라운드 서비스, 주기적 체크포인트 | 기능 1 |
| `voice/SpeechGateway` | Pleos TTS·STT(온디바이스, ko-KR) → suspend 함수. 대화 하나씩 직렬화 | 6.3 |
| `voice/SttWait` | STT 응답 대기: 준비 완료부터 설정 시간(3/5/7초) 재기, 말하는 중이면 끝까지 듣기 (순수 Kotlin, 단위 테스트) | 4장 T_STT |
| `voice/VoiceIntentParser` | 응답 → 수락/거절/취소/오탐/수량 (순수 Kotlin, 단위 테스트) | 6.3 |
| `voice/RefillVoiceConfirm` | 추정 기록 음성 확인. 무응답·거절·취소는 추정 유지 | 시나리오 A·B |
| `nudge/NudgePolicy` | 넛지 판단: 정차, 편의 넛지(열선·공조) 한 주행 2회·15분 간격(설정 30분·끄기), 거절·실행 시 재질문 없음, 공조는 난방 중·24℃ 이상일 때 2℃ 낮추는 제안만 (순수 Kotlin, 단위 테스트) | 기능 5 |
| `nudge/NudgeEngine` | 상태 읽기 → 판단 → 대화 → 이력 저장. 열선·공조는 수락 후 재확인하고 실행. 열선 사전 동의(스마트 자동 케어, 기본 Off) 시 묻지 않고 실행 후 알림 | 기능 5, 6.3 |
| `vehicle/CarState` | AAOS Car API: 정차 여부(UX 제한), 외기온 | 기능 5 |
| `ui/MainActivity` + `HomeScreen`·`RecordsScreen`·`SettingsScreen` | Compose 화면. 홈(확인할 기록·오늘 주행·월간 요약·차량 상태·소모품 1-Click 교체 기록·넛지 이력), 기록(출처 레이블, 맞아요/수량/금액/오탐/되돌리기), 설정(케어 넛지 On/Off, 열선 자동 케어, 편의 제안 간격 15/30분·끄기, 음성 응답 대기 3/5/7초) | 기능 4, 5.1·5.2, 4장 |
| `ui/NumberPadDialog` | 수량·금액 입력용 큰 숫자 패드 (시스템 키보드 대신) | 5.2 |
| `ui/DebugActivity` | 시연·검증용 화면. 설정 → 개발자 화면에서 연다 | - |

## B05 · Mock 기록 생성

`RecordEngine.process(MockInput)`은 B01 v0.2 이벤트에서 Leg, Journey, 주유·충전 후보를 생성합니다. 입력과 기대 출력은 [B04 Fixture](mock-fixtures/fixtures)를 따릅니다. 시연용 F01은 Leg 2건, Journey 1건, 활성 운행 35분, 12km, 주유 후보 1건을 반환합니다.

F03 어댑터는 결과의 `records`를 읽고 `correctAmount`가 반환한 기록을 사용합니다. 원본 이벤트와 사용자 정정 이력은 따로 보존합니다. F04는 `mockFeed.samplingSec`를 전달하면 미관측 600초를 활성 운행에서 제외하고 공백을 별도 반환합니다.

## B06 · Mock 회귀 검증

팀 Android 프로젝트에서 `.\gradlew.bat :app:testDebugUnitTest`로 전체 단위 테스트를 실행합니다.

### 시나리오별 회귀 테스트

| 시나리오 | 입력 | 기대 출력 | 실제 결과 | 판정 |
| --- | --- | --- | --- | --- |
| F01 · ICE 주행·주유 | ICE 시동 전환, Fuel 30% → 75% | Leg 2건, Journey 1건, 35분, 12km. 주유 후보 1건, 수량·금액 미확인 | Fixture의 전체 출력과 일치. 동일 시각 이벤트 순서를 바꿔도 일치 | 통과 |
| F02 · EV 주행·충전 | EV READY 전환, SOC 36% → 80% | Leg 2건, Journey 1건, 35분, 23km. 충전 후보 1건, 배터리 증가량·과금량·금액 미확인 | Fixture의 전체 출력과 일치. 동일 시각 이벤트 순서를 바꿔도 일치 | 통과 |
| F03 · 연료 잔량 변동 | 주행 중 Fuel 증가 및 OFF 전후 소폭 상승 | 35분, 14km, 주유 후보 0건 | 순서를 바꾼 입력까지 Fixture의 전체 출력과 일치 | 통과 |
| F04 · 수집 공백 | 300초 샘플 주기에서 08:10~08:20 동안 신호 미수신 | 공백 600초 별도 기록, Leg 1건, 활성 1,200초, 누적계 차분 13km | 600초를 합산에서 제외. 20분·13km와 공백 기록 모두 일치 | 통과 |
| F05 · 중복 재전송 | F01에 중복 관측·반복 ON을 추가하고 입력 전체를 재전송 | 기록 건수·집계가 F01과 동일 | Leg 2건, Journey 1건, 주유 후보 1건. F01과 전체 출력 일치 | 통과 |
| F06 · 작은 SOC 증가 | EV READY 해제 전후의 소폭 SOC 증가 | 35분, 18km, 충전 후보 0건 | 순서를 바꾼 입력까지 Fixture의 전체 출력과 일치 | 통과 |
| P/ISG 모의 정차 | F01 주행 중 누적계가 그대로인 관측과 반복 ON | Leg를 닫지 않고 900초·1,200초 유지 | 기존 두 Leg의 활성시간 그대로 | 통과 |
| Journey 경계 편집 | F01의 첫 Leg 뒤에 여정 경계 추가 | 5km·900초와 7km·1,200초의 두 여정, 원본 Leg 보존 | 구간 ID, 시간, 거리 및 원본 불변 확인 | 통과 |
| ACC 전환 | F01의 OFF를 ACC로 변경한 입력과 실제 OFF를 추가한 입력 | 둘 다 활성 2,100초. 실제 OFF가 관측된 경우에만 후보 1건 | ACC만 있을 때 0건, OFF 추가 시 주유 후보 1건 | 통과 |
| 금액 정정 후 재처리 | 미확인 → 65,000원 → 67,000원 정정 후 F01 재생 | 67,000원 `USER_INPUT`, 이전 값과 원본 보존 | 최신 금액, 이전 금액, 정정 이력, 원본 이벤트 모두 일치 | 통과 |
| 누적계 관측 없음 | F01에서 누적계 이벤트 전체 제거 | Leg·Journey 거리 및 사건 누적계 미확인 | 해당 필드 모두 `null` | 통과 |
| 종료 시점 관측 누락 | F01의 첫 종료 시점 누적계·Fuel 관측 제거 | 종료 거리 미확인, 근거 없는 주유 후보 0건 | 종료 누적계와 거리 `null`, 주유 후보 0건 | 통과 |
| 이벤트 ID 충돌 | 동일 ID에 서로 다른 값 입력 | 입력 오류 | `IllegalArgumentException` 확인 | 통과 |

### 팀 F03 웹 화면 재진입

2026-11-14에 팀 F03의 [`08e3844` 웹 소스](https://github.com/Mybread2/pleos-care-log/tree/08e384408c1055c1a9f7f5baad28506409dcd789/frontend)를 로컬에서 실행하고 브라우저로 확인했습니다.

| 시나리오 | 입력 | 기대 출력 | 실제 결과 | 판정 |
| --- | --- | --- | --- | --- |
| 초기 금액 정정 | F01 전체 실행 → 주유 기록 → 조회·정정 → 65,000원 적용 | 금액이 `null / UNKNOWN`에서 `65,000원 / USER_INPUT`으로 변경 | 현재 금액과 정정 이력 `correction-1` 일치 | 통과 |
| 금액 수정 후 재진입 | 70,000원으로 수정 → 주유 기록 → 조회·정정 재진입 | 두 화면에 `70,000원 / USER_INPUT` 유지. 정정 이력과 원본 보존 | 두 화면 모두 70,000원 표시. 정정 이력 2건과 65,000원 → 70,000원 변경 확인. 원본 e03/e05 30%, e07 75% 및 주유량 미확인 유지 | 통과 |

재진입은 같은 브라우저 탭에서 화면을 전환하는 경우입니다. 웹 시연 가이드는 새로고침과 시연 초기화가 메모리 기록을 지운다고 명시합니다.

## 빌드

- JDK 17+, Android SDK (compileSdk 35)
- Pleos Vehicle SDK 2.3.2, TextToSpeech·SpeechToText 3.3.1 (`https://nexus-playground.pleos.ai/repository/maven-releases/`)

```bash
./gradlew testDebugUnitTest   # 단위 테스트
./gradlew installDebug        # Pleos Connect Emulator에 설치
./gradlew exportReleaseApk    # 서명한 릴리스 APK → apk/{팀명}_PleosCareLog_release.apk
```

릴리스 서명: `signing/keystore.properties`(+ `carelog-release.jks`)가 있으면 그 키로 서명(APK Signature Scheme v3, Pleos 키 로테이션 요건). 폴더는 git 제외 — **키와 비밀번호는 따로 백업**하고 팀원에게는 직접 전달. 없으면 릴리스 APK는 서명되지 않음. 팀명은 `app/build.gradle.kts`의 `submissionTeamName`.

## 에뮬레이터 시연

1. Pleos Connect Emulator v2.1.0 (API 34) 실행
2. `adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088/ (Virtual Console)
3. 앱 실행 → Virtual Console에서 연료 % 또는 배터리 % 올림 → 설정 → 개발자 화면 → `지금 스냅샷 저장` (또는 60초 주기 저장 대기)
4. 추정 주유·충전 기록이 생성되는지 확인

넛지 시연: 디버그 화면 `외기온 4℃ (열선)` → 5초 안에 "오늘 4도예요. 핸들 열선을 1단으로 켤까요?" / `외기온 -2℃ (결빙)` + `새 주행 시작` → "기온이 낮아 노면이 얼 수 있어요." (외기온은 콘솔에서 못 바꿔서 대체값 사용)

> 창문·기어는 JSON 바디로 바꿉니다: `POST /vehicle/body` `{"window_fl":50}`, `POST /vehicle/gear` `{"gear":"Drive"}` (쿼리 파라미터 아님).

> Virtual Console은 브라우저 탭이 열려 있어야 동작합니다 (페이지가 1초마다 `/browser/session` heartbeat 전송). 탭을 닫으면 값 변경 API가 `emulator_inactive`를 반환합니다.

## 검증 상태 (2026-11-09, Pleos Connect Emulator v2.1.0)

| 항목 | 결과 |
|---|---|
| 단위 테스트 64건 (RefillDetector·MonthlySummary·TripSummary·Consumables·VoiceIntentParser·SttWait·NudgePolicy) | 통과 |
| 오늘 주행 | 콘솔로 12,340→12,360km, 60→56.5kWh → 20.0km · 2분 · 5.7km/kWh. 배터리 변화 없이 거리만 늘면 전비 대신 사유 표시 |
| 소모품 1-Click | [교체함] → 확인 → 12,360km 기준 저장, 12,500km에서 "약 14,860 km 남음 · 2027년 11월까지". EV라 엔진오일 숨김 |
| 공조 상태 읽기 | 설정 온도·제상은 운전석(0x1), **전원·에어컨은 실내 전체 구역(0x75)만 지원** → 운전석 실패 시 전체 구역으로 재조회 |
| 공조 넛지 | 콘솔 에어컨 Off·26℃, 외기온 4℃ → "실내 설정 온도가 26도예요. 24도로 낮출까요?" → 무응답 → 온도 그대로(26℃), `NO_RESPONSE` |
| 공조 제어 | `setHvacTemperature` 26→24℃ → 콘솔·재조회 24.0 확인 (수락 후 실행 경로) |
| 스마트 자동 케어(열선) | 설정에서 동의 On → 외기온 4℃ → 묻지 않고 1단 실행, "미리 동의하신 대로…" 안내, 콘솔 Level1, `AUTO_EXECUTED` |
| 릴리스 APK | `exportReleaseApk` → v3 서명(apksigner 확인, minSdk 28이라 v2는 빌드 도구가 생략) → 에뮬 설치·실행, 차량 값 수신 정상. 릴리스는 debuggable 아님(`run-as` 불가) |
| 설정: 편의 제안 간격·음성 응답 대기 | 선택값 저장·앱 재시작 후 유지. 끄기 + 새 주행 + 외기온 4℃ → 열선 제안 없음, -2℃ → 결빙 안내는 발화 |
| STT 대기 기준 | STT 준비(`ReadyToStartRecognition`)가 요청 후 **약 2.2초** 뒤에 옴 → 3초 설정에서 준비 후 3.005초에 시간 초과. 예전처럼 요청 시점부터 쟀다면 실제로 들을 시간은 설정보다 2초 넘게 짧았음 |
| Vehicle SDK 값 수신 | SoC %, 배터리 잔량, 누적 주행거리 수신 확인 |
| 충전 감지 | Virtual Console에서 SoC 20%→80%, 15.5→61.9kWh → `EV 20%→80% (+60%p) 배터리 +46.4kWh` 추정 기록 생성 |
| 충전량 | 배터리 잔량(Wh) 차이 = **배터리 순증량**. 충전기 과금량과 다름 (PRD 6.5) |
| 앱 강제 종료 후 재실행 | 기록 유지, 중복 없음, 수집 서비스 재시작 |
| 단위 (V5 일부) | `getEvBatteryLevel` = **Wh** (콘솔 61.9kWh → 61900), `getCurrentDistanceDriven` = **km** |
| 연료 % | 에뮬레이터 차량(EV)에서 미지원 (`property ID is not in carPropertyConfig list`) |
| 배터리 용량 | `SecurityException` — 추가 권한 필요, 확인 중 |
| 절전 복귀·종료 저장 (V2·V3) | 미검증 |
| TTS | 온디바이스 ko-KR(seoyoon) 발화, onDone 수신. 리스너는 `initialize` 성공 **후**에 붙여야 함 (먼저 붙이면 SDK 내부 크래시) |
| STT | 마이크 녹음 시작·인식 루프 동작. 말할 입력이 없어 시간 초과 → 무응답 처리 확인 (대기 시간은 위 'STT 대기 기준'). **실제 인식은 미검증** (호스트 마이크 연결 필요) |
| 충전 음성 확인 | SoC 80→95% 감지 → "약 11.6킬로와트시로 추정해 저장했어요. 수량이 맞나요?" → 무응답 → `UNCONFIRMED` 유지 |
| 열선 넛지 | 외기온 4℃ → 제안 발화 → 무응답 → 제어 없음, `NO_RESPONSE` 이력 |
| 열선 제어 | `setHvacSteeringWheelHeat(1)` → 콘솔 `Level1`, 재조회 1 확인 (수락 후 실행 경로) |
| 결빙 안내 | 외기온 -2℃ → 1회 발화, 이어서 열선 제안 붙지 않음 |
| 정차 판정·외기온 | UX 제한 기준 정차=true(P단), AAOS 외기온 25.0℃ 수신 |
| 화면 | 홈·기록·설정 렌더링, 금액 15,000원 입력 → [사용자 입력] 표시, 맞아요 → 확인됨, 충전 안 했어요 → 오탐·월간 요약에서 제외, 넛지 스위치 저장 |
| 주행 중 화면 | DO 미선언 → D단(속도 0 포함)에서 플랫폼이 "주행 중에는 사용할 수 없습니다"로 차단, P단 복귀 시 화면 복원 |
| 창문 하차 넛지 | 창문 열림 읽기 확인. 벨트 체결→해제 순간이 조건인데 콘솔에 벨트 제어가 없어(기본 해제) 발화는 단위 테스트로만 확인 |
