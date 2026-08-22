#!/usr/bin/env python3
"""상가정보 CSV → place 적재용 TSV 변환 (TMT-161).

정본은 소상공인 상가(상권)정보 단독이다 — 근거는 TMT-160 결정(2026-08-22)과
[분석] 매장 원본 데이터 실측 문서 §6. 인허가 병합은 UT2 이후로 유예됐다.

입력:  상가정보 서울 CSV (UTF-8, 분기 갱신). 컬럼은 이름으로 찾는다 —
       분기 갱신에서 컬럼 순서가 바뀌어도 동작하고, 이름이 바뀌면 시끄럽게 죽는다.
출력:  탭 구분 TSV (stdout). 컬럼 순서는 sql/upsert.sql의 staging 테이블과 일치해야 한다.

표준 에러로 처리 통계를 남긴다. 걸러진 행이 왜 걸러졌는지 세지 않으면
"141,126건이어야 하는데 13만 건" 같은 이상을 조용히 지나치게 된다.
"""

import csv
import sys
import argparse

# 실측 문서 §3의 매핑표. DDL 길이 제약(docs/DB-SCHEMA.sql)을 넘으면 자르지 않고 버린다
# — 길이 초과는 데이터 이상 신호라서, 잘라 넣으면 이상이 숨는다.
REQUIRED_COLUMNS = [
    "상가업소번호", "상호명", "지점명",
    "상권업종대분류명", "상권업종소분류명",
    "시도명", "시군구명", "법정동명",
    "지번주소", "도로명주소", "경도", "위도",
]

MAX_LEN = {"name": 100, "road_address": 200, "jibun_address": 200, "region_name": 50}

# 서울 bbox — 실측 문서 §2의 함정(엉뚱한 좌표계가 bbox는 통과) 때문에 넉넉하지 않게 잡는다.
SEOUL_LON = (126.734, 127.270)
SEOUL_LAT = (37.413, 37.716)


def build_name(sangho: str, jijeom: str) -> str:
    """상호명 + 지점명. 지점명이 상호명에 이미 들어 있으면 붙이지 않는다."""
    sangho, jijeom = sangho.strip(), jijeom.strip()
    if jijeom and jijeom not in sangho:
        return f"{sangho} {jijeom}"
    return sangho


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("csv_path")
    ap.add_argument("--sigungu", help="시군구명 필터 (예: 마포구) — 시험 적재용")
    args = ap.parse_args()

    stats = {
        "read": 0, "kept": 0, "not_food": 0, "not_seoul": 0, "sigungu_filtered": 0,
        "missing_required": 0, "bad_coord": 0, "coord_out_of_bbox": 0,
        "too_long": 0, "dup_external_id": 0,
    }
    seen_ids: set[str] = set()
    out = csv.writer(sys.stdout, delimiter="\t", lineterminator="\n",
                     quoting=csv.QUOTE_NONE, escapechar="\\")

    with open(args.csv_path, newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        missing = [c for c in REQUIRED_COLUMNS if c not in (reader.fieldnames or [])]
        if missing:
            print(f"컬럼 누락 — 분기 갱신에서 스키마가 바뀐 것 같다: {missing}", file=sys.stderr)
            print(f"실제 헤더: {reader.fieldnames}", file=sys.stderr)
            return 1

        for row in reader:
            stats["read"] += 1
            if row["상권업종대분류명"].strip() != "음식":
                stats["not_food"] += 1
                continue
            if row["시도명"].strip() != "서울특별시":
                stats["not_seoul"] += 1
                continue
            if args.sigungu and row["시군구명"].strip() != args.sigungu:
                stats["sigungu_filtered"] += 1
                continue

            ext_id = row["상가업소번호"].strip()
            name = build_name(row["상호명"], row["지점명"])
            road = row["도로명주소"].strip()
            jibun = row["지번주소"].strip()
            region = f"{row['시군구명'].strip()} {row['법정동명'].strip()}".strip()

            if not ext_id or not name or not road or not region:
                stats["missing_required"] += 1
                continue
            try:
                lon, lat = float(row["경도"]), float(row["위도"])
            except ValueError:
                stats["bad_coord"] += 1
                continue
            if not (SEOUL_LON[0] <= lon <= SEOUL_LON[1] and SEOUL_LAT[0] <= lat <= SEOUL_LAT[1]):
                stats["coord_out_of_bbox"] += 1
                continue
            if (len(name) > MAX_LEN["name"] or len(road) > MAX_LEN["road_address"]
                    or len(jibun) > MAX_LEN["jibun_address"] or len(region) > MAX_LEN["region_name"]):
                stats["too_long"] += 1
                continue
            if ext_id in seen_ids:
                stats["dup_external_id"] += 1
                continue
            seen_ids.add(ext_id)

            # 컬럼 순서 = sql/upsert.sql staging 정의. \N은 SQL NULL.
            out.writerow(["SEMAS", ext_id, name, road, jibun or "\\N", region,
                          f"{lon:.7f}", f"{lat:.7f}"])
            stats["kept"] += 1

    for k, v in stats.items():
        print(f"{k}\t{v}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
