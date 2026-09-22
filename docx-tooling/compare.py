"""Item 3 - compare the existing systems on features, with screenshots.

Adds to Section 2.1:
  * Figure 2.2, the Cloudbeds reservation calendar, captured from the vendor's own
    product page - it shows directly why a hotel PMS does not fit a restaurant:
    the primary grid is room-nights, not tables and orders.
  * Figure 2.3, the eZee Optimus restaurant POS, from the vendor's product page.
  * Section 2.1.5 and Table 2.1, a capability comparison of the three systems
    against the proposed one.

It also corrects Section 2.1.4. The report asserted that eZee does not implement
ingredient-level deduction. That is wrong for the vendor's restaurant product:
eZee Optimus lists Recipe Management, Inventory Threshold, Inventory Units
Conversion and Purchase Management on its published Advanced plan. The claim held
only for eZee Absolute, the hotel PMS, whose F&B side is billing. The vendor has
also moved: eZee Technosys now trades as Yanolja Cloud Solution, and
ezeetechnosys.com redirects there, so reference [14] is repointed at the live
product page.

Every figure in Table 2.1 comes from the vendors' own published product and
pricing pages, read on 22 September 2026. Cells are what the vendor documents,
not a judgement about what the product can do - the table note says so.

Because both new figures sit above the use case diagram, it becomes Figure 2.4.
"""
import zipfile, re, shutil, os, struct
import xml.dom.minidom as M

SRC = '_docx/out.docx'
EMU = 914400
ACCESSED = '22 September 2026'

FIGS = [
    # caption anchor section, image, printed width cap (in), caption, bookmark
    ('2.1.3', '_diagrams/comp_cloudbeds.png', 5.80,
     'Figure 2.2: Cloudbeds Property Management System -  Reservation Calendar. '
     'Source: cloudbeds.com, accessed ' + ACCESSED + '.', '_Rms_fig_2_2'),
    ('2.1.5', '_diagrams/comp_ezee.png', 4.60,
     'Figure 2.3: eZee Optimus Restaurant POS -  Table, Menu and Order Screens. '
     'Source: ezeeoptimus.com, accessed ' + ACCESSED + '.', '_Rms_fig_2_3'),
]

H2 = ('<w:pPr><w:pStyle w:val="Heading2"/><w:spacing w:before="300" w:after="160" '
      'w:line="360" w:lineRule="auto"/></w:pPr>')
H2R = '<w:rPr><w:b/><w:bCs/><w:sz w:val="32"/><w:szCs w:val="32"/></w:rPr>'
H3 = ('<w:pPr><w:pStyle w:val="Heading3"/><w:spacing w:before="200" w:after="100" '
      'w:line="360" w:lineRule="auto"/></w:pPr>')
H3R = '<w:rPr><w:b/><w:bCs/><w:sz w:val="28"/><w:szCs w:val="28"/></w:rPr>'
BD = ('<w:pPr><w:spacing w:before="100" w:after="100" w:line="360" '
      'w:lineRule="auto"/><w:jc w:val="both"/></w:pPr>')
BDR = '<w:rPr><w:sz w:val="24"/><w:szCs w:val="24"/></w:rPr>'
CAP = ('<w:pPr><w:spacing w:before="60" w:after="200" w:line="360" '
       'w:lineRule="auto"/><w:jc w:val="center"/></w:pPr>')
CAPR = '<w:rPr><w:i/><w:iCs/><w:color w:val="444444"/></w:rPr>'
NOTE = ('<w:pPr><w:spacing w:before="40" w:after="200" w:line="280" '
        'w:lineRule="auto"/><w:jc w:val="both"/></w:pPr>')
NOTER = '<w:rPr><w:sz w:val="18"/><w:szCs w:val="18"/><w:color w:val="444444"/></w:rPr>'

