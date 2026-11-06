# 제출 체크리스트

공식 규칙은 [competition/rules.md](../competition/rules.md)(2026-11-05 확인). **마감 2026-11-15 KST(정확한 시각 미공지 — 전날까지 제출 권장).** 최종 이메일 제출·SNS 업로드·심사 계정 초대는 사용자 승인이 필요해 **수행하지 않았다.**

| # | 항목 | 상태 | 비고 |
| --- | --- | --- | --- |
| 1 | 공식 접수양식(기획서) 작성 | ⬜ 사용자 | 작성 재료: [planning-material.md](planning-material.md). 양식은 공식 사이트에서 내려받아 채운다 |
| 2 | 개인정보 수집·이용 동의서(참가자별 서명) | ⬜ 사용자 | 팀 참가 시 전원 개별 서명, 대표자가 전원 CC |
| 3 | 참가 부문(대학생/일반) 확인 | ⬜ 사용자 | SSAFY 교육생이라는 사실만으로 확정되지 않음 |
| 4 | 이메일 제출 | ⬜ 사용자 | `appdevchallenge@hyundai-partners.com`, 제목 `[차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명` |
| 5 | 소스 코드 저장소 | ✅ | `main` 브랜치 (2026-11-06 병합, 작업 브랜치는 삭제) |
| 6 | release APK `apk/팀명_앱이름_release.apk` | ✅ | `apk/한영서_BabyRelay_release.apk` (개인 참가, 성명 사용). 이름만 바꿨고 해시는 그대로 |
| 7 | README(앱 이름·소개·실행 방법·테스트 환경·APK 위치) | ✅ | |
| 8 | 심사용 GitHub 계정을 collaborator로 초대, 실제 접근 확인 | ⬜ 사용자 | Private 저장소는 공지된 심사용 계정 초대 필요. **쓰기 권한이 부여될 수 있음 유의** |
| 9 | 프로토타입 시연 영상(선택, 가산점) | ◐ 초안 | `docs/demo/video/babyrelay-demo-A-draft.mp4`. 심사용으로 다시 다듬고 업로드 경로를 정해야 함 |
| 10 | 숏폼 영상(별도 특별상, `#HMG차량용앱개발공모전`) | ⬜ | 미제작. 업로드는 사용자 승인 후 |
| 11 | 앱 서명 키 별도 백업 | ⬜ 사용자 | `C:\SSAFY\keys\babyrelay-release.jks`, `keystore.properties` |
| 12 | 시설 데이터를 실제 공공데이터로 교체 | ⬜ 권장 | 현재 SAMPLE. 절차: [data-sources.md](../development/data-sources.md) |
| 13 | Pleos 도착 콜백 실검증 | ⬜ 권장 | [sdk-integration.md](../development/sdk-integration.md) 남은 위험 |

## 제출 전 일관성 점검

- 기획서의 "차량 연동" 문구가 검증 상태와 모순되지 않는가: **경유지 포함 경로 요청·취소·조회는 실제 에뮬레이터 TESTED, 도착 콜백은 REPLAY 검증만.**
- 영상의 모든 장면에 출처 라벨(Pleos/REPLAY/SAMPLE)이 있는가.
- 기획서에 사용자 수요·시장 규모 수치를 쓰지 않았는가(검증된 수치 없음).
- 2차 PT(2026-12-03~04) 진출 시 작동 앱이 필요하다. 1차 산출물은 2차 전에 개선 가능.
