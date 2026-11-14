# Pleos SDK 필수 신호 지원범위 조사 (2026-11-14)

> 범위: 문서 조사와 SDK 바이너리 분석만 했다. 에뮬레이터 결과는 2026-11-09 기록을 인용했고 이번에 새로 돌리지 않았다. 실차는 전부 미검증이다.
> 기준 버전: Vehicle SDK 2.3.2(공식 문서 기준 버전과 같음 [9]), Pleos Connect Emulator 2.1.0 / API 34 [15]
> 과제가 참고하라고 한 SDK-01~03 검증 계획·『02. SDK·기술 검증』(팀 노션)은 게스트 권한 문제로 접근하지 못해 대조하지 않았다.

## PM 공유: 핵심 차단 위험

1. **시동 ON/OFF·EV READY를 받는 Pleos SDK API가 없다.** 상태 enum만 있고 이를 반환하는 메서드는 0개다. 남은 경로는 AAOS `IGNITION_STATE`(일반 권한)인데, 에뮬에서 값이 오는지와 READY가 어떤 값으로 오는지는 미검증이다. 실패하면 「앱 실행 구간 + 주행거리 변화」로 주행 구간을 추정한다(PRD V1~V3 대안).
2. **ICE 연료 L와 탱크 용량은 Pleos SDK에 없다(%만 있음).** AAOS `FUEL_LEVEL`(사용자 동의가 필요한 dangerous 권한)으로만 받을 수 있고, 에뮬 차량이 EV라 12월까지 실동작을 확인할 수 없다. ICE는 Mock fixture로 시연하고 「시연 데이터」라고 표기한다.
3. **배터리 용량 API는 `CAR_ENERGY`가 아니라 Pleos `CAR_INFO` 권한을 요구한다.** MVP 매니페스트에는 이 권한이 없어서, 에뮬에서 난 SecurityException의 유력한 원인으로 본다(재현은 미검증).
4. 12월 2차 PT는 에뮬레이터 시연이고 실차는 필요 없다. 다만 「문서에서 지원하지 않는 기능은 사용 불가」이고 기술지원 창구도 없으므로, 비공개 경로(내부 AIDL, Pleos 전용 ID)는 쓰지 않는다 [16].

## 근거 수준 표기

- **(A)** 공식 문서에 명시: 아래 링크 번호 [n]
- **(B)** SDK 바이너리(javap)로 시그니처만 확인: Vehicle-2.3.2.aar, vehicle-data-1.1.11.aar, vehicle-aidl-1.1.1.aar
- **(C)** 에뮬레이터 실동작: 2026-11-09 기록만 인용
- **(D)** 실차: 전부 미검증
- 권한 표기에서 `sig/priv`는 protectionLevel `signature|privileged`를 뜻한다. `—`는 해당 근거가 없다는 뜻이다.

## 매트릭스

