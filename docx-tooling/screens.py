"""Item 9 - replace the Section 3.9 mock-ups with screenshots of the running system.

Figures 3.8, 3.9 and 3.10 were rendered mock-ups: a drawn browser chrome, placeholder
item thumbnails and composed sample data. The examiner asked for screenshots of the
project, so each is replaced with a capture taken from the system running against the
real database (see _diagrams/shots_app.mjs), and a fourth view - the cashier billing
terminal, which had no figure at all - is added as 3.9.4 / Figure 3.11.

The admin panel is deliberately not included: its user table still lists three test
accounts created during development, and a screenshot of it would put them in the
report.

Images are mapped by caption text, not by media filename, because the user's own
edits have renumbered word/media before.
"""
import zipfile, re, shutil, os, struct
import xml.dom.minidom as M

SRC = '_docx/out.docx'
EMU = 914400
MAX_W, MAX_H = 5.80, 9.28

SWAP = {
    'Figure 3.8':  '_diagrams/app_pos.png',
    'Figure 3.9':  '_diagrams/app_kitchen.png',
    'Figure 3.10': '_diagrams/app_manager.png',
}
NEWFIG = '_diagrams/app_cashier.png'

H3 = ('<w:pPr><w:pStyle w:val="Heading3"/><w:spacing w:before="200" w:after="100" '
      'w:line="360" w:lineRule="auto"/></w:pPr>')
H3R = '<w:rPr><w:b/><w:bCs/><w:sz w:val="28"/><w:szCs w:val="28"/></w:rPr>'
BD = ('<w:pPr><w:spacing w:before="100" w:after="100" w:line="360" '
      'w:lineRule="auto"/><w:jc w:val="both"/></w:pPr>')
BDR = '<w:rPr><w:sz w:val="24"/><w:szCs w:val="24"/></w:rPr>'
CAP = ('<w:pPr><w:spacing w:before="60" w:after="200" w:line="360" '
       'w:lineRule="auto"/><w:jc w:val="center"/></w:pPr>')
CAPR = '<w:rPr><w:i/><w:iCs/><w:color w:val="444444"/></w:rPr>'

CASHIER_PROSE = (
    "The Cashier Billing Terminal is the only screen that handles money, so it is laid "
    "out to make the amount being charged and the amount being taken visible at the same "
    "time. A shift banner across the top shows the running cash, card and digital totals "
    "for the open shift together with the End Shift control, which is what the cashier "
    "reconciles against the physical drawer at close. The left column lists every order "
    "the kitchen has marked ready, so billing is driven by the order queue rather than by "
    "the cashier remembering which tables are waiting. Selecting one renders the itemised "
    "bill on the right: line quantities and unit prices, then subtotal, discount, service "
    "charge and VAT as separate lines rather than a single total, so a disputed charge can "
    "be traced to the rule that produced it. Payment method is chosen from Cash, Card or "
    "Digital, and for cash the tendered amount is entered and change is computed and shown "
    "before the settlement is committed. Settling the payment posts the transaction, "
    "closes the bill and returns the table to the floor plan in one action.")


def png(p):
    return struct.unpack('>II', open(p, 'rb').read(33)[16:24])


def extent(path, maxh=MAX_H):
    w, h = png(path)
    k = min(MAX_W / w, maxh / h)
    return int(round(w * k * EMU)), int(round(h * k * EMU)), w, h


z = zipfile.ZipFile(SRC)
xml = z.read('word/document.xml').decode('utf8')
rels = z.read('word/_rels/document.xml.rels').decode('utf8')
z.close()
rid = {m.group(1): m.group(2)
       for m in re.finditer(r'Id="(rId\d+)"[^>]*Target="(media/[^"]+)"', rels)}

head, body, tail = re.match(r'(.*<w:body>)(.*)(</w:body>.*)', xml, re.S).groups()
paras = re.findall(r'<w:p(?: [^>]*)?>.*?</w:p>|<w:p/>', body, re.S)
def txt(p): return ''.join(re.findall(r'<w:t[^>]*>([^<]*)</w:t>', p)).strip()

