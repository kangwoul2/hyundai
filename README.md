# Hyundai Pleos App Challenge — 기획 저장소

> **상태: DISCOVERY / 아이디어 미확정 / 구현 미착수 (2026-11-05)**

이 저장소는 2026 현대자동차그룹 차량용 앱 개발 공모전을 준비하며, Claude의 산업·SDK 조사 결과를 보존하고 공모전 규칙과 후보 아이디어를 검토하는 작업실입니다. 아직 특정 아이디어를 선택하거나 앱을 개발했다고 주장하지 않습니다.

## 처음 읽는 순서

1. [Claude 조사 결과를 쉽게 설명한 글](docs/research/claude-analysis-explained.md)
2. [공모전 공식 규칙과 제출 안내](docs/competition/rules.md)
3. [추천 개발 환경과 구현 접근](docs/development/environment.md)
4. [차량 SDK 데이터 접근성 검증표](docs/development/data-capability-matrix.md)
5. [아이디어 후보 모음](docs/ideas/idea-pool.md)
6. [아직 미정인 결정과 다음 행동](docs/process/decision-log.md)
7. [Claude 원본 조사 텍스트](docs/research/claude-original-2026-11-05.txt)

**Astra·Claude·Codex 등 AI 협업 도구는 루트의 [AGENTS.md](AGENTS.md)를 먼저 읽어 주세요.**

## 지켜야 할 원칙

- 아이디어를 임의로 확정하지 않는다. 사용자와 비교·토론 후 선택한다.
- 공식 발표로 확인한 사실, 외부 조사 보고서의 주장, 우리의 추정을 구분한다.
- 차량 신호는 SDK 명칭만 보고 '사용 가능'이라고 단정하지 않는다. 해당 버전·권한·차종·에뮬레이터에서 확인한다.
- SDK를 활용한 자동차 고유 가치, 직접 데이터 수집, 외부 데이터와의 검증 가능한 결합을 추구한다.
- AI 모델 호출만 붙인 아이디어를 우선하지 않는다.
- 차량 제어, 안전 진단, 위험 예측은 근거 없이 구현을 약속하지 않는다.

## 공식 사이트

- 공모전: https://appdevchallenge.auto-hmg.io/
- 접수 및 양식: https://appdevchallenge.auto-hmg.io/guide/submission
- 심사: https://appdevchallenge.auto-hmg.io/guide/judging-awards
- 개발환경: https://appdevchallenge.auto-hmg.io/guide/development
- Pleos Playground: https://developers.pleos.ai/playground/discover/developer-journey

## 가장 가까운 마감

- **2026-11-15 (KST):** 1차 기획서 이메일 접수 마감. 정확한 마감 시각은 공고에 별도 표시되지 않으므로 임의로 가정하지 않는다.
- **2026-11-23:** 1차 서류 발표.
- **2026-12-03~04:** 2차 PT. 합격 시 작동 가능한 앱 필요.

이 저장소는 비공개(private)입니다. 1차에 GitHub 개발 산출물을 제출할 경우에는 **소스 코드와 실제 release APK**가 필요하며, 문서만 등록한 현재 저장소를 구현 증빙으로 소개해서는 안 됩니다.
