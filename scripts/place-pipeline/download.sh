#!/usr/bin/env bash
# 소상공인 상가(상권)정보 다운로드 (TMT-161). 로그인·API 키 불필요.
#
# atchFileId는 분기마다 바뀌므로 메타 API에서 먼저 조회한다.
# zip 내부 파일명은 CP437→CP949로 디코딩해야 한글이 보인다 — unzip -O cp949.
# 출처·실측 근거: [분석] 매장 원본 데이터 실측 §7 (Confluence 57901087)
set -euo pipefail

OUT_DIR="${1:-.}"
META="https://www.data.go.kr/tcs/dss/selectFileDataDownload.do?publicDataPk=15083033&publicDataDetailPk=uddi:b3094bc9-8756-4ecc-9141-9144b98a531e&fileDetailSn=1"

ATCH=$(curl -fsS "$META" | jq -r .atchFileId)
[ -n "$ATCH" ] && [ "$ATCH" != "null" ] || { echo "atchFileId 조회 실패 — 분기 갱신으로 publicDataDetailPk가 바뀌었을 수 있다" >&2; exit 1; }

echo ">>> atchFileId=$ATCH"
curl -fSL -o "$OUT_DIR/sangga.zip" \
  "https://www.data.go.kr/cmm/cmm/fileDownload.do?atchFileId=${ATCH}&fileDetailSn=1"

echo ">>> 압축 해제 (파일명 cp437→cp949 재해석 — macOS unzip은 -O 미지원이라 python으로)"
python3 - "$OUT_DIR" <<'PYEOF'
import zipfile, sys, os
d = sys.argv[1]
os.makedirs(os.path.join(d, "sangga"), exist_ok=True)
with zipfile.ZipFile(os.path.join(d, "sangga.zip")) as z:
    for info in z.infolist():
        if info.is_dir():
            continue
        try:
            name = info.filename.encode("cp437").decode("cp949")
        except (UnicodeDecodeError, UnicodeEncodeError):
            name = info.filename
        target = os.path.join(d, "sangga", os.path.basename(name))
        with z.open(info) as src, open(target, "wb") as dst:
            while chunk := src.read(1 << 20):
                dst.write(chunk)
PYEOF
ls -lh "$OUT_DIR/sangga" | sed -n '1,20p'
echo ">>> 서울 CSV를 transform.py에 넘기면 된다"
