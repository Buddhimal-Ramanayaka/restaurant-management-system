"""Insert 2.1.1 The Existing Manual Workflow, with the new flow chart.

The examiner asked for a flow chart of the existing system. Section 1.2 documents
the manual process in prose but nothing charts it, so this adds the subsection at
the head of 2.1 - the current operation is described before the commercial
alternatives are reviewed - and pushes Cloudbeds, Maestro and eZee down one.

Because the new figure sits above the use case diagram, it takes the number 2.1
and the use case diagram becomes 2.2. That renumber touches four places: the
caption, the List of Figures entry, and two sentences of prose.
"""
import zipfile, re, shutil, os, struct
import xml.dom.minidom as M

SRC = '_docx/out.docx'
FIG = '_diagrams/fig2_1_existing.png'
EMU = 914400
MAX_W, MAX_H = 5.80, 9.28

H3_PPR = ('<w:pPr><w:pStyle w:val="Heading3"/><w:spacing w:before="260" w:after="140" '
          'w:line="360" w:lineRule="auto"/></w:pPr>')
H3_RPR = '<w:rPr><w:b/><w:bCs/><w:sz w:val="28"/><w:szCs w:val="28"/></w:rPr>'
BD_PPR = ('<w:pPr><w:spacing w:before="100" w:after="100" w:line="360" '
          'w:lineRule="auto"/><w:jc w:val="both"/></w:pPr>')
BD_RPR = '<w:rPr><w:sz w:val="24"/><w:szCs w:val="24"/></w:rPr>'
CAP_PPR = ('<w:pPr><w:spacing w:before="60" w:after="200" w:line="360" '
           'w:lineRule="auto"/><w:jc w:val="center"/></w:pPr>')
CAP_RPR = '<w:rPr><w:i/><w:iCs/><w:color w:val="444444"/></w:rPr>'

PROSE = [
 "Daiya Food Restaurant currently operates without any digital point-of-sale or "
 "inventory system. An order is taken on a paper pad, transcribed onto a handwritten "
 "kitchen order ticket and physically carried to the kitchen pass, with the carbon copy "
 "retained for billing. Ingredient stock is held in a physical ledger reconciled at the "
 "close of service, and bills are computed on a calculator. Figure 2.1 charts that "
 "process end to end across the four roles it involves.",

 "Charting the workflow shows that the problems set out in Section 1.2 are properties of "
 "the process itself rather than isolated lapses. The two to five minutes a ticket takes "
 "to reach the pass is not a delay that better staffing would remove; it is the time cost "
 "of moving paper, and it grows into a queue precisely when service is busiest. Likewise, "
 "no point in the flow deducts stock when a dish is ordered, so the five to fifteen "
 "percent monthly divergence between recorded and physical stock is inevitable: "
 "consumption is only ever inferred, at the end of the day, from what remains on the "
 "shelf.",

 "Three of the steps produce no record at all. When the kitchen finds an ingredient "
 "missing, the dish is struck off the ticket and the waiter is told verbally, so the same "
 "shortage is discovered again on the next order. When a discount is applied, nothing "
 "captures who authorised it. When a void or refund occurs, the only trace is a paper "
 "note. Each of these is a point at which the process discards information that "
 "management later needs, which is why post-hoc investigation of a cash or stock "
 "discrepancy is not possible.",

 "The final lane is where the absence of data becomes a management problem. Sales are "
 "tallied by hand from a pile of tickets, stock is counted on the shelf, and reorder "
 "quantities are judged from memory and a visual check of the store room. There is no "
 "trigger when an ingredient runs low, and no view of which items are profitable or when "
 "demand peaks. The proposed system addresses each of these points directly, and the "
 "corresponding workflow under the new system is given as Figure 3.7.",
]

def esc(s): return s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
def para(t, ppr=BD_PPR, rpr=BD_RPR):
    return '<w:p>%s<w:r>%s<w:t xml:space="preserve">%s</w:t></w:r></w:p>' % (ppr, rpr, esc(t))

z = zipfile.ZipFile(SRC)
xml = z.read('word/document.xml').decode('utf8')
rels = z.read('word/_rels/document.xml.rels').decode('utf8')
head, body, tail = re.match(r'(.*<w:body>)(.*)(</w:body>.*)', xml, re.S).groups()
paras = re.findall(r'<w:p(?: [^>]*)?>.*?</w:p>|<w:p/>', body, re.S)
def txt(p): return ''.join(re.findall(r'<w:t[^>]*>([^<]*)</w:t>', p)).strip()

# ---- a new image part + relationship -------------------------------------
existing = [int(m.group(1)) for m in re.finditer(r'Target="media/image(\d+)\.png"', rels)]
newnum = max(existing) + 1
newmedia = 'media/image%d.png' % newnum
rids = [int(m.group(1)) for m in re.finditer(r'Id="rId(\d+)"', rels)]
newrid = 'rId%d' % (max(rids) + 1)
rels = rels.replace('</Relationships>',
    '<Relationship Id="%s" Type="http://schemas.openxmlformats.org/officeDocument/'
    '2006/relationships/image" Target="%s"/></Relationships>' % (newrid, newmedia))