COLS = [1500, 875, 875, 875, 875]
HEADERS = ['Capability', 'Cloudbeds', 'Maestro PMS', 'eZee Optimus', 'Proposed RMS']
ROWS = [
 ('Primary design target',
  'Hotel and short-stay property management',
  'Hotel, resort and conference property management',
  'Restaurant and bar point of sale',
  'Standalone SME restaurant'),
 ('Table and floor-plan management',
  'Rooms; restaurant tables only through the attached POS module',
  'Rooms; F&amp;B is a secondary module',
  'Yes -  table management listed on the Advanced plan',
  'Yes -  floor plan with a five-state table lifecycle'),
 ('Kitchen coordination',
  'Order ticket list within the POS module',
  'F&amp;B ticketing within the hotel module',
  'Printed KOTs and vouchers',
  'Live Kanban board, per-line item status, WebSocket push to waiter'),
 ('Recipe-to-ingredient deduction on sale',
  'Not in the published feature list',
  'Not in the published feature list',
  'Yes -  recipe management with unit conversion',
  'Yes -  deduction inside the order transaction, on an append-only ledger'),
 ('Automatic reorder trigger',
  'Not in the published feature list',
  'Not in the published feature list',
  'Yes -  inventory threshold alerts and purchase management',
  'Yes -  purchase order auto-drafted at the reorder level'),
 ('Shift cash reconciliation with variance',
  'Not in the published feature list',
  'Not in the published feature list',
  'Shift reports and day close',
  'Yes -  declared drawer against system total, manager review'),
 ('Per-item profitability (revenue against COGS)',
  'Reporting suite; item-level COGS not published',
  'Reporting suite; item-level COGS not published',
  'Inventory and sales reports',
  'Yes -  margin per menu item on the manager dashboard'),
 ('Audit trail of discounts, voids and adjustments',
  'Not in the published feature list',
  'Not in the published feature list',
  'Audit reports listed on the Advanced plan',
  'Yes -  every action attributed to a user and retained'),
 ('Published price',
  'Quotation only; no price published',
  'Quotation only; enterprise per-property licence',
  'USD 60 per month per outlet, Advanced plan, paid yearly',
  'No licence fee -  self-hosted'),
 ('Deployment',
  'Vendor cloud (SaaS)',
  'Vendor-hosted or on-premise',
  'Vendor cloud (SaaS)',
  'Self-hosted on a single server or on-premise machine'),
 ('Control of data and source code',
  'Vendor-controlled; API access by plan',
  'Vendor-controlled; API management module',
  'Vendor-controlled; third-party integrations charged separately',
  'Database and source code held by the restaurant'),
]

TABLE_NOTE = (
 'A cell reading "not in the published feature list" means the capability does not '
 'appear in that vendor’s public product or pricing documentation. It is a '
 'statement about what the vendor publishes, not a test result: none of the three '
 'systems was installed or trialled for this review. Compiled from cloudbeds.com, '
 'maestropms.com and ezeeoptimus.com, accessed ' + ACCESSED + '.')

CLOUDBEDS_FIG = (
 'Figure 2.2 reproduces the reservation calendar that Cloudbeds publishes as the '
 'primary screen of its property management system. The grid is organised by room '
 'and by night, with occupancy percentage and nightly rate across the top and '
 'individual rooms down the side. Nothing in that layout corresponds to a table, a '
 'cover or an open order, which is the concrete form the mismatch takes: a '
 'restaurant using it would be managing its service through a screen designed to '
 'answer a different question.')

MAESTRO_NOTE = (
 'No figure is given for Maestro. The vendor publishes no images of the product '
 'interface anywhere on maestropms.com, so the assessment above rests on its '
 'published feature documentation alone; that absence is itself recorded in Table '
 '2.1.')