| 신호 | 구분 | 표준 AAOS (프로퍼티·타입·단위 / 권한·protectionLevel) | Pleos SDK API (메서드·반환형 / Pleos 권한) | (A) 공식 문서 | (B) javap | (C) 에뮬 11/09 | (D) 실차 | 판정 | 12월 위험 / 대안 |
|---|---|---|---|---|---|---|---|---|---|
| 시동 ON/OFF | ICE | `IGNITION_STATE` Integer, ONCHANGE, 값 0~5(UNDEFINED/LOCK/OFF/ACC/ON/START) / `CAR_POWERTRAIN` = **normal** | **없음** | AAOS 프로퍼티·권한·protectionLevel 명시 [1][2][5][6]. Pleos 문서에는 시동 API가 없고, Virtual Console 문서에도 전원 도메인이 없음 [9][14] | `IgnitionState` enum과 `VehicleIgnitionStateValue`(0~5)는 있지만 참조하는 API 클래스가 0개. `PleosPropertyIds.IGNITION_STATE`·`ENGINE_STATE`도 이름만 있고 어떤 API도 쓰지 않음 | 미검증. 콘솔 `/vehicle/power-system/simulator`(Off/Ready/On)는 있지만 앱이 받는지는 확인 안 함 | 미검증 | Pleos SDK로는 불가. AAOS 경로는 문서상 가능하나 실동작 미검증 | AAOS getProperty·콜백을 콘솔 Off/Ready/On으로 1회 확인(11/15 이후). 실패하면 앱 시작·종료 + 주행거리 변화로 추정. 실차에서는 OFF 시 IVI도 같이 꺼져 OFF 이벤트를 못 받을 수 있음(추론) |
| EV READY | EV | 별도 READY 프로퍼티 없음. `IGNITION_STATE`의 어느 값으로 오는지는 문서에 없음 / 권한은 위와 같음 | **없음** | `VehicleIgnitionState`에 READY 값이 없음 → EV 매핑은 문서에 없음 [2] | `PleosPropertyIds.EV_POWER_STATE`는 이름만 있고 public API는 없음 | 미검증 | 미검증 | SDK로는 불가, AAOS 경로는 미검증 | 위 실험에서 콘솔 Ready가 ON(4)으로 오는지 함께 기록. 오지 않으면 SOC·주행거리 변화로 대체 |
| Odometer | 공통 | `PERF_ODOMETER` Float km, CONTINUOUS / Android 14: `CAR_MILEAGE` = **sig/priv**(서드파티 불가). AOSP main(Android B 플래그)에서 `CAR_MILEAGE_3P` = dangerous가 추가됐지만 API 34에는 없음 | `Odometer.getCurrentDistanceDriven` → Float(콜백), `registerDistanceDriven` / `CAR_DRIVE` | AAOS 단위·권한 [3][5][6][7][8]. Pleos: Odometer 도메인 = `CAR_DRIVE` [10]. 메서드 레퍼런스 페이지는 본문이 비어 있음 [17] | 내부에서 `PERF_ODOMETER`를 Vehicle Service에 요청하고 `@RequiresCustomPermission(CAR_DRIVE)`가 붙어 있음 → Pleos 서비스가 `CAR_MILEAGE`를 대신 쥐는 우회 구조 | 동작, 단위 km | 미검증 | **가능**(SDK 경로) | 위험 낮음. AAOS로 직접 읽는 것은 권한상 불가하니 SDK 경로로 고정 |
| Fuel % | ICE | % 프로퍼티 없음(`FUEL_LEVEL`÷`INFO_FUEL_CAPACITY`로 계산해야 함) | `Fuel.getFuelPercent` → Int(콜백), `registerFuelPercent` / `CAR_ENERGY` | Fuel 도메인 = `CAR_ENERGY` [10]. 값 범위·단위는 문서에 없음(레퍼런스 본문 비어 있음) [17] | Pleos 전용 `FUEL_PERCENT` ID를 씀(AAOS `FUEL_LEVEL` 아님) | 에뮬 차량이 EV라 미지원 | 미검증 | 문서·바이너리상 있음, 실동작 미검증 | 에뮬로 검증 불가 → fixture로 시연하고 표기. 0~100 가정은 V5에서 범위 검사로 방어 |
| Fuel L | ICE | `FUEL_LEVEL` Float **mL**, CONTINUOUS / `CAR_ENERGY` = **dangerous**(런타임 동의) | **없음** | AAOS 단위·권한, EV에는 구현 안 됨 [4]. Pleos 정책: 「연료량 등 표준 데이터는 AAOS Car API를 쓰고 SDK는 중복 제공하지 않음」 [11] | `PleosPropertyIds.FUEL_LEVEL`은 이름만 있고 API는 없음 | 미검증(EV 에뮬) | 미검증 | AAOS 경로만 가능, 미검증 | 런타임 권한 요청 코드가 필요함(현 MVP에는 `requestPermissions`가 없음). 검증 전에는 %만 표시하고 L·금액은 공란(V5 대안) |
| SOC % | EV | % 프로퍼티 없음 | `EvBattery.getEvBatteryStateOfCharge` → Int(콜백), `registerEvBatteryStateOfCharge` / `CAR_ENERGY` | EvBattery 도메인 = `CAR_ENERGY` [10]. 구독 API는 v2.3.1 신규 [13] | Pleos 전용 `EV_BATTERY_SOC` ID | 20→80% 변화 감지 | 미검증 | **가능** | 위험 낮음 |
| SOC Wh | EV | `EV_BATTERY_LEVEL` Float **Wh**, CONTINUOUS / `CAR_ENERGY` = **dangerous** | `EvBattery.getEvBatteryLevel` → Float(콜백), **deprecated** / `CAR_ENERGY` | AAOS 단위·권한 [4]. Pleos 릴리스노트: `EvBatteryLevelListener`는 「Hidden from docs」 [13] | deprecated 메시지 「use the Google EV_BATTERY_LEVEL property」. 내부 ID는 AAOS `EV_BATTERY_LEVEL` | 동작, 단위 Wh | 미검증 | 가능(deprecated) | 12월에는 동작이 확인된 Pleos getter를 유지. AAOS로 직접 읽으려면 dangerous 런타임 권한이 필요하고 미검증 |
| 배터리 용량 | EV | `INFO_EV_BATTERY_CAPACITY` Float Wh(정격), STATIC / `CAR_INFO` = **normal**. 현재 가용 용량은 `EV_CURRENT_BATTERY_CAPACITY` Float Wh / `CAR_ENERGY` = dangerous | `EvBattery.getEvBatteryCapacity` → Float(콜백), **deprecated** / **Pleos `CAR_INFO`** | AAOS 단위·권한 [4]. Pleos: 「API마다 권한이 다를 수 있으니 @RequiresPermission 확인」 [10] | `@RequiresCustomPermission(CAR_INFO)`, 내부 ID `INFO_EV_BATTERY_CAPACITY`, deprecated → Google 프로퍼티 사용 권고 | SecurityException. MVP 매니페스트에 Pleos `CAR_INFO`가 없어 이것이 유력한 원인(재현은 미검증) | 미검증 | 조건부(권한 추가 후 재확인) | Pleos `CAR_INFO`를 선언하고 1회 확인. 안 되면 AAOS `INFO_EV_BATTERY_CAPACITY`(normal이라 설치 시 부여). 둘 다 안 되면 사용자 입력·차종 기본값(V5) |
| 연료탱크 용량 | ICE | `INFO_FUEL_CAPACITY` Float **mL**, STATIC / `CAR_INFO` = **normal** | **없음** | AAOS 단위·권한 [4] | `PleosPropertyIds.INFO_FUEL_CAPACITY`는 이름만 있고 API는 없음 | 미검증(EV 에뮬) | 미검증 | AAOS 경로만 가능, 미검증 | fixture 값 + 사용자 입력으로 대체 |
| 권한(서드파티 부여) | 공통 | `CAR_INFO`·`CAR_POWERTRAIN`·`CAR_EXTERIOR_ENVIRONMENT` = normal(설치 시 자동) / `CAR_ENERGY` = dangerous(런타임 동의) / `CAR_MILEAGE` = sig/priv(불가) / `CAR_MILEAGE_3P` = dangerous(main에만 있음) | `pleos.car.permission.CAR_*`(읽기)와 `CONTROL_CAR_*`(제어)를 매니페스트에 선언 | AAOS [5][6][7]. Pleos 선언 방식·도메인 표 [10]. **Pleos 권한의 protectionLevel, 자동 부여 여부, 심사 여부는 문서에 없음.** SDK에 없는 특수 프로퍼티는 「기술지원팀 검토 요청」 [12]인데 공모전에는 기술지원 창구가 없음 [16] | `PleosPermission` 상수 33개(CAR_* 11, CONTROL_CAR_* 11, PRIVATE_CAR_* 11). PRIVATE_* 용도는 불명 | 매니페스트 선언만으로(런타임 요청 코드 없이) `CAR_ENERGY`·`CAR_DRIVE` 읽기와 열선 제어(`CONTROL_CAR_CLIMATE`) 동작. AAOS `CAR_EXTERIOR_ENVIRONMENT`로 `ENV_OUTSIDE_TEMPERATURE` 읽기 동작 | 미검증 | 에뮬에서는 OK, 실차·출시는 미검증 | 에뮬 시연에는 차단 요소 없음. AAOS dangerous 권한(`CAR_ENERGY`)을 쓰려면 런타임 요청 UI를 추가해야 함 |
| 에뮬레이터 지원 | 공통 | VHAL을 쓰려면 cold boot 필수(quick boot면 VHAL 사용 불가) [15] | Pleos Connect Emulator 2.1.0, API 34 시스템 이미지 [15] | Virtual Console 도메인은 Motion(기어·브레이크·조향각·주행거리)과 Energy(잔여 주행거리·배터리 용량·충전 상태 등)이고, 전원·시동 도메인은 기재 없음 [14]. 2차 PT는 에뮬 시연, 실차 불필요 [16] | — | 에뮬 차량은 EV(연료 API 미지원). 문서에 없는 콘솔 API `/vehicle/power-system/simulator`(Off/Ready/On)가 있음 | 해당 없음 | EV 신호는 시연 가능. ICE·시동은 시연 근거 없음 | ICE·시동은 Mock fixture로 시연하고 「시연 데이터」로 표기(PRD 9.1 원칙) |