nw, nh = struct.unpack('>II', open(FIG, 'rb').read(33)[16:24])
k = min(MAX_W / nw, MAX_H / nh)
cx, cy = int(round(nw * k * EMU)), int(round(nh * k * EMU))
print('  figure %dx%d -> %.2f x %.2f in, %d dpi' % (nw, nh, cx / EMU, cy / EMU, nw / (cx / EMU)))

drawing = (
 '<w:p><w:pPr><w:keepNext/><w:spacing w:before="160" w:after="60"/>'
 '<w:jc w:val="center"/></w:pPr><w:r><w:drawing>'
 '<wp:inline distT="0" distB="0" distL="0" distR="0">'
 '<wp:extent cx="%d" cy="%d"/><wp:effectExtent l="0" t="0" r="0" b="0"/>'
 '<wp:docPr id="9001" name="Figure 2.1"/><a:graphic '
 'xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData '
 'uri="http://schemas.openxmlformats.org/drawingml/2006/picture">'
 '<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">'
 '<pic:nvPicPr><pic:cNvPr id="9001" name="Figure 2.1"/><pic:cNvPicPr/></pic:nvPicPr>'
 '<pic:blipFill><a:blip r:embed="%s"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>'
 '<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="%d" cy="%d"/></a:xfrm>'
 '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>'
 '</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>'
 % (cx, cy, newrid, cx, cy))

BM = 7200
block = (
 '<w:p>%s<w:bookmarkStart w:id="%d" w:name="_Rms_sec_2_1_1_manual"/>'
 '<w:r>%s<w:t xml:space="preserve">2.1.1  The Existing Manual Workflow</w:t></w:r>'
 '<w:bookmarkEnd w:id="%d"/></w:p>' % (H3_PPR, BM, H3_RPR, BM)
 + ''.join(para(t) for t in PROSE[:1])
 + drawing
 + '<w:p>%s<w:bookmarkStart w:id="%d" w:name="_Rms_fig_2_1"/><w:r>%s'
   '<w:t xml:space="preserve">Figure 2.1: Flow Chart -  Existing Manual Workflow at '
   'Daiya Food Restaurant</w:t></w:r><w:bookmarkEnd w:id="%d"/></w:p>'
   % (CAP_PPR, BM + 1, CAP_RPR, BM + 1)
 + ''.join(para(t) for t in PROSE[1:]))

# ---- splice in before 2.1.1 Cloudbeds ------------------------------------
c = [i for i, p in enumerate(paras)
     if 'Heading3' in p and txt(p).startswith('2.1.1') and 'Cloudbeds' in txt(p)]
assert len(c) == 1, c
anchor = paras[c[0]]

newp = list(paras)
ren = 0
for i, p in enumerate(paras):
    t = txt(p)
    is_toc = 'w:leader="dot"' in p
    is_h = 'Heading3' in p
    if not (is_toc or is_h):
        # prose and caption references to the old Figure 2.1
        if 'Figure 2.1' in t and not t.startswith('2.1'):
            newp[i] = p.replace('Figure 2.1', 'Figure 2.2'); ren += 1
        continue
    m = re.match(r'^2\.1\.([123])\b', t)
    if m and (is_h or is_toc):
        newp[i] = p.replace('2.1.%s' % m.group(1), '2.1.%d' % (int(m.group(1)) + 1), 1); ren += 1
    elif is_toc and t.startswith('Figure 2.1'):
        newp[i] = p.replace('Figure 2.1', 'Figure 2.2', 1); ren += 1

print('  renumbered %d headings / TOC entries / references' % ren)

reb, cur = [], 0
for i, p in enumerate(paras):
    a = body.find(p, cur); assert a >= 0
    reb.append(body[cur:a])
    if p is anchor:
        reb.append(block)
    reb.append(newp[i]); cur = a + len(p)
reb.append(body[cur:])
body = ''.join(reb)

xml = head + body + tail
M.parseString(xml.encode('utf8')); M.parseString(rels.encode('utf8'))

shutil.copy(SRC, '_docx/_t.docx')
zin = zipfile.ZipFile('_docx/_t.docx')
zo = zipfile.ZipFile('_docx/_n.docx', 'w', zipfile.ZIP_DEFLATED)
for it in zin.infolist():
    n = it.filename
    if n == 'word/document.xml': d = xml.encode('utf8')
    elif n == 'word/_rels/document.xml.rels': d = rels.encode('utf8')
    else: d = zin.read(n)
    zo.writestr(it, d)
zo.writestr('word/' + newmedia, open(FIG, 'rb').read())
zo.close(); zin.close()
shutil.move('_docx/_n.docx', SRC); os.remove('_docx/_t.docx')
print('  added %s as %s' % (os.path.basename(FIG), newmedia))
print('\ninserted 2.1.1 The Existing Manual Workflow (%d paragraphs + figure)' % (len(PROSE) + 2))
