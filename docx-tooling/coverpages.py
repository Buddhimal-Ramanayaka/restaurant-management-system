"""Rebuild the first cover page and the title page to the IT5106 template.

Source of truth is "IT5106 - Final Project Report Template.pdf", not the .docx of
the same name: the .docx is a lossy PDF->Word conversion. It carries
w:jc="left" on every line although the PDF is plainly centred, it merged the
candidate name and index number into one paragraph although the PDF has them on
separate lines, and it records the candidate name as 16pt although the PDF glyph
metrics put it at 20pt (the name spans 330.7pt for 36 characters, which is ~9.2pt
per character; 16pt Calibri would give ~7.5pt). Everything below is measured off
the PDF with pdftotext -bbox.

Template spec, both pages centred:

  Cover page          y=110  title, 20pt bold
                      y=347  candidate name, 20pt
                      y=599  submission month and year, 16pt bold

  Title page          y=210  title, 20pt bold
                      y=309  candidate name, 20pt
                      y=340  index number, 16pt
                      y=395  "Name(s) of the supervisor(s):", 16pt
                      y=497  submission month and year, 16pt bold
                      y=691  three-line submission statement, 12pt bold

y is points from the top of the page. Positions are hit with w:spacing w:before,
computed from the 25mm top margin, then verified against the rendered PDF.

The document keeps Times New Roman rather than the template's Calibri, because
the rest of the dissertation is Times New Roman and a Calibri cover on a Times
New Roman body would read as an error. Sizes, weights, centring, ordering and the
two-page split all follow the template.
"""
import zipfile, re, shutil, os
import xml.dom.minidom as M

SRC = '_docx/out.docx'
TOP = 1417                      # 25mm top margin, twips
PT = 20                         # twips per point

TITLE    = 'RESTAURANT MANAGEMENT SYSTEM'
SUBTITLE = 'A Centralized Web-Based Solution for SME Restaurant Operations'
NAME     = 'H. R. B. Prasanga'
INDEX    = '2019035'
SUPLABEL = 'Name(s) of the supervisor(s):'
SUPNAME  = 'I. V. Gimahana Mithuranga'
WHEN     = 'July 2026'
STATE    = ['This dissertation is submitted in partial fulfilment of the requirement of the',
            'Degree of Bachelor of Information Technology (External) of the',
            'University of Colombo School of Computing']

def esc(s):
    return s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')

def para(lines, pt, bold=False, before=0, after=0):
    """One centred paragraph; `lines` joined with explicit breaks."""
    half = pt * 2
    rpr = '<w:rPr>%s<w:sz w:val="%d"/><w:szCs w:val="%d"/></w:rPr>' % (
        '<w:b/>' if bold else '', half, half)
    if isinstance(lines, str):
        lines = [lines]
    runs = []
    for i, ln in enumerate(lines):
        if i:
            runs.append('<w:r>%s<w:br/></w:r>' % rpr)
        runs.append('<w:r>%s<w:t xml:space="preserve">%s</w:t></w:r>' % (rpr, esc(ln)))
    return ('<w:p><w:pPr><w:spacing w:before="%d" w:after="%d" w:line="240" '
            'w:lineRule="auto"/><w:jc w:val="center"/></w:pPr>%s</w:p>'
            % (before, after, ''.join(runs)))

PAGEBREAK = '<w:p><w:pPr><w:spacing w:before="0" w:after="0"/></w:pPr>' \
            '<w:r><w:br w:type="page"/></w:r></w:p>'

def spacer(tw):
    """Empty paragraph of an exact height. Word discards w:spacing w:before at the
    top of a page, which collapsed the whole title page on the first attempt, so
    the leading gap on each page is a spacer with w:lineRule="exact" instead."""
    return ('<w:p><w:pPr><w:spacing w:before="0" w:after="0" w:line="%d" '
            'w:lineRule="exact"/></w:pPr></w:p>' % tw)

# The cover and title pages carry no page number, so they get their own section
# with no footerReference. A section that declares none and has no preceding
# section has no footer at all; the section after it keeps its own footer and
# restarts the roman numbering at i on the Declaration.
COVERSECT = ('<w:p><w:pPr><w:sectPr><w:pgSz w:w="11906" w:h="16838"/>'
             '<w:pgMar w:top="1417" w:right="1417" w:bottom="1417" w:left="2098" '
             'w:header="708" w:footer="708" w:gutter="0"/>'
             '<w:cols w:space="720"/><w:docGrid w:linePitch="360"/>'
             '</w:sectPr></w:pPr></w:p>')

