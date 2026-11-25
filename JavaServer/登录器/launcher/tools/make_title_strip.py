"""从原版 bg_servers.png 那条「第一步：选择大区」红色标题条裁出一条没有字的标题条。

左端头 x 12..18 原样，字下面那段用 x=158 那一列（字已经结束、渐变还没开始暗）平铺，
后面接原图 158..280 的红→暗渐变。只用原图像素，不画任何新东西。
输出 Assets/title_strip.png（高 25 = y 82..106，含上下各一道暗线）。
"""
import sys
from pathlib import Path
from PIL import Image

assets = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / "A4Launcher" / "Assets"
src = Image.open(assets / "bg_servers.png").convert("RGBA")
top, bottom = 82, 107
cap = src.crop((12, top, 18, bottom))
col = src.crop((158, top, 159, bottom))
tail = src.crop((158, top, 280, bottom))
fill = 160
out = Image.new("RGBA", (cap.width + fill + tail.width, bottom - top))
out.paste(cap, (0, 0))
for x in range(fill):
    out.paste(col, (cap.width + x, 0))
out.paste(tail, (cap.width + fill, 0))
out.save(assets / "title_strip.png")
print("title_strip.png", out.size)
