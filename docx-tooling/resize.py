"""Restore the two figure sizes the author set by hand.

swap_plain.py sized every figure to the page, which overwrote two deliberate
resizes made during the author's own final pass: Figure 3.3 at 5.56 x 8.67 in and
Figure 3.6 at 3.64 x 8.91 in.

Only the height is restored. 5.56 / 8.67 = 0.6418, which is exactly the aspect of
the class diagram, so that resize was a corner drag - the author chose a height and
the width followed. The plain-notation artwork replacing each figure has its own
aspect, so forcing the recorded widths back would stretch the drawing; applying the
recorded heights and deriving the widths reproduces the intent without distortion.
"""
import zipfile, re, shutil, os, struct
import xml.dom.minidom as M

SRC = '_docx/out.docx'
EMU = 914400
MAX_W = 5.80
HEIGHTS = {'Figure 3.3': 8.67, 'Figure 3.6': 8.91}

z = zipfile.ZipFile(SRC)
xml = z.read('word/document.xml').decode('utf8')
rels = z.read('word/_rels/document.xml.rels').decode('utf8')
rid = {m.group(1): m.group(2)
       for m in re.finditer(r'Id="(rId\d+)"[^>]*Target="(media/[^"]+)"', rels)}

head, body, tail = re.match(r'(.*<w:body>)(.*)(</w:body>.*)', xml, re.S).groups()
paras = re.findall(r'<w:p(?: [^>]*)?>.*?</w:p>|<w:p/>', body, re.S)
def txt(p): return ''.join(re.findall(r'<w:t[^>]*>([^<]*)</w:t>', p)).strip()

newp, done = list(paras), 0
for i, p in enumerate(paras):
    m = re.match(r'^(Figure \d+\.\d+):', txt(p))
    if not m or m.group(1) not in HEIGHTS or 'w:leader="dot"' in p:
        continue
    fig = m.group(1)
    j = next(k for k in range(i - 1, i - 6, -1) if '<w:drawing>' in paras[k])
    src = rid[re.search(r'r:embed="(rId\d+)"', paras[j]).group(1)]
    pw, ph = struct.unpack('>II', z.read('word/' + src)[16:24])
    h = HEIGHTS[fig]
    w = min(MAX_W, h * pw / ph)
    h = min(h, w * ph / pw)                      # width cap wins if it binds first
    cx, cy = int(round(w * EMU)), int(round(h * EMU))
    e = re.search(r'<wp:extent cx="(\d+)" cy="(\d+)"/>', paras[j])
    old = (int(e.group(1)) / EMU, int(e.group(2)) / EMU)
    q = paras[j].replace(e.group(0), '<wp:extent cx="%d" cy="%d"/>' % (cx, cy))
    newp[j] = re.sub(r'<a:ext cx="\d+" cy="\d+"/>', '<a:ext cx="%d" cy="%d"/>' % (cx, cy), q)
    done += 1
    print('  %-11s %s  %.2f x %.2f in  (was %.2f x %.2f)  %d dpi'
          % (fig, src, cx / EMU, cy / EMU, old[0], old[1], pw / (cx / EMU)))
assert done == len(HEIGHTS), done
z.close()

reb, cur = [], 0
for i, p in enumerate(paras):
    a = body.index(p, cur)
    reb.append(body[cur:a]); reb.append(newp[i]); cur = a + len(p)
reb.append(body[cur:])
xml = head + ''.join(reb) + tail
M.parseString(xml.encode('utf8'))

shutil.copy(SRC, '_docx/_t.docx')
zin = zipfile.ZipFile('_docx/_t.docx')
zo = zipfile.ZipFile('_docx/_n.docx', 'w', zipfile.ZIP_DEFLATED)
for it in zin.infolist():
    zo.writestr(it, xml.encode('utf8') if it.filename == 'word/document.xml'
                else zin.read(it.filename))
zo.close(); zin.close()
shutil.move('_docx/_n.docx', SRC); os.remove('_docx/_t.docx')
print('\ndocument.xml well-formed; written')