# --- spacing, derived from the target y positions -----------------------------
# a line of n-pt text at single spacing occupies about 1.15n
def h(pt, n=1): return int(round(pt * 1.15 * n * PT))

cur = TOP
def gap(target_pt):
    """space-before needed to put the next block's top at target_pt from page top"""
    global cur
    g = int(round(target_pt * PT)) - cur
    return max(g, 0)

blocks = []
# Heights are deterministic: a line at single spacing is 1.15 x its point size, and
# the subtitle wraps to two lines in the 5.80in column. Positions below are the
# template's y values in points, converted at 20 twips to the point.
# ---------------- cover page ----------------
blocks.append(spacer(783))                                    # margin 70.9pt -> y 110
blocks.append(para(TITLE, 20, True))                          # y 110, h 23 -> 133
blocks.append(para(SUBTITLE, 16, False, before=120))           # y 139, 2 lines -> 175.8
blocks.append(para(NAME, 20, False, before=3424))              # y 347, h 23 -> 370
blocks.append(para(WHEN, 16, True, before=4580))               # y 599
blocks.append(PAGEBREAK)

# ---------------- title page ----------------
blocks.append(spacer(2783))                                   # margin 70.9pt -> y 210
blocks.append(para(TITLE, 20, True))                          # y 210, h 23 -> 233
blocks.append(para(SUBTITLE, 16, False, before=120))           # y 239, 2 lines -> 275.8
blocks.append(para(NAME, 20, False, before=664))               # y 309, h 23 -> 332
blocks.append(para(INDEX, 16, False, before=160))              # y 340 -> 358.4
blocks.append(para(SUPLABEL, 16, False, before=732))           # y 395 -> 413.4
blocks.append(para(SUPNAME, 16, False, before=60))             # y 416.4 -> 434.8
blocks.append(para(WHEN, 16, True, before=1244))               # y 497 -> 515.4
blocks.append(para(STATE, 12, True, before=3512))              # y 691, 3 lines
blocks.append(COVERSECT)

NEWFRONT = ''.join(blocks)

# --- splice in, replacing everything before the Declaration -------------------
z = zipfile.ZipFile(SRC)
xml = z.read('word/document.xml').decode('utf8')
head, body, tail = re.match(r'(.*<w:body>)(.*)(</w:body>.*)', xml, re.S).groups()
paras = re.findall(r'<w:p(?: [^>]*)?>.*?</w:p>|<w:p/>', body, re.S)

def txt(p): return ''.join(re.findall(r'<w:t[^>]*>([^<]*)</w:t>', p)).strip()

dec = [i for i, p in enumerate(paras) if txt(p) == 'DECLARATION']
assert len(dec) >= 1, 'DECLARATION heading not found'
d = dec[0]
# the empty Heading1 immediately before DECLARATION belongs to that block, keep it
start_keep = d - 1 if (d and not txt(paras[d - 1]) and 'Heading1' in paras[d - 1]) else d
old = ''.join(paras[:start_keep])
print('replacing %d paragraphs of front matter (up to but not including para %d)'
      % (start_keep, start_keep))
for i, p in enumerate(paras[:start_keep]):
    t = txt(p)
    if t or '<w:drawing>' in p:
        print('   dropped p%-2d %s%r' % (i, '[IMAGE] ' if '<w:drawing>' in p else '', t[:56]))

assert body.startswith(old)
body = NEWFRONT + body[len(old):]

xml = head + body + tail
M.parseString(xml.encode('utf8'))          # validate before writing anything
shutil.copy(SRC, '_docx/_t.docx')
zin = zipfile.ZipFile('_docx/_t.docx')
zo = zipfile.ZipFile('_docx/_c.docx', 'w', zipfile.ZIP_DEFLATED)
for it in zin.infolist():
    zo.writestr(it, xml.encode('utf8') if it.filename == 'word/document.xml'
                     else zin.read(it.filename))
zo.close(); zin.close()
shutil.move('_docx/_c.docx', SRC); os.remove('_docx/_t.docx')
print('\ncover and title pages written to', SRC)