# ---- swap the three existing figures -------------------------------------
replace_media, newp = {}, list(paras)
for i, p in enumerate(paras):
    m = re.match(r'^(Figure \d+\.\d+):', txt(p))
    if not m or m.group(1) not in SWAP or 'w:leader="dot"' in p:
        continue
    fig = m.group(1)
    j = next(k for k in range(i - 1, i - 6, -1) if '<w:drawing>' in paras[k])
    target = rid[re.search(r'r:embed="(rId\d+)"', paras[j]).group(1)]
    src = SWAP[fig]
    cx, cy, w, h = extent(src)
    e = re.search(r'<wp:extent cx="(\d+)" cy="(\d+)"/>', paras[j])
    old = (int(e.group(1)) / EMU, int(e.group(2)) / EMU)
    q = paras[j].replace(e.group(0), '<wp:extent cx="%d" cy="%d"/>' % (cx, cy))
    newp[j] = re.sub(r'<a:ext cx="\d+" cy="\d+"/>', '<a:ext cx="%d" cy="%d"/>' % (cx, cy), q)
    replace_media[target] = src
    print('  %-11s -> %-22s %4dx%-5d  %.2f x %.2f in (was %.2f x %.2f)'
          % (fig, os.path.basename(src), w, h, cx / EMU, cy / EMU, *old))
assert len(replace_media) == 3, replace_media

# ---- a new image part for the cashier figure -----------------------------
num = max(int(m.group(1)) for m in re.finditer(r'Target="media/image(\d+)\.png"', rels)) + 1
media = 'media/image%d.png' % num
newrid = 'rId%d' % (max(int(m.group(1)) for m in re.finditer(r'Id="rId(\d+)"', rels)) + 1)
rels = rels.replace('</Relationships>',
    '<Relationship Id="%s" Type="http://schemas.openxmlformats.org/officeDocument/'
    '2006/relationships/image" Target="%s"/></Relationships>' % (newrid, media))

cx, cy, w, h = extent(NEWFIG)
print('  %-11s -> %-22s %4dx%-5d  %.2f x %.2f in (new)'
      % ('Figure 3.11', os.path.basename(NEWFIG), w, h, cx / EMU, cy / EMU))

drawing = (
 '<w:p><w:pPr><w:keepNext/><w:spacing w:before="200" w:after="60" w:line="360" '
 'w:lineRule="auto"/><w:jc w:val="center"/></w:pPr><w:r><w:drawing>'
 '<wp:inline distT="0" distB="0" distL="0" distR="0">'
 '<wp:extent cx="%d" cy="%d"/><wp:effectExtent l="0" t="0" r="0" b="0"/>'
 '<wp:docPr id="9101" name="Figure 3.11"/><a:graphic '
 'xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData '
 'uri="http://schemas.openxmlformats.org/drawingml/2006/picture">'
 '<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">'
 '<pic:nvPicPr><pic:cNvPr id="9101" name="Figure 3.11"/><pic:cNvPicPr/></pic:nvPicPr>'
 '<pic:blipFill><a:blip r:embed="%s"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>'
 '<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="%d" cy="%d"/></a:xfrm>'
 '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>'
 '</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>' % (cx, cy, newrid, cx, cy))

block = (
 '<w:p>%s<w:bookmarkStart w:id="7400" w:name="_Rms_sec_3_9_4"/><w:r>%s'
 '<w:t xml:space="preserve">3.9.4  Cashier Billing Terminal</w:t></w:r>'
 '<w:bookmarkEnd w:id="7400"/></w:p>' % (H3, H3R)
 + drawing
 + '<w:p>%s<w:bookmarkStart w:id="7401" w:name="_Rms_fig_3_11"/><w:r>%s'
   '<w:t xml:space="preserve">Figure 3.11: Cashier Billing Terminal -  Shift Totals, '
   'Ready-to-Bill Queue, Itemised Bill and Payment Settlement</w:t></w:r>'
   '<w:bookmarkEnd w:id="7401"/></w:p>' % (CAP, CAPR)
 + '<w:p>%s<w:r>%s<w:t xml:space="preserve">%s</w:t></w:r></w:p>' % (BD, BDR, CASHIER_PROSE))

