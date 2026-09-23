"""Extract the report into report_content.json for the preview artifact.

Blocks come out in document order as {"k": p|h1|h2|h3|fig|cap|table, ...}.

Two things are deliberately dropped, both caught when the first preview rendered:
dot-leader paragraphs, which are the TOC and the two lists and read as broken body
text ("References54"); and decorative images under 3 in wide - the university crest
and the supervisor's signature - which otherwise get paired with whatever caption
follows them.

    python _docx/extract_preview.py && python _docx/build_preview.py
"""
import zipfile, re, json, io, os, struct

SRC = '_docx/out.docx'
OUT = '_docx/report_content.json'
FIGDIR = '_docx/preview/fig'
EMU = 914400
MIN_FIG_IN = 3.0

z = zipfile.ZipFile(SRC)
xml = z.read('word/document.xml').decode('utf8')
rels = z.read('word/_rels/document.xml.rels').decode('utf8')
rid = {m.group(1): m.group(2)
       for m in re.finditer(r'Id="(rId\d+)"[^>]*Target="(media/[^"]+)"', rels)}

body = re.search(r'<w:body>(.*)</w:body>', xml, re.S).group(1)
blocks = re.findall(r'<w:tbl>.*?</w:tbl>|<w:p(?: [^>]*)?>.*?</w:p>|<w:p/>', body, re.S)

def txt(s):
    return re.sub(r'\s+', ' ', ''.join(re.findall(r'<w:t[^>]*>([^<]*)</w:t>', s))).strip()

def unesc(s):
    return s.replace('&lt;', '<').replace('&gt;', '>').replace('&amp;', '&')

out = []
os.makedirs(FIGDIR, exist_ok=True)
kept_media = set()

for b in blocks:
    if b.startswith('<w:tbl>'):
        rows = []
        for tr in re.findall(r'<w:tr(?: [^>]*)?>.*?</w:tr>', b, re.S):
            rows.append([unesc(txt(tc))
                         for tc in re.findall(r'<w:tc>.*?</w:tc>', tr, re.S)])
        if rows:
            out.append({'k': 'table', 'rows': rows})
        continue

    if 'w:leader="dot"' in b:          # TOC / List of Figures / List of Tables
        continue

    if '<w:drawing>' in b:
        m = re.search(r'r:embed="(rId\d+)"', b)
        e = re.search(r'<wp:extent cx="(\d+)" cy="(\d+)"/>', b)
        if m and e and rid.get(m.group(1)):
            w, h = int(e.group(1)) / EMU, int(e.group(2)) / EMU
            src = rid[m.group(1)]
            if w >= MIN_FIG_IN:        # skip the crest and the signature
                data = z.read('word/' + src)
                px = [0, 0]
                if data[:8] == b'\x89PNG\r\n\x1a\n':
                    px = list(struct.unpack('>II', data[16:24]))
                out.append({'k': 'fig', 'src': src, 'w': w, 'h': h, 'px': px,
                            'dpi': int(px[0] / w) if px[0] and w else 0,
                            'bytes': len(data)})
                if src not in kept_media:
                    open(os.path.join(FIGDIR, src.replace('media/', '')), 'wb').write(data)
                    kept_media.add(src)
        continue

    t = unesc(txt(b))
    if not t:
        continue
    style = re.search(r'<w:pStyle w:val="(Heading[123])"/>', b)
    if style:
        out.append({'k': 'h' + style.group(1)[-1], 't': t})
    elif re.match(r'^(Figure|Table) \d+\.\d+:', t):
        out.append({'k': 'cap', 't': t})
    else:
        out.append({'k': 'p', 't': t})

json.dump(out, io.open(OUT, 'w', encoding='utf8'), ensure_ascii=False, indent=0)
kinds = {}
for b in out:
    kinds[b['k']] = kinds.get(b['k'], 0) + 1
print('%d blocks -> %s' % (len(out), OUT))
print('  ' + '  '.join('%s:%d' % kv for kv in sorted(kinds.items())))
print('  %d figure images written to %s' % (len(kept_media), FIGDIR))
