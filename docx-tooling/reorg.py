"""Two relocations the examiner asked for.

Item 11 - "reports should be under Evaluation": Appendix C (three sample
management reports) becomes section 5.7 of Chapter 5. C.1-C.3 drop a level to
5.7.1-5.7.3 and Tables C.1-C.3 renumber to 5.5-5.7, continuing Chapter 5's
existing run of 5.1-5.4.

Item 8 - "include use case diagram": the diagram was present but sat at the end
of 2.1 Review of Existing Systems, under the competitor reviews, which is not
where a reader looks for a model of the proposed system. It moves to the head of
2.2 Functional Requirements, after that section's opening paragraph. Nothing
renumbers - it stays Figure 2.2 because it stays after Figure 2.1.

Page numbers in the new front-matter entries are placeholders; pages.py
re-measures every one of them at the end.
"""
import zipfile, re, shutil, os
import xml.dom.minidom as M

SRC = '_docx/out.docx'
z = zipfile.ZipFile(SRC)
xml = z.read('word/document.xml').decode('utf8')
z.close()
head, body, tail = re.match(r'(.*<w:body>)(.*)(</w:body>.*)', xml, re.S).groups()
paras = re.findall(r'<w:p(?: [^>]*)?>.*?</w:p>|<w:p/>', body, re.S)
def txt(p): return ''.join(re.findall(r'<w:t[^>]*>([^<]*)</w:t>', p)).strip()

def find(pred, what):
    hits = [i for i, p in enumerate(paras) if pred(p)]
    assert len(hits) == 1, '%s -> %r' % (what, hits)
    return hits[0]

H2 = ('<w:pPr><w:pStyle w:val="Heading2"/><w:spacing w:before="300" w:after="160" '
      'w:line="360" w:lineRule="auto"/></w:pPr>')
H2R = '<w:rPr><w:b/><w:bCs/><w:sz w:val="32"/><w:szCs w:val="32"/></w:rPr>'
H3 = ('<w:pPr><w:pStyle w:val="Heading3"/><w:spacing w:before="200" w:after="100" '
      'w:line="360" w:lineRule="auto"/></w:pPr>')
H3R = '<w:rPr><w:b/><w:bCs/><w:sz w:val="28"/><w:szCs w:val="28"/></w:rPr>'
BD = ('<w:pPr><w:spacing w:before="100" w:after="100" w:line="360" '
      'w:lineRule="auto"/><w:jc w:val="both"/></w:pPr>')
BDR = '<w:rPr><w:sz w:val="24"/><w:szCs w:val="24"/></w:rPr>'

bm = [7300]
def heading(text, anchor, ppr, rpr):
    i = bm[0]; bm[0] += 1
    return ('<w:p>%s<w:bookmarkStart w:id="%d" w:name="%s"/><w:r>%s'
            '<w:t xml:space="preserve">%s</w:t></w:r><w:bookmarkEnd w:id="%d"/></w:p>'
            % (ppr, i, anchor, rpr, text, i))

def toc(text, anchor, page, indent=True):
    return ('<w:p><w:pPr><w:spacing w:before="60" w:after="60" w:line="320" '
            'w:lineRule="auto"/>%s</w:pPr><w:hyperlink w:anchor="%s" w:history="1">'
            '<w:r><w:rPr><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr>'
            '<w:t xml:space="preserve">%s</w:t></w:r><w:r><w:ptab w:relativeTo="margin" '
            'w:alignment="right" w:leader="dot"/></w:r><w:r><w:rPr><w:sz w:val="22"/>'
            '<w:szCs w:val="22"/></w:rPr><w:t>%d</w:t></w:r></w:hyperlink></w:p>'
            % ('<w:ind w:left="360"/>' if indent else '', anchor, text, page))

# ---------------------------------------------------------------- item 11 ----
app_c = find(lambda p: 'Heading1' in p and txt(p).startswith('APPENDIX C'), 'Appendix C')
last  = find(lambda p: 'RPT-IVR-20250713-002' in txt(p) and 'End of report' in txt(p),
             'last report footer')

s0 = body.index(paras[app_c])
s1 = body.index(paras[last], s0) + len(paras[last])
slab = body[s0:s1]

slab = slab.replace(paras[app_c], '', 1)          # the APPENDIX C banner goes

SUBS = [('C.1', '5.7.1', 'Daily Sales Report',        'Table C.1', 'Table 5.5'),
        ('C.2', '5.7.2', 'Shift Cash Reconciliation', 'Table C.2', 'Table 5.6'),
        ('C.3', '5.7.3', 'Inventory Variance Report', 'Table C.3', 'Table 5.7')]