EZEE_REWRITE = [
 'eZee Absolute is a hotel management system with modules for reservation '
 'management, group booking, laundry, housekeeping and multi-currency settlement '
 '[14]. Its food and beverage side is restaurant billing attached to a hotel '
 'property rather than a restaurant platform in its own right. The vendor’s '
 'restaurant product is a separate system, eZee Optimus, and it is that product '
 'rather than eZee Absolute which is the closer comparison for this project. Both '
 'now trade under Yanolja Cloud Solution, which acquired eZee Technosys; '
 'ezeetechnosys.com redirects to the Yanolja Cloud Solution site.',

 'eZee Optimus is the one system reviewed here that does implement ingredient-level '
 'stock control. Its published Advanced plan lists recipe management, real-time '
 'stock, inventory threshold alerts, inventory unit conversion and purchase '
 'management alongside table and order management, so consumption is derived from '
 'sales rather than counted at close of service. Figure 2.3 shows the product’s '
 'ordering screens as the vendor publishes them.',

 'Two differences remain material for Daiya Food Restaurant. The first is cost and '
 'control: the Advanced plan is priced at USD 60 per outlet per month paid annually, '
 'the system is hosted by the vendor, and third-party integrations are charged '
 'separately, so the restaurant neither holds its own operational data nor can '
 'extend the system itself. The second is kitchen coordination. eZee Optimus '
 'produces printed kitchen order tickets and vouchers; there is no live kitchen '
 'display with per-line item status and no push back to the waiter when a dish is '
 'ready, which is the specific loop Section 1.2 identifies as the cause of the two '
 'to five minute delay and of dishes going cold at the pass.',
]

CLOSING = (
 'Taken together the three reviews point to one conclusion. Cloudbeds and Maestro '
 'originate in hotel management software and treat food and beverage as an attached '
 'module, so the operational unit they model is the room-night rather than the '
 'table and the order. eZee Optimus is genuinely a restaurant system and does close '
 'the recipe-to-stock loop, but it is a vendor-hosted subscription whose kitchen '
 'coordination stops at a printed ticket. None of the three combines ingredient-level '
 'stock control, live kitchen coordination and a complete audit trail in a system '
 'the restaurant itself owns and can run on one machine. That combination is the gap '
 'this project is designed to fill, and Table 2.1 sets out the comparison in full.')

SUMMARY_INTRO = (
 'Table 2.1 sets the three systems reviewed above against the proposed system on the '
 'capabilities that Chapter 1 identified as the operational problems at Daiya Food '
 'Restaurant. The entries record what each vendor publishes, so the table is a '
 'comparison of documented capability rather than of measured performance.')

# ---------------------------------------------------------------------------
def esc(s): return s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;') \
                    .replace('&amp;amp;', '&amp;').replace('&amp;lt;', '&lt;')

def para(t, ppr=BD, rpr=BDR):
    return '<w:p>%s<w:r>%s<w:t xml:space="preserve">%s</w:t></w:r></w:p>' % (ppr, rpr, t)

bmid = [7500]
def heading(text, anchor, ppr, rpr):
    i = bmid[0]; bmid[0] += 1
    return ('<w:p>%s<w:bookmarkStart w:id="%d" w:name="%s"/><w:r>%s'
            '<w:t xml:space="preserve">%s</w:t></w:r><w:bookmarkEnd w:id="%d"/></w:p>'
            % (ppr, i, anchor, rpr, text, i))

def cell(text, w, bold=False, fill='FFFFFF'):
    rpr = '<w:rPr>%s<w:sz w:val="18"/><w:szCs w:val="18"/></w:rPr>' % (
        '<w:b/><w:bCs/>' if bold else '')
    return ('<w:tc><w:tcPr><w:tcW w:w="%d" w:type="pct"/>'
            '<w:shd w:val="clear" w:color="auto" w:fill="%s"/></w:tcPr>'
            '<w:p><w:pPr><w:spacing w:before="40" w:after="40" w:line="240" '
            'w:lineRule="auto"/></w:pPr><w:r>%s<w:t xml:space="preserve">%s</w:t>'
            '</w:r></w:p></w:tc>' % (w, fill, rpr, text))

