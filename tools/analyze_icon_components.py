from pathlib import Path
from PIL import Image
from collections import deque

p = Path('/home/ubuntu/velum/app/src/main/res/drawable/logo_velum_mark.png')
im = Image.open(p).convert('RGBA')
a = im.getchannel('A')
threshold = 24
pix = a.load()
w, h = im.size
seen = bytearray(w * h)
components = []
for y in range(h):
    for x in range(w):
        i = y * w + x
        if seen[i] or pix[x, y] < threshold:
            continue
        q = deque([(x, y)])
        seen[i] = 1
        n = 0; minx = maxx = x; miny = maxy = y
        while q:
            cx, cy = q.popleft(); n += 1
            minx = min(minx, cx); maxx = max(maxx, cx)
            miny = min(miny, cy); maxy = max(maxy, cy)
            for nx, ny in ((cx-1,cy),(cx+1,cy),(cx,cy-1),(cx,cy+1)):
                if 0 <= nx < w and 0 <= ny < h:
                    ni = ny * w + nx
                    if not seen[ni] and pix[nx, ny] >= threshold:
                        seen[ni] = 1; q.append((nx, ny))
        components.append((n, (minx,miny,maxx+1,maxy+1)))
for n, box in sorted(components, reverse=True)[:30]:
    print(n, box)
