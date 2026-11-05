#!/usr/bin/env python3
"""공개 수유시설 CSV → facilities_snapshot.json 변환기 + 데이터 품질 보고.

이 스크립트는 네트워크를 호출하지 않는다(허가 없는 대량 수집 금지). 사람이 공공데이터포털/지자체 포털에서
이용 조건을 확인하고 내려받은 CSV 한 파일을 입력으로 받는다.

사용 예:
  python tools/collect_facilities.py --csv data/raw/nursing.csv --source "공공데이터포털 <데이터셋명>" \
      --verified-on 2026-11-05 --map name=시설명 address=소재지도로명주소 lat=위도 lon=경도 kind=시설구분 \
      --demo-center 37.4692,127.0393 --radius-km 60 > snapshot.json

  python tools/collect_facilities.py --report app/src/main/assets/facilities_snapshot.json

품질 지표 정의는 앱의 DataQuality.of(...)와 같다.
"""
import argparse
import csv
import json
import math
import sys

sys.stdout.reconfigure(encoding="utf-8")  # Windows 기본 인코딩(cp949) 방지
sys.stderr.reconfigure(encoding="utf-8")
from collections import Counter


def haversine(lat1, lon1, lat2, lon2):
    r = 6371.0088
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def quality(facilities):
    total = len(facilities)
    ids = [f["id"] for f in facilities]
    valid = sum(
        1 for f in facilities
        if isinstance(f.get("lat"), (int, float)) and isinstance(f.get("lon"), (int, float))
        and -90 <= f["lat"] <= 90 and -180 <= f["lon"] <= 180 and not (f["lat"] == 0 and f["lon"] == 0)
    )
    dates = sorted(f["verifiedOn"] for f in facilities if f.get("verifiedOn"))
    return {
        "total": total,
        "validCoordinates": valid,
        "validCoordinateRatio": round(valid / total, 3) if total else 0,
        "duplicateIds": total - len(set(ids)),
        "missingKind": sum(1 for f in facilities if not f.get("kind")),
        "missingAmenities": sum(1 for f in facilities if not f.get("amenities")),
        "byOrigin": dict(Counter(f.get("origin", "?") for f in facilities)),
        "oldestVerified": dates[0] if dates else None,
        "newestVerified": dates[-1] if dates else None,
    }


def convert(args):
    mapping = dict(kv.split("=", 1) for kv in args.map)
    required = {"name", "lat", "lon"}
    if not required <= mapping.keys():
        sys.exit(f"--map 에 {sorted(required)} 가 필요합니다")
    clat, clon = (float(x) for x in args.demo_center.split(","))
    facilities, seen = [], set()
    with open(args.csv, encoding=args.encoding, newline="") as fh:
        for i, row in enumerate(csv.DictReader(fh), start=1):
            try:
                lat, lon = float(row[mapping["lat"]]), float(row[mapping["lon"]])
            except (ValueError, KeyError):
                continue  # 좌표 없는 행은 추천에 쓸 수 없으므로 제외(품질 보고에서 원본 대비 비율로 확인)
            if haversine(clat, clon, lat, lon) > args.radius_km:
                continue
            key = (row[mapping["name"]].strip(), round(lat, 5), round(lon, 5))
            if key in seen:
                continue
            seen.add(key)
            facilities.append({
                "id": f"pub-{len(facilities) + 1:04d}",
                "name": row[mapping["name"]].strip(),
                "address": row.get(mapping.get("address", ""), "").strip(),
                "lat": lat,
                "lon": lon,
                **({"kind": row[mapping["kind"]].strip()} if mapping.get("kind") and row.get(mapping["kind"], "").strip() else {}),
                # 수유시설 데이터셋은 수유실 존재 자체를 의미한다. 기저귀대 등은 원본에 있을 때만 별도 매핑한다.
                "amenities": ["NURSING_ROOM"],
                "origin": "PUBLIC_DATA",
                "verifiedOn": args.verified_on,
            })
    json.dump({
        "meta": {"dataset": args.source, "kind": "PUBLIC_DATA", "generatedOn": args.verified_on, "note": "원본 CSV에서 변환"},
        "demoOrigin": {"lat": clat, "lon": clon, "label": "시연 중심"},
        "destinations": [],
        "facilities": facilities,
    }, sys.stdout, ensure_ascii=False, indent=2)
    print(json.dumps(quality(facilities), ensure_ascii=False), file=sys.stderr)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--report", help="기존 스냅샷의 품질 지표만 출력")
    ap.add_argument("--csv")
    ap.add_argument("--encoding", default="utf-8-sig")
    ap.add_argument("--source", default="")
    ap.add_argument("--verified-on", default="")
    ap.add_argument("--map", nargs="*", default=[])
    ap.add_argument("--demo-center", default="37.4692,127.0393")
    ap.add_argument("--radius-km", type=float, default=60.0)
    args = ap.parse_args()
    if args.report:
        with open(args.report, encoding="utf-8") as fh:
            print(json.dumps(quality(json.load(fh)["facilities"]), ensure_ascii=False, indent=2))
    elif args.csv:
        convert(args)
    else:
        ap.error("--report 또는 --csv 중 하나가 필요합니다")


if __name__ == "__main__":
    main()
