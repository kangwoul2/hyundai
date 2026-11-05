#!/usr/bin/env python3
"""시연용 SAMPLE 시설 스냅샷 생성기.

생성되는 시설은 실제 운영 시설이 아니다(이름·속성 모두 합성). 좌표만 서울 양재 부근 →
대전역 / 천안아산역 직선 위에서 계산한 값이며, 모든 항목은 origin=SAMPLE 로 표시된다.
실제 공공데이터를 확보하면 tools/collect_facilities.py 로 같은 형식의 스냅샷을 만든다.

사용: python tools/make_sample_snapshot.py > app/src/main/assets/facilities_snapshot.json
"""
import json
import math
import sys

sys.stdout.reconfigure(encoding="utf-8")  # Windows 기본 인코딩(cp949) 방지
sys.stderr.reconfigure(encoding="utf-8")

ORIGIN = (37.4692, 127.0393)  # 서울 양재 부근 (시연용 출발 위치)
DESTS = {
    "dest-daejeon": ("대전역", "대전광역시 동구 중앙로 (시연용 근사 좌표)", 36.3323, 127.4342),
    "dest-cheonan": ("천안아산역", "충청남도 아산시 (시연용 근사 좌표)", 36.7944, 127.1042),
    "dest-gangneung": ("강릉역", "강원특별자치도 강릉시 (시연용 근사 좌표)", 37.7640, 128.8993),
}

N, D, H, P = "NURSING_ROOM", "DIAPER_TABLE", "HOT_WATER", "PARKING"

# (목적지, 직선상 위치 비율 t, 직선에서 떨어진 거리 km(+왼쪽/-오른쪽), 종류, 시설, 확인일)
SPEC = [
    ("dest-daejeon", 0.12, 1.2, "휴게소", [N, D, H, P], "2026-10-20"),
    ("dest-daejeon", 0.20, 4.5, "쇼핑몰", [N, D, P], "2026-09-30"),
    ("dest-daejeon", 0.28, 0.8, "휴게소", [N, D, H, P], "2026-10-25"),
    ("dest-daejeon", 0.33, 6.0, "공공시설", [N], "2026-08-15"),
    ("dest-daejeon", 0.42, 2.0, "휴게소", [N, D, P], "2025-06-01"),  # 확인일이 오래된 항목
    ("dest-daejeon", 0.48, 9.5, "문화시설", [N, D], "2026-10-01"),
    ("dest-daejeon", 0.55, 1.5, "휴게소", [N, D, H, P], "2026-10-28"),
    ("dest-daejeon", 0.62, 3.0, None, [N, D], "2026-07-10"),  # 종류 정보 누락
    ("dest-daejeon", 0.70, 12.0, "쇼핑몰", [N, D, H, P], "2026-10-05"),
    ("dest-daejeon", 0.78, 1.0, "휴게소", [D, P], "2026-10-12"),  # 수유실 없음
    ("dest-daejeon", 0.88, 2.2, "공공시설", [N, D, P], "2026-10-18"),
    ("dest-daejeon", 0.93, 0.5, "휴게소", [], "2026-10-30"),  # 시설 정보 미상
    ("dest-cheonan", 0.35, 1.8, "휴게소", [N, D, P], "2026-10-22"),
    ("dest-cheonan", 0.65, 2.6, "쇼핑몰", [N, D, H], "2026-10-09"),
]


def point(dest_key, t, off_km):
    _, _, dlat, dlon = DESTS[dest_key]
    olat, olon = ORIGIN
    km_lat = math.pi * 6371.0088 / 180.0
    km_lon = km_lat * math.cos(math.radians(olat))
    bx, by = (dlon - olon) * km_lon, (dlat - olat) * km_lat
    total = math.hypot(bx, by)
    ux, uy = bx / total, by / total
    px, py = bx * t + (-uy) * off_km, by * t + ux * off_km
    return round(olat + py / km_lat, 6), round(olon + px / km_lon, 6)


def main():
    facilities = []
    for i, (dk, t, off, kind, am, verified) in enumerate(SPEC, start=1):
        lat, lon = point(dk, t, off)
        facilities.append({
            "id": f"sample-{i:02d}",
            "name": f"샘플 쉼터 {i:02d}",
            "address": f"합성 데이터 (실제 시설 아님) #{i:02d}",
            "lat": lat,
            "lon": lon,
            **({"kind": kind} if kind else {}),
            "amenities": am,
            "origin": "SAMPLE",
            "verifiedOn": verified,
        })
    snapshot = {
        "meta": {
            "dataset": "BabyRelay 시연용 합성 샘플",
            "kind": "SAMPLE",
            "generatedOn": "2026-11-05",
            "note": "실제 운영 시설이 아닌 합성 데이터입니다. 실제 공공데이터는 인증키/포털 로그인이 필요해 이번 빌드에 포함하지 못했습니다.",
        },
        "demoOrigin": {"lat": ORIGIN[0], "lon": ORIGIN[1], "label": "서울 양재 부근 (시연용)"},
        "destinations": [
            {"id": k, "name": v[0], "address": v[1], "lat": v[2], "lon": v[3]} for k, v in DESTS.items()
        ],
        "facilities": facilities,
    }
    json.dump(snapshot, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