def table():
    out = ['<w:tbl><w:tblPr><w:tblW w:w="5000" w:type="pct"/><w:tblBorders>'
           '<w:top w:val="single" w:sz="4" w:space="0" w:color="auto"/>'
           '<w:left w:val="single" w:sz="4" w:space="0" w:color="auto"/>'
           '<w:bottom w:val="single" w:sz="4" w:space="0" w:color="auto"/>'
           '<w:right w:val="single" w:sz="4" w:space="0" w:color="auto"/>'
           '<w:insideH w:val="single" w:sz="4" w:space="0" w:color="auto"/>'
           '<w:insideV w:val="single" w:sz="4" w:space="0" w:color="auto"/></w:tblBorders>'
           '<w:tblCellMar><w:left w:w="40" w:type="dxa"/><w:right w:w="40" w:type="dxa"/>'
           '</w:tblCellMar><w:tblLook w:val="04A0" w:firstRow="1" w:lastRow="0" '
           'w:firstColumn="1" w:lastColumn="0" w:noHBand="0" w:noVBand="1"/></w:tblPr>'
           '<w:tblGrid>' + ''.join('<w:gridCol w:w="%d"/>' % int(c * 8381 / 5000)
                                   for c in COLS) + '</w:tblGrid>']
    out.append('<w:tr><w:trPr><w:tblHeader/></w:trPr>'
               + ''.join(cell(h, w, True, 'D9D9D9') for h, w in zip(HEADERS, COLS))
               + '</w:tr>')
    for r in ROWS:
        out.append('<w:tr>' + cell(r[0], COLS[0], True)
                   + ''.join(cell(v, w) for v, w in zip(r[1:], COLS[1:])) + '</w:tr>')
    out.append('</w:tbl>')
    return ''.join(out)

# ---------------------------------------------------------------------------
z = zipfile.ZipFile(SRC)
xml = z.read('word/document.xml').decode('utf8')
rels = z.read('word/_rels/document.xml.rels').decode('utf8')
z.close()
head, body, tail = re.match(r'(.*<w:body>)(.*)(</w:body>.*)', xml, re.S).groups()
paras = re.findall(r'<w:p(?: [^>]*)?>.*?</w:p>|<w:p/>', body, re.S)
def txt(p): return ''.join(re.findall(r'<w:t[^>]*>([^<]*)</w:t>', p)).strip()

def only(pred, what):
    hits = [p for p in paras if pred(p)]
    assert len(hits) == 1, '%s -> %d' % (what, len(hits))
    return hits[0]

nextnum = max(int(m.group(1)) for m in re.finditer(r'Target="media/image(\d+)\.png"', rels)) + 1
nextrid = max(int(m.group(1)) for m in re.finditer(r'Id="rId(\d+)"', rels)) + 1
newmedia = {}
docpr = [9200]

def figure(path, maxw):
    global rels, nextnum, nextrid
    media = 'media/image%d.png' % nextnum; nextnum += 1
    rid = 'rId%d' % nextrid; nextrid += 1
    newmedia[media] = path
    rels = rels.replace('</Relationships>',
        '<Relationship Id="%s" Type="http://schemas.openxmlformats.org/officeDocument/'
        '2006/relationships/image" Target="%s"/></Relationships>' % (rid, media))
    w, h = struct.unpack('>II', open(path, 'rb').read(33)[16:24])
    k = min(maxw / w, 9.28 / h)
    cx, cy = int(round(w * k * EMU)), int(round(h * k * EMU))
    i = docpr[0]; docpr[0] += 1
    print('    %-24s %4dx%-5d -> %.2f x %.2f in  (%d dpi)'
          % (os.path.basename(path), w, h, cx / EMU, cy / EMU, w / (cx / EMU)))
    return ('<w:p><w:pPr><w:keepNext/><w:spacing w:before="200" w:after="60" '
            'w:line="360" w:lineRule="auto"/><w:jc w:val="center"/></w:pPr><w:r><w:drawing>'
            '<wp:inline distT="0" distB="0" distL="0" distR="0">'
            '<wp:extent cx="%d" cy="%d"/><wp:effectExtent l="0" t="0" r="0" b="0"/>'
            '<wp:docPr id="%d" name="Figure"/><a:graphic '
            'xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData '
            'uri="http://schemas.openxmlformats.org/drawingml/2006/picture">'
            '<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">'
            '<pic:nvPicPr><pic:cNvPr id="%d" name="Figure"/><pic:cNvPicPr/></pic:nvPicPr>'
            '<pic:blipFill><a:blip r:embed="%s"/><a:stretch><a:fillRect/></a:stretch>'
            '</pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/>'
            '<a:ext cx="%d" cy="%d"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/>'
            '</a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic>'
            '</wp:inline></w:drawing></w:r></w:p>' % (cx, cy, i, i, rid, cx, cy))

