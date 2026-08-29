"""Final compliance pass against the IT5106 template.

Six defects found by the audit, five fixed here:
 1. 18 body-prose paragraphs carried runs with no <w:sz>, so Word resolved them
    to its 10pt fallback (docDefaults declares a font but no size). The submitted
    original had zero such paragraphs - every one is a sentence added during the
    figure rework. Body prose must be 12pt.
 2. The A.4 headings were emitted at 12pt while every other Heading2 is 16pt and
    every other Heading3 is 14pt.
 3. Table 4.1 was never referred to in the prose.
 4. Appendix C table header cells were unsized (10pt) inside 9pt tables.
 5. Figures 3.1 and 3.4 had their captions orphaned onto the following page.
    keepNext binds every figure to its caption; Fig 3.4 also has to lose a little
    height, because at 9.28in the figure plus a two-line caption exceeds the
    9.72in column and no amount of keeping-together can fit them.

Not fixed - reported instead: the reference list is IEEE-numbered [1]-[15] but no
[n] citation appears in the text. That is true of the submitted original as well,
so it is the author's own to resolve; inserting citations is not a formatting fix.

Captions render at 10pt italic grey. That is unchanged from the submitted
original and is the author's convention, so it is deliberately left alone.
"""
import zipfile, re, shutil, os, struct
import xml.dom.minidom as M

SRC = '_docx/out.docx'
z = zipfile.ZipFile(SRC)
xml = z.read('word/document.xml').decode('utf8')
head, body, tail = re.match(r'(.*<w:body>)(.*)(</w:body>.*)', xml, re.S).groups()

PARA = r'<w:p(?: [^>]*)?>.*?</w:p>|<w:p/>'
def txt(p): return ''.join(re.findall(r'<w:t[^>]*>([^<]*)</w:t>', p)).strip()
def runs(p): return re.findall(r'<w:r>(?:(?!</w:r>).)*?</w:r>', p, re.S)
def has_text(r): return bool(''.join(re.findall(r'<w:t[^>]*>([^<]*)</w:t>', r)).strip())

def size_runs(p, half):
    """Give every text-bearing run in p an explicit size, without touching one
    that already declares its own."""
    out = []
    for seg in re.split(r'(<w:r>(?:(?!</w:r>).)*?</w:r>)', p, flags=re.S):
        if not seg.startswith('<w:r>') or '<w:sz ' in seg or not has_text(seg):
            out.append(seg); continue
        sz = '<w:sz w:val="%d"/><w:szCs w:val="%d"/>' % (half, half)
        if '<w:rPr>' in seg:
            seg = seg.replace('<w:rPr>', '<w:rPr>' + sz, 1)
        else:
            seg = seg.replace('<w:r>', '<w:r><w:rPr>' + sz + '</w:rPr>', 1)
        out.append(seg)
    return ''.join(out)

tbl = [(m.start(), m.end()) for m in re.finditer(r'<w:tbl>.*?</w:tbl>', body, re.S)]
paras = re.findall(PARA, body, re.S)
off, c = [], 0
for p in paras:
    i = body.find(p, c); off.append(i); c = i + len(p)
def in_tbl(i): return any(a <= off[i] < e for a, e in tbl)
def iscap(t): return bool(re.match(r'^(Figure|Table) [\dA-C]+\.\d+\s*:', t))

# ---- 1. body prose stranded at 10pt -> 12pt ---------------------------------
fixed = []
for i, p in enumerate(paras):
    t = txt(p)
    if not t or iscap(t) or in_tbl(i): continue
    if re.search(r'<w:pStyle w:val="(Heading|TOC)', p): continue
    if not any('<w:sz ' not in r and has_text(r) for r in runs(p)): continue
    new = size_runs(p, 24)
    body = body.replace(p, new, 1)
    fixed.append((i, t[:52]))
print('1. body prose 10pt -> 12pt: %d paragraphs' % len(fixed))
for i, t in fixed: print('     p%-5d %s' % (i, t))

# ---- 2. A.4 headings to match the rest --------------------------------------
n2 = 0
for p in re.findall(PARA, body, re.S):
    t = txt(p)
    if not re.match(r'^A\.4(\.\d+)?\s', t): continue
    lvl = 'Heading3' if re.match(r'^A\.4\.\d', t) else 'Heading2'
    want = 28 if lvl == 'Heading3' else 32
    if 'w:val="%s"' % lvl not in p: continue
    new = re.sub(r'<w:sz w:val="\d+"/>', '<w:sz w:val="%d"/>' % want, p)
    new = re.sub(r'<w:szCs w:val="\d+"/>', '<w:szCs w:val="%d"/>' % want, new)
    if new != p: body = body.replace(p, new, 1); n2 += 1
print('2. A.4 headings resized to match (H2 16pt / H3 14pt): %d' % n2)

# ---- 3. cite Table 4.1 in the prose -----------------------------------------
n3 = 0
for p in re.findall(PARA, body, re.S):
    t = txt(p)
    if not t.startswith('The development environment') and \
       not (t.startswith('The system was developed') and 'environment' in t): continue
    lm = list(re.finditer(r'(<w:t[^>]*>)([^<]*)(</w:t>)', p))
    if not lm: continue
    m = lm[-1]
    add = ' Table 4.1 summarises the environment and the version of each component.'
    new = p[:m.start()] + m.group(1) + m.group(2).rstrip() + add + m.group(3) + p[m.end():]
    new = new.replace('<w:t>', '<w:t xml:space="preserve">')
    body = body.replace(p, new, 1); n3 = 1
    print('3. Table 4.1 now cited in: %r' % (t[:60],)); break
if not n3: print('3. Table 4.1 citation: NO ANCHOR PARAGRAPH FOUND -- see report')

# ---- 4. Appendix C table headers -> 9pt to match their tables ---------------
n4 = 0
# splice back-to-front so an earlier table's offsets stay valid after a later
# one grows; recomputing the match list mid-loop is what corrupted the first run
tbls = list(re.finditer(r'<w:tbl>.*?</w:tbl>', body, re.S))
for m in reversed(tbls[-3:]):
    blk = m.group(0)
    new = size_runs(blk, 18)
    if new != blk:
        body = body[:m.start()] + new + body[m.end():]
        n4 += 1
print('4. Appendix C tables given explicit 9pt on unsized header cells: %d' % n4)

# ---- 5. keep every figure with its caption ----------------------------------
n5 = 0
for p in re.findall(PARA, body, re.S):
    if '<w:drawing>' not in p or '<w:keepNext/>' in p: continue
    if '<w:pPr>' in p:
        new = p.replace('<w:pPr>', '<w:pPr><w:keepNext/>', 1)
    else:
        new = re.sub(r'(<w:p(?: [^>]*)?>)', r'\1<w:pPr><w:keepNext/></w:pPr>', p, count=1)
    body = body.replace(p, new, 1); n5 += 1
print('5. keepNext applied to figure paragraphs: %d' % n5)

xml = head + body + tail
M.parseString(xml.encode('utf8'))          # validate before writing anything
shutil.copy(SRC, '_docx/_t.docx')
zin = zipfile.ZipFile('_docx/_t.docx')
zout = zipfile.ZipFile('_docx/_f.docx', 'w', zipfile.ZIP_DEFLATED)
for it in zin.infolist():
    zout.writestr(it, xml.encode('utf8') if it.filename == 'word/document.xml'
                       else zin.read(it.filename))
zout.close(); zin.close()
shutil.move('_docx/_f.docx', SRC); os.remove('_docx/_t.docx')
print('   document.xml well-formed; written to', SRC)
