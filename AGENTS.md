# AI 작업 지침 — Astra / Claude / Codex / GPT

## 현재 프로젝트 상태 (2026-11-05)

- 저장소: `kangwoul2/hyundai` (private)
- 단계: 조사 정리, 공식 규칙 확인, 후보 아이디어 토론.
- **아이디어 확정: NO / 코드 개발 착수: NO / SDK 구동 검증: NO / 1차 제출: NO**
- 사용자의 우선 요청: Claude 원문을 이해하기 쉬운 문장으로 정리하고, SDK의 실제 가능성을 알아본 다음 함께 아이디어를 선택할 것.

## 필독 순서
1. `README.md`
2. `docs/research/claude-analysis-explained.md`
3. `docs/competition/rules.md`
4. `docs/development/environment.md`
5. `docs/development/data-capability-matrix.md`
6. `docs/ideas/idea-pool.md`
7. `docs/process/decision-log.md`
8. 필요한 경우 `docs/research/claude-original-2026-11-05.txt`

## 자료 신뢰도

- `OFFICIAL`: 공모전 또는 Pleos 공식 페이지에서 직접 확인한 내용. URL과 확인 일자를 함께 기록한다.
- `REPORTED`: 사용자가 전달한 Claude 조사 원문의 주장. 원문에서 VERIFIED라 했어도 별도 교차검증 전에는 독립 확인으로 간주하지 않는다.
- `HYPOTHESIS`: 우리 팀의 사업·심사 전략 추정.
- `UNKNOWN`: SDK 반환 타입, 권한, 차량 호환성, 데이터 활용 동의 등 아직 확인 못한 것.
- `TESTED`: 실제 환경에서 재현 가능한 테스트 로그와 버전 기록이 있는 경우에만 사용.

## 진행 규칙

1. 사용자 승인 없이 후보 중 하나를 확정하거나 이름·아키텍처를 고정하지 말 것.
2. 현 단계에서는 기획·환경·SDK 검증 문서를 갱신한다. 앱 구현은 최종 콘셉트 선정 후 수행한다.
3. 방법론: 문제 → 대상 사용자 → 자동차만의 정보 → 필요한 SDK → 데이터 수집 방법 → MVP → 검증 지표 → 리스크.
4. 외부 데이터는 라이선스·갱신주기·정확도·공공 API 이용 조건을 확인한다.
5. 개인정보·위치·차량 식별 데이터는 수집 최소화, 목적 명시, 동의, 로컬 우선 보관 원칙.
6. 안전·제어 권한은 기본적으로 사용하지 않으며 별도 승인 전까지 시연 구현도 약속하지 말 것.
7. 플랫폼 공식 요구사항과 가이드가 바뀌면 공식 문서를 우선으로 갱신하고 변경 내역을 남길 것.

## 지금 Astra에게 기대하는 산출물

- 원문에서 주장/검증/추론을 분리한 비판적 요약.
- 아이디어 5~8개의 경쟁 서비스 중복 및 차량 데이터 의존성 비교.
- 상위 후보의 SDK 검증 실험 설계(코드는 아이디어 확정 전 변경 최소화).
- 최종 선택을 위한 판단표와 열린 질문 3개 이내.
- 결과는 `docs/ideas/`, `docs/development/`, `docs/process/`에 기록.

## 절대 혼동 금지

공모전 서류는 기획서만 있어도 지원 가능하지만, 1차 가산점용 GitHub는 소스+APK를 요구한다. SDK의 상태 도메인 존재만으로 모든 차종·에뮬레이터에서 해당 데이터가 읽힌다고 증명되지는 않는다.
