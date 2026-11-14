# 문서 색인

## 문서 목록

| 분류 | 문서 | 내용 |
|---|---|---|
| research | [research/sdk-signal-support.md](research/sdk-signal-support.md) | Pleos SDK 필수 신호(시동/READY·Odometer·Fuel/SOC·권한·에뮬레이터) 지원범위 조사 (2026-11-14) |

Mock 데이터와 검증 스크립트는 문서가 아니라서 [`../mock-fixtures/`](../mock-fixtures/)에 둔다.

## 분류 규칙

새 문서는 아래 폴더 중 하나에 넣고, 위 목록에 한 줄 추가한다. 맞는 분류가 없을 때만 폴더를 새로 만든다.

| 폴더 | 넣는 문서 |
|---|---|
| `research/` | SDK·기술 조사, 실동작 검증 기록 |
| `contracts/` | 팀 데이터 계약(B01 등)을 레포로 옮길 때 |
| `planning/` | PRD, 시연 시나리오, 일정 |

- 파일 이름은 영문 kebab-case(`sdk-signal-support.md`)로 짓는다. 한글 파일 이름은 GitHub 링크가 깨진다.
- 문서 첫머리에 작성일과 근거 수준(문서 조사 / 실동작 검증 / 미검증)을 적는다.