def caption(text, anchor):
    i = bmid[0]; bmid[0] += 1
    return ('<w:p>%s<w:bookmarkStart w:id="%d" w:name="%s"/><w:r>%s'
            '<w:t xml:space="preserve">%s</w:t></w:r><w:bookmarkEnd w:id="%d"/></w:p>'
            % (CAP, i, anchor, CAPR, text, i))

def insert_after(block, new):
    global body
    at = body.index(block) + len(block)
    body = body[:at] + new + body[at:]

print('  figures')
# --- Cloudbeds: figure after the second Cloudbeds paragraph ----------------
cb_last = only(lambda p: txt(p).startswith('However, Cloudbeds was evaluated'), 'Cloudbeds para')
insert_after(cb_last,
             para(CLOUDBEDS_FIG)
             + figure(FIGS[0][1], FIGS[0][2])
             + caption(FIGS[0][3], FIGS[0][4]))

# --- Maestro: the note about no published interface ------------------------
ms_last = only(lambda p: txt(p).startswith('From the perspective of an SME restaurant, Maestro'),
               'Maestro para')
insert_after(ms_last, para(MAESTRO_NOTE))

# --- eZee: replace the heading and the single paragraph --------------------
ez_h = only(lambda p: 'Heading3' in p and txt(p).startswith('2.1.4'), 'eZee heading')
body = body.replace(ez_h, ez_h.replace('Ezee Absolute', 'eZee Optimus and eZee Absolute'), 1)
ez_p = only(lambda p: txt(p).startswith('Ezee Absolute (formerly'), 'eZee para')
body = body.replace(ez_p,
                    para(EZEE_REWRITE[0]) + para(EZEE_REWRITE[1])
                    + figure(FIGS[1][1], FIGS[1][2]) + caption(FIGS[1][3], FIGS[1][4])
                    + para(EZEE_REWRITE[2]), 1)

# --- the closing paragraph, and 2.1.5 with the table -----------------------
old_close = only(lambda p: txt(p).startswith('The common thread across all three systems'),
                 'closing para')
body = body.replace(old_close,
    para(CLOSING)
    + heading('2.1.5  Comparative Summary', '_Rms_sec_2_1_5', H3, H3R)
    + para(SUMMARY_INTRO)
    + caption('Table 2.1: Capability Comparison -  Cloudbeds, Maestro PMS, eZee Optimus '
              'and the Proposed System', '_Rms_tbl_2_1')
    + table()
    + para(TABLE_NOTE, NOTE, NOTER), 1)
print('  2.1.5 Comparative Summary + Table 2.1 (%d rows)' % len(ROWS))

