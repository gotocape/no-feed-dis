#!/usr/bin/env python3
"""生成纸张纹理 PNG，作为首页背景的叠加层。

为什么手写 PNG 而不用现成图片：
1. 不引入第三方依赖（环境里没有 Pillow）
2. 纹理完全可复现，改 seed 就是另一张纸
3. 体积可控（纯噪点，压缩后约 20KB）

输出：app/src/main/res/drawable-nodpi/paper_texture.png
颜色是围绕中灰的随机扰动，配合 <bitmap android:alpha> 低透明度叠加，
在浅色和深色背景上都能形成细微的明暗起伏，像纸的纤维。
"""

import os
import random
import struct
import zlib

W = H = 128          # 平铺单元。太小会看出重复，太大会撑大 APK
BASE = 128           # 中灰，叠加时才不会偏色
JITTER = 14          # 起伏幅度。超过 20 就显得脏
SEED = 20261025

random.seed(SEED)

raw = bytearray()
for _y in range(H):
    raw.append(0)                       # 每行首字节 = filter type 0 (None)
    for _x in range(W):
        v = max(0, min(255, BASE + random.randint(-JITTER, JITTER)))
        raw += bytes((v, v, v))         # color type 2 = 真彩色 RGB


def chunk(typ: bytes, data: bytes) -> bytes:
    return (
        struct.pack(">I", len(data))
        + typ
        + data
        + struct.pack(">I", zlib.crc32(typ + data) & 0xFFFFFFFF)
    )


png = b"\x89PNG\r\n\x1a\n"
png += chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 2, 0, 0, 0))
png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
png += chunk(b"IEND", b"")

out = os.path.join("app", "src", "main", "res", "drawable-nodpi", "paper_texture.png")
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, "wb") as f:
    f.write(png)

print(f"written: {out}")
print(f"size   : {len(png)} bytes ({W}x{H})")
