# 제출 체크리스트

공식 규칙은 [competition/rules.md](../competition/rules.md)(2026-11-05 확인). **마감 2026-11-15 KST(정확한 시각 미공지 — 전날까지 제출 권장).** 최종 이메일 제출·SNS 업로드·심사 계정 초대는 사용자 승인이 필요해 **수행하지 않았다.**

| # | 항목 | 상태 | 비고 |
| --- | --- | --- | --- |
| 1 | 공식 접수양식(기획서) 작성 | ◐ 본문 완성 | 양식에 넣을 그림 5장: [figures/](figures/). 본문: [proposal.md](proposal.md) (내부 평가: [self-review.md](self-review.md)). 공식 DOCX 양식 칸에 옮기고 분량 제한 확인은 사용자 |
| 2 | 개인정보 수집·이용 동의서(참가자별 서명) | ⬜ 사용자 | 팀 참가 시 전원 개별 서명, 대표자가 전원 CC |
| 3 | 참가 부문(대학생/일반) 확인 | ⬜ 사용자 | SSAFY 교육생이라는 사실만으로 확정되지 않음 |
| 4 | 이메일 제출 | ⬜ 사용자 | `appdevchallenge@hyundai-partners.com`, 제목 `[차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명` |
| 5 | 소스 코드 저장소 | ✅ | `main` 브랜치 (2026-11-06 병합). 원격 `feature/babyrelay-mvp` 삭제는 사용자가 GitHub 웹에서 수행 |
| 6 | release APK `apk/팀명_앱이름_release.apk` | ✅ | `apk/한영서_BabyRelay_release.apk` (개인 참가, 성명 사용). 이름만 바꿨고 해시는 그대로 |
| 7 | README(앱 이름·소개·실행 방법·테스트 환경·APK 위치) | ✅ | |
| 8 | 심사용 GitHub 계정을 collaborator로 초대, 실제 접근 확인 | ⬜ 사용자 | Private 저장소는 공지된 심사용 계정 초대 필요. **쓰기 권한이 부여될 수 있음 유의** |
| 9 | 프로토타입 시연 영상(선택, 가산점) | ✅ 최종본 | `docs/demo/video/babyrelay-demo-final.mp4` (77초, 1280x720). 유튜브 일부 공개 등 업로드와 링크 기재는 사용자 |
| 10 | 숏폼 영상(별도 특별상, `#HMG차량용앱개발공모전`) | ⬜ | 미제작. 업로드는 사용자 승인 후 |
| 11 | 앱 서명 키 별도 백업 | ⬜ 사용자 | `C:\SSAFY\keys\babyrelay-release.jks`, `keystore.properties` |
| 12 | 시설 데이터를 실제 공공데이터로 교체 | ⬜ 권장 | 현재 SAMPLE. 후보: [수유정보 알리미 Open API](https://sooyusil.com/home/39.htm)(전국 3,019건, 위경도, 수유실 종류, 아빠 이용 여부, 운영시간 없음, 신청 후 심의와 인증키 발급, 공공 목적 조건). 절차: [data-sources.md](../development/data-sources.md) |
| 13 | Pleos 도착 콜백 실검증 | ⬜ 권장 | [sdk-integration.md](../development/sdk-integration.md) 남은 위험 |
| 14 | 영아 보호자 5명 인터뷰 | ⬜ 권장 | 사업성 점수를 올릴 유일한 직접 근거. 질문과 판단 기준은 proposal.md 6.5절. 결과는 문장 그대로 1절과 6.5절에 추가 |

## 제출 이메일 문안

- 받는 사람: `appdevchallenge@hyundai-partners.com`
- 제목: `[차량용 앱 개발 공모전] 한영서_한영서`
- 첨부: 접수양식(기획서) 작성본, 개인정보 수집 및 이용 동의서(서명본)

```text
안녕하세요. 2026 현대자동차그룹 차량용 앱 개발 공모전에 개인으로 참가하는 한영서입니다.

접수양식(기획서)과 개인정보 수집 및 이용 동의서를 첨부합니다.

- 앱 이름: BabyRelay (베이비릴레이)
- GitHub 저장소: https://github.com/kangwoul2/hyundai (비공개, 심사용 계정 초대 완료)
- release APK: 저장소 apk/한영서_BabyRelay_release.apk
- 시연 영상: (업로드 링크)

감사합니다.
한영서 드림
```

## 제출 전 일관성 점검

- 기획서의 "차량 연동" 문구가 검증 상태와 모순되지 않는가: **경유지 포함 경로 요청·취소·조회는 실제 에뮬레이터 TESTED, 도착 콜백은 REPLAY 검증만.**
- 영상의 모든 장면에 출처 라벨(Pleos/REPLAY/SAMPLE)이 있는가.
- 기획서의 모든 수치에 출처가 있는가(출생아 수, 수유시설 수, 플레오스 탑재 계획, 인구보건복지협회 조사). 출처 없는 시장 규모나 전환율은 쓰지 않는다.
- 2차 PT(2026-12-03~04) 진출 시 작동 앱이 필요하다. 1차 산출물은 2차 전에 개선 가능.