관련 PRD 9.1 항목: 시동/READY → V1·V2·V3, 단위·용량 → V5, 권한 → V1

## 쓰면 안 되는 경로

- **`vehicle-aidl`의 `IVehicleService.getPropertyValue(String, int, int, …)`를 직접 호출해 Pleos 전용 ID(`IGNITION_STATE`, `EV_POWER_STATE` 등)를 요청하는 방식** (B): 범용 IPC라 기술적으로는 열려 있어 보이지만 문서에 없다. 공모전 규정 「문서에서 지원되지 않는 기능은 사용할 수 없습니다」 [16]에 걸린다.
- **AAOS `PERF_ODOMETER` 직접 읽기**: API 34에서는 `CAR_MILEAGE`(sig/priv)가 필요해 서드파티 앱에 부여되지 않는다 [3][6][8].

## 11/15 이후 확인할 실동작 항목 (문서 조사로는 판정 불가)

1. AAOS `IGNITION_STATE`: `CAR_POWERTRAIN`을 선언하고 getProperty·구독한 뒤 콘솔 power-system을 Off/Ready/On으로 전환해 본다 → V1~V3
2. Pleos `CAR_INFO`를 선언한 뒤 `getEvBatteryCapacity`를 다시 호출해 본다 → V5
3. (선택) AAOS `EV_BATTERY_LEVEL`·`INFO_EV_BATTERY_CAPACITY` 직접 읽기: `CAR_ENERGY` 런타임 동의 흐름이 Pleos에서 뜨는지 확인

