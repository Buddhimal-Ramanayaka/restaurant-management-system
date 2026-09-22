"""Swap the plain-notation figures into out.docx.

Mapped by caption text, not by media filename: the user's own edits renumbered
word/media, so image5..image12 no longer mean what they did. For each caption the
script walks back to the nearest preceding drawing and replaces whatever that one
points at.

Figures 3.4 and 3.6 are capped at 9.00in rather than the usual 9.28in: at full
height the image plus a two-line caption exceeds the 9.72in text column and Word
overrides keepNext, orphaning the caption onto the next page.
"""
import zipfile, re, shutil, os, struct
import xml.dom.minidom as M

SRC = '_docx/out.docx'
EMU = 914400
MAX_W = 5.80
NEW = {
    'Figure 2.1':  ('_diagrams/fig2_1_usecase.png',      9.28),
    'Figure 3.1':  ('_diagrams/fig3_1_architecture.png', 9.28),
    'Figure 3.2':  ('_diagrams/fig3_2_erd.png',          9.28),
    'Figure 3.3':  ('_diagrams/fig3_3_class.png',        9.28),
    'Figure 3.4':  ('_diagrams/fig3_4_sequence.png',     9.00),
    'Figure 3.5':  ('_diagrams/fig3_5_state.png',        9.28),
    'Figure 3.6':  ('_diagrams/fig3_6.png',              9.00),
    'Figure 3.7':  ('_diagrams/fig3_7_activity.png',     9.28),
}

png = lambda p: struct.unpack('>II', open(p, 'rb').read(33)[16:24])

z = zipfile.ZipFile(SRC)
xml = z.read('word/document.xml').decode('utf8')
rels = z.read('word/_rels/document.xml.rels').decode('utf8')
rid = {m.group(1): m.group(2)
       for m in re.finditer(r'Id="(rId\d+)"[^>]*Target="(media/[^"]+)"', rels)}

head, body, tail = re.match(r'(.*<w:body>)(.*)(</w:body>.*)', xml, re.S).groups()
paras = re.findall(r'<w:p(?: [^>]*)?>.*?</w:p>|<w:p/>', body, re.S)
def txt(p): return ''.join(re.findall(r'<w:t[^>]*>([^<]*)</w:t>', p)).strip()

# caption -> the drawing that precedes it
plan = {}
for i, p in enumerate(paras):
    t = txt(p)
    m = re.match(r'^(Figure \d+\.\d+):', t)
    if not m or m.group(1) not in NEW:
        continue
    if 'w:leader="dot"' in p:          # List of Figures entry, not the caption
        continue
    for j in range(i - 1, max(0, i - 6), -1):
        if '<w:drawing>' in paras[j]:
            r = re.search(r'r:embed="(rId\d+)"', paras[j])
            if r and rid.get(r.group(1)):
                plan[m.group(1)] = (j, rid[r.group(1)])
            break

missing = [k for k in NEW if k not in plan]
assert not missing, 'no drawing found for: %s' % missing

replace_media = {}
newp = list(paras)
for fig, (j, target) in sorted(plan.items()):
    src, maxh = NEW[fig]
    nw, nh = png(src)
    k = min(MAX_W / nw, maxh / nh)
    cx, cy = int(round(nw * k * EMU)), int(round(nh * k * EMU))
    p = paras[j]
    e = re.search(r'<wp:extent cx="(\d+)" cy="(\d+)"/>', p)
    old = (int(e.group(1)) / EMU, int(e.group(2)) / EMU)
    q = p.replace(e.group(0), '<wp:extent cx="%d" cy="%d"/>' % (cx, cy))
    q = re.sub(r'<a:ext cx="\d+" cy="\d+"/>', '<a:ext cx="%d" cy="%d"/>' % (cx, cy), q)
    newp[j] = q
    replace_media[target] = src
    print('  %-11s -> %-32s %5dx%-5d  %.2f x %.2f in (was %.2f x %.2f)  %d dpi'
          % (fig, os.path.basename(src), nw, nh, cx / EMU, cy / EMU, old[0], old[1],
             nw / (cx / EMU)))

reb, cur = [], 0
for i, p in enumerate(paras):
    a = body.find(p, cur); assert a >= 0
    reb.append(body[cur:a]); reb.append(newp[i]); cur = a + len(p)
reb.append(body[cur:])
xml = head + ''.join(reb) + tail
M.parseString(xml.encode('utf8'))

shutil.copy(SRC, '_docx/_t.docx')
zin = zipfile.ZipFile('_docx/_t.docx')
zo = zipfile.ZipFile('_docx/_p.docx', 'w', zipfile.ZIP_DEFLATED)
for it in zin.infolist():
    n = it.filename
    if n == 'word/document.xml':
        d = xml.encode('utf8')
    elif n.replace('word/', '') in replace_media:
        d = open(replace_media[n.replace('word/', '')], 'rb').read()
    else:
        d = zin.read(n)
    zo.writestr(it, d)
zo.close(); zin.close()
shutil.move('_docx/_p.docx', SRC); os.remove('_docx/_t.docx')
print('\n%d figures swapped; document.xml well-formed' % len(plan))