# ---- rebuild, inserting after the 3.9.3 prose ----------------------------
cap310 = next(i for i, p in enumerate(paras)
              if txt(p).startswith('Figure 3.10:') and 'w:leader="dot"' not in p)
anchor = cap310 + 1                      # the paragraph describing the dashboard
assert txt(paras[anchor]).startswith('The Manager Dashboard'), txt(paras[anchor])[:60]

reb, cur = [], 0
for i, p in enumerate(paras):
    a = body.index(p, cur)
    reb.append(body[cur:a]); reb.append(newp[i]); cur = a + len(p)
    if i == anchor:
        reb.append(block)
reb.append(body[cur:])
body = ''.join(reb)

# ---- the section intro now says these are screenshots --------------------
intro = next(p for p in paras if txt(p).startswith('The user interface was designed with three'))
assert intro.count('</w:t></w:r>') >= 1
add = (' Every figure in this section is a screenshot of the completed system running '
       'against the project database, not a mock-up or wireframe.')
body = body.replace(intro, intro.replace(
    'optional on other views.', 'optional on other views.' + add, 1), 1)
assert add in body

# ---- TOC and List of Figures ---------------------------------------------
def entry(after, text, anchor_name, page, ind):
    global body
    src = next(p for p in paras if 'w:leader="dot"' in p and txt(p).startswith(after))
    new = ('<w:p><w:pPr><w:spacing w:before="60" w:after="60" w:line="320" '
           'w:lineRule="auto"/>%s</w:pPr><w:hyperlink w:anchor="%s" w:history="1">'
           '<w:r><w:rPr><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr>'
           '<w:t xml:space="preserve">%s</w:t></w:r><w:r><w:ptab w:relativeTo="margin" '
           'w:alignment="right" w:leader="dot"/></w:r><w:r><w:rPr><w:sz w:val="22"/>'
           '<w:szCs w:val="22"/></w:rPr><w:t>%d</w:t></w:r></w:hyperlink></w:p>'
           % ('<w:ind w:left="360"/>' if ind else '', anchor_name, text, page))
    at = body.index(src) + len(src)
    body = body[:at] + new + body[at:]

entry('3.9.3', '      3.9.4  Cashier Billing Terminal', '_Rms_sec_3_9_4', 34, True)
entry('Figure 3.10:',
      'Figure 3.11: Cashier Billing Terminal - Shift Totals, Ready-to-Bill Queue, '
      'Itemised Bill and Payment Settlement', '_Rms_fig_3_11', 35, False)
print('  added 3.9.4 Cashier Billing Terminal + Figure 3.11, with TOC and LoF entries')

xml = head + body + tail
M.parseString(xml.encode('utf8')); M.parseString(rels.encode('utf8'))

shutil.copy(SRC, '_docx/_t.docx')
zin = zipfile.ZipFile('_docx/_t.docx')
zo = zipfile.ZipFile('_docx/_n.docx', 'w', zipfile.ZIP_DEFLATED)
for it in zin.infolist():
    n = it.filename
    if n == 'word/document.xml':
        d = xml.encode('utf8')
    elif n == 'word/_rels/document.xml.rels':
        d = rels.encode('utf8')
    elif n.replace('word/', '') in replace_media:
        d = open(replace_media[n.replace('word/', '')], 'rb').read()
    else:
        d = zin.read(n)
    zo.writestr(it, d)
zo.writestr('word/' + media, open(NEWFIG, 'rb').read())
zo.close(); zin.close()
shutil.move('_docx/_n.docx', SRC); os.remove('_docx/_t.docx')
print('\ndocument.xml well-formed; written')