for old, new, title, oldt, newt in SUBS:
    h = find(lambda p, o=old: 'Heading2' in p and txt(p).startswith(o + '  '), old)
    assert paras[h] in slab
    slab = slab.replace(paras[h],
                        heading('%s  %s' % (new, title), '_Rms_sec_%s' % new.replace('.', '_'),
                                H3, H3R), 1)
    n = slab.count(oldt)
    assert n == 2, '%s appears %d times' % (oldt, n)   # caption + one prose reference
    slab = slab.replace(oldt, newt)

INTRO = ("Beyond the functional and performance testing reported above, the system was "
         "evaluated on whether its reporting output is usable by the restaurant's "
         "management. Three reports were generated from a representative service day and "
         "reviewed with the owner: the daily sales report, the shift cash reconciliation "
         "and the inventory variance report. These are reproduced as Tables 5.5 to 5.7. "
         "Each addresses one of the management problems identified in Section 1.2 - "
         "unknown profitability, unaccountable cash variance and undetected stock loss - "
         "and together they demonstrate that the data captured during service is "
         "sufficient to answer those questions without any manual tallying.")

slab = (heading('5.7  Sample Management Reports', '_Rms_sec_5_7', H2, H2R)
        + '<w:p>%s<w:r>%s<w:t xml:space="preserve">%s</w:t></w:r></w:p>' % (BD, BDR, INTRO)
        + slab)

body = body[:s0] + body[s1:]
ch6 = find(lambda p: 'Heading1' in p and txt(p).startswith('CHAPTER 6'), 'Chapter 6')
at = body.index(paras[ch6])
body = body[:at] + slab + body[at:]
print('  Appendix C -> 5.7 Sample Management Reports (Tables C.1-C.3 -> 5.5-5.7)')

# ----------------------------------------------------------------- item 8 ----
uc_cap = find(lambda p: txt(p).startswith('Figure 2.2:') and 'w:leader="dot"' not in p,
              'use case caption')
uc_draw = uc_cap - 1
assert '<w:drawing>' in paras[uc_draw]
blank = uc_draw - 1
assert txt(paras[blank]) == ''
after = uc_cap + 1
assert txt(paras[after]).startswith('Figure 2.2 shows')

b0 = body.index(paras[blank], body.index(paras[uc_draw]) - 4000)
b1 = body.index(paras[after], b0) + len(paras[after])
uc = body[b0:b1]
body = body[:b0] + body[b1:]

fr = find(lambda p: 'Heading2' in p and txt(p).startswith('2.2  Functional Requirements'),
          '2.2 heading')
opening = paras[fr + 1]
at = body.index(opening) + len(opening)
body = body[:at] + uc + body[at:]
print('  use case diagram: end of 2.1 -> head of 2.2 Functional Requirements')

# ------------------------------------------------------------- front matter --
old_toc = find(lambda p: 'w:leader="dot"' in p and txt(p).startswith('Appendix C:'),
               'Appendix C TOC entry')
body = body.replace(paras[old_toc], '', 1)

anchor = find(lambda p: 'w:leader="dot"' in p and txt(p).startswith('5.6.4'), '5.6.4 TOC')
new_toc = (toc('5.7  Sample Management Reports', '_Rms_sec_5_7', 48)
           + ''.join(toc('      %s  %s' % (new, title), '_Rms_sec_%s' % new.replace('.', '_'), 48)
                     for _, new, title, _, _ in SUBS))
at = body.index(paras[anchor]) + len(paras[anchor])
body = body[:at] + new_toc + body[at:]

lot = []
for i, p in enumerate(paras):
    if 'w:leader="dot"' in p and re.match(r'^Table C\.[123]:', txt(p)):
        lot.append(p)
assert len(lot) == 3
for p in lot:
    body = body.replace(p, '', 1)
t61 = find(lambda p: 'w:leader="dot"' in p and txt(p).startswith('Table 6.1:'), 'Table 6.1 LoT')
moved = ''.join(p.replace('Table C.%d' % (n + 1), 'Table 5.%d' % (n + 5), 1)
                for n, p in enumerate(lot))
at = body.index(paras[t61])
body = body[:at] + moved + body[at:]
print('  TOC: -1 appendix entry, +4 section entries; List of Tables: C.1-C.3 -> 5.5-5.7')

xml = head + body + tail
M.parseString(xml.encode('utf8'))

shutil.copy(SRC, '_docx/_t.docx')
zin = zipfile.ZipFile('_docx/_t.docx')
zo = zipfile.ZipFile('_docx/_n.docx', 'w', zipfile.ZIP_DEFLATED)
for it in zin.infolist():
    zo.writestr(it, xml.encode('utf8') if it.filename == 'word/document.xml' else zin.read(it.filename))
zo.close(); zin.close()
shutil.move('_docx/_n.docx', SRC); os.remove('_docx/_t.docx')
print('\ndocument.xml well-formed; written')
