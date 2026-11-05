# Claude / Astra 작업 시작 안내

이 저장소의 **최상위 협업 규칙은 [AGENTS.md](AGENTS.md)**에 있다. 작업을 시작하기 전에 반드시 읽고 준수한다.

- **현재 상태 (2026-11-05): 아이디어 미확정, 앱 코드 미작성, SDK 실측 미검증.**
- 사용자 요구: Claude 조사 내용을 이해하기 쉬운 한국어로 정리하고, 공모전 제출 규칙 및 Pleos 개발 환경을 파악한 뒤, 함께 아이디어를 선택한다.
- 작업 순서: `README.md` → `AGENTS.md` → `docs/research/claude-analysis-explained.md` → `docs/competition/rules.md` → `docs/development/data-capability-matrix.md` → `docs/ideas/idea-pool.md`.
- Claude 원본은 `docs/research/claude-original-2026-11-05.txt`. 외부 보고서에서 VERIFIED라고 표기해도 공식 근거를 별도로 확인하지 못했으면 REPORTED로 본다.
- 사용자 승인 없이 하나의 후보 아이디어를 확정하거나 임의로 구현하지 않는다.
- 최우선 기준은 실제 차량 데이터 접근 가능성, 기존 현대차 서비스와의 차별화, 측정 가능한 사용자 문제, 현실적인 공모전 제출 일정이다.

## 첫 응답 방식

1. 프로젝트의 현재 상태를 4~6문장으로 요약한다.
2. 아이디어 후보를 적어도 3개 비교하고 그중 SDK 접근 리스크를 식별한다.
3. 사용자가 선택할 수 있도록 열린 질문을 던진다. **최종 선택은 사용자 몫이다.**
