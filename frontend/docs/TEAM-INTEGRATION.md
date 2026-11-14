# 팀 저장소 배치 안내

## 옮길 위치

`frontend` 폴더를 `Mybread2/pleos-care-log`의 루트에 추가합니다. 기존 `app/`, `mock-fixtures/`, `docs/`, Gradle 파일은 그대로 둡니다. 현재 폴더에는 별도 Git 메타데이터가 없으므로 서브모듈로 추가할 필요가 없습니다.

## 실행과 검증

팀 저장소 루트에서:

```powershell
cd frontend
npm test
npm run build
npm run dev
```

실행 주소는 `http://localhost:3000`입니다. Node.js 22에서 검증했으며 외부 패키지 설치는 필요하지 않습니다. 설치 없이 시연하려면 `frontend/index.standalone.html`을 브라우저로 엽니다.

## Android 앱과 웹 Mock의 관계

| 영역 | 기존 Android 앱 | frontend 웹 Mock |
| --- | --- | --- |
| 화면 | Kotlin/Compose | HTML/CSS/JavaScript |
| 차량 입력 | VehicleGateway 및 SDK | MockVehicleDataSource |
| 계산 | RefillDetector·TripSummary | RecordEngine 및 B04 Mock 시험 가정 |
| 저장 | Room의 snapshot/refill_record 등 | 메모리 RecordStore·별도 UserCorrection |
| 실행 | Android/Pleos 환경 | 일반 브라우저 |

현재 정리는 두 앱을 같은 저장소에 배치하는 작업입니다. 실제 차량 데이터 공유나 화면 이식은 별도 개발 범위입니다. Kotlin의 `costWon`/확인 상태와 웹 계약의 `amountKrw`/출처/정정 이력 등을 매핑해야 하며, 연결할 HTTP API가 구현돼 있다고 가정하지 않습니다.

## 팀 데이터와 생성물

- `tests/fixtures/team-b04/`는 기존 팀 저장소에서 받은 고정 버전 사본입니다. 출처 커밋과 해시는 `source.json`에 있습니다. 루트 Mock 데이터가 변경돼도 이 사본은 자동으로 바뀌지 않습니다.
- `npm run build`는 이 사본에서 기본 시연용 `src/mock/teamFixture.js`와 단독 HTML을 생성합니다. 기대값은 런타임 입력에 넣지 않습니다.
- `.gitattributes`는 고정 Fixture의 바이트를 보존합니다. 상위 저장소의 줄바꿈 설정과 무관하게 해시를 유지하기 위한 규칙입니다.
- `dist/`, `artifacts/`, 의존성·캐시·비밀 설정은 폴더 내부 `.gitignore`로 제외합니다.
- 촬영 순서는 `../F04-DEMO-GUIDE.md`, 1차 과제 범위와 검사 근거는 `F03-F04-REVIEW.md`를 참고합니다.

저장소 생성, 원격 주소 변경, 커밋, 업로드는 이 패키지 준비 과정에서 수행하지 않았습니다.