## 못 찾은 것 (미검증)

- Pleos 권한(`pleos.car.permission.*`)의 protectionLevel, 실차에서의 부여 방식, 앱마켓 심사 조건
- Pleos API 레퍼런스의 메서드별 반환 범위·단위·권한: 페이지는 있지만 2026-11-14 기준 본문이 비어 있음(브라우저 렌더링으로도 확인) [17]
- 실제 Pleos 차량의 Android 버전(`CAR_MILEAGE_3P` 사용 가능 여부가 여기에 달림)
- EV READY가 `IGNITION_STATE`의 어느 값으로 매핑되는지
- `universal-access-0.6.0.aar`(그룹 `ai.pleos.playground.security`): 관련 API 없음 (B). 토큰 발급·보안 저장소용 SDK(`UniversalAccess.create/initialize/getToken`, `SecureStorageService`)이고, 매니페스트 권한은 `INTERNET`뿐이다. 시동·Odometer·Fuel·SOC 관련 클래스나 `pleos.car`·`android.car` 권한 문자열은 없다.

## 근거 링크

| # | 내용 | 구분 | URL |
|---|---|---|---|
| 1 | VehiclePropertyIds.IGNITION_STATE: Normal `PERMISSION_POWERTRAIN` | 공식 | https://developer.android.com/reference/android/car/VehiclePropertyIds#IGNITION_STATE |
| 2 | VehicleIgnitionState: 값 0~5, CarPropertyManager로 조회 | 공식 | https://developer.android.com/reference/android/car/VehicleIgnitionState |
| 3 | PERF_ODOMETER: km, `PERMISSION_MILEAGE_3P`(Dangerous) 또는 `PERMISSION_MILEAGE`(Signature/Privileged) | 공식 | https://developer.android.com/reference/android/car/VehiclePropertyIds#PERF_ODOMETER |
| 4 | FUEL_LEVEL(mL, Dangerous ENERGY) / INFO_FUEL_CAPACITY(mL, Normal CAR_INFO) / EV_BATTERY_LEVEL(Wh, Dangerous ENERGY) / INFO_EV_BATTERY_CAPACITY(Wh, Normal CAR_INFO) / EV_CURRENT_BATTERY_CAPACITY(Wh, Dangerous ENERGY) | 공식 | https://developer.android.com/reference/android/car/VehiclePropertyIds#FUEL_LEVEL · #INFO_FUEL_CAPACITY · #EV_BATTERY_LEVEL · #INFO_EV_BATTERY_CAPACITY · #EV_CURRENT_BATTERY_CAPACITY |
| 5 | Car 권한 상수: MILEAGE(Signature/Privileged), MILEAGE_3P·ENERGY(Dangerous), CAR_INFO·POWERTRAIN(Normal) | 공식 | https://developer.android.com/reference/android/car/Car#PERMISSION_MILEAGE |
| 6 | Car 서비스 매니페스트 android14-release: CAR_MILEAGE=signature\|privileged, CAR_ENERGY=dangerous, CAR_INFO·CAR_POWERTRAIN·CAR_EXTERIOR_ENVIRONMENT=normal, CAR_MILEAGE_3P 미선언 | 공식(AOSP 소스) | https://android.googlesource.com/platform/packages/services/Car/+/refs/heads/android14-release/service/AndroidManifest.xml |
| 7 | 같은 매니페스트 main: CAR_MILEAGE_3P=dangerous(featureFlag android_b_vehicle_properties) | 공식(AOSP 소스) | https://android.googlesource.com/platform/packages/services/Car/+/refs/heads/main/service/AndroidManifest.xml |
| 8 | VehiclePropertyIds.java android14-release: PERF_ODOMETER는 `@RequiresPermission(Car.PERMISSION_MILEAGE)` 단독 | 공식(AOSP 소스) | https://android.googlesource.com/platform/packages/services/Car/+/refs/heads/android14-release/car-lib/src/android/car/VehiclePropertyIds.java |
| 9 | Pleos Vehicle SDK 소개: 서드파티 앱 → Vehicle Service 구조, 「Vehicle SDK v2.3.2 / Vehicle Service v2.3.21 기준」 | 공식 | https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/intro |
| 10 | Pleos 권한 선언: 도메인별 표, 「API마다 @RequiresPermission 확인」 | 공식 | https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission |
| 11 | Pleos SDK API 정책: Standard First(표준 데이터는 AAOS Car API) | 공식 | https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/pleos-connect-sdk-api |
| 12 | API 호환성·가용성: VehiclePropertyIds·CarPropertyManager로 지원 확인, 특수 프로퍼티는 기술지원팀 검토 | 공식 | https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability |
| 13 | SDK 릴리스노트: Vehicle v2.3.1 변경, EvBatteryLevelListener 「Hidden from docs」 | 공식 | https://document.pleos.ai/en/docs/pleos-only/release-note/sdk-release-note |
| 14 | Virtual Console 사용: 도메인 표(Vehicle·Motion·ADAS·Convenience·Safety·Energy·Climate) | 공식 | https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator |
| 15 | 에뮬레이터 설정: cold boot 필수(quick boot면 VHAL 불가), Emulator 2.1.0 / API 34 | 공식 | https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator · https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator |
| 16 | 공모전 공지: 문서 미지원 기능 사용 불가, 2차 PT는 에뮬 시연(실차 불필요), 기술지원 창구 없음 | 공식(공모전) | https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000008 |
| 17 | Pleos API 레퍼런스 getter 페이지(본문 비어 있음 확인용) | 공식 | https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/get-current-distance-driven |