# --- front matter ----------------------------------------------------------
# Done before the renumber below: these lookups anchor on paragraphs that the
# renumber is about to rewrite.
def entry(after_prefix, text, anchor, page, ind):
    global body
    src = only(lambda p: 'w:leader="dot"' in p and txt(p).startswith(after_prefix),
               'TOC anchor ' + after_prefix)
    new = ('<w:p><w:pPr><w:spacing w:before="60" w:after="60" w:line="320" '
           'w:lineRule="auto"/>%s</w:pPr><w:hyperlink w:anchor="%s" w:history="1">'
           '<w:r><w:rPr><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr>'
           '<w:t xml:space="preserve">%s</w:t></w:r><w:r><w:ptab w:relativeTo="margin" '
           'w:alignment="right" w:leader="dot"/></w:r><w:r><w:rPr><w:sz w:val="22"/>'
           '<w:szCs w:val="22"/></w:rPr><w:t>%d</w:t></w:r></w:hyperlink></w:p>'
           % ('<w:ind w:left="360"/>' if ind else '', anchor, text, page))
    at = body.index(src) + len(src)
    body = body[:at] + new + body[at:]

entry('2.1.4', '      2.1.5  Comparative Summary', '_Rms_sec_2_1_5', 8, True)
# both new List of Figures entries go after Figure 2.1, in order, so the list reads
# 2.1, 2.2, 2.3 and then the use case entry that the renumber below turns into 2.4
entry('Figure 2.1:', FIGS[1][3].split('. Source')[0] + '.', FIGS[1][4], 9, False)
entry('Figure 2.1:', FIGS[0][3].split('. Source')[0] + '.', FIGS[0][4], 7, False)

# 2.1.4 TOC label follows the heading
t214 = only(lambda p: 'w:leader="dot"' in p and txt(p).startswith('2.1.4'), '2.1.4 TOC')
body = body.replace(t214, t214.replace('Ezee Absolute', 'eZee Optimus and eZee Absolute'), 1)

# List of Tables: Table 2.1 goes first, before Table 4.1
lot41 = only(lambda p: 'w:leader="dot"' in p and txt(p).startswith('Table 4.1:'), 'Table 4.1 LoT')
new41 = ('<w:p><w:pPr><w:spacing w:before="60" w:after="60" w:line="320" '
         'w:lineRule="auto"/></w:pPr><w:hyperlink w:anchor="_Rms_tbl_2_1" w:history="1">'
         '<w:r><w:rPr><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr>'
         '<w:t xml:space="preserve">Table 2.1: Capability Comparison - Cloudbeds, '
         'Maestro PMS, eZee Optimus and the Proposed System</w:t></w:r>'
         '<w:r><w:ptab w:relativeTo="margin" w:alignment="right" w:leader="dot"/></w:r>'
         '<w:r><w:rPr><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr><w:t>9</w:t></w:r>'
         '</w:hyperlink></w:p>')
at = body.index(lot41)
body = body[:at] + new41 + body[at:]
print('  TOC +1 section, List of Figures +2, List of Tables +1')

# --- the use case diagram becomes Figure 2.4 -------------------------------
n = 0
for p in paras:
    if 'Figure 2.2' in txt(p) and p in body:
        body = body.replace(p, p.replace('Figure 2.2', 'Figure 2.4'), 1); n += 1
assert n >= 3, n
print('  use case diagram renumbered 2.2 -> 2.4 in %d places' % n)

# --- reference [14] --------------------------------------------------------
r14 = only(lambda p: txt(p).startswith('[14]'), 'reference 14')
body = body.replace(r14, r14
    .replace('Ezee Technosys', 'eZee Technosys (Yanolja Cloud Solution)')
    .replace('eZee Absolute -  Hotel Management System',
             'eZee Optimus -  Restaurant POS System')
    .replace('https://www.ezeetechnosys.com/', 'https://www.ezeeoptimus.com/')
    .replace('[Accessed: Jul. 2025]', '[Accessed: Sep. 2026]')
    .replace('2024.', '2026.'), 1)
assert 'ezeeoptimus.com' in body
print('  reference [14] repointed at the live product page')

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
for media, path in newmedia.items():
    zo.writestr('word/' + media, open(path, 'rb').read())
zo.close(); zin.close()
shutil.move('_docx/_n.docx', SRC); os.remove('_docx/_t.docx')
print('\ndocument.xml well-formed; written')
