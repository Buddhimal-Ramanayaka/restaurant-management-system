"""Make the TOC, List of Figures and List of Tables clickable, and nothing else.

The front matter was flattened to plain dot-leader text at some point, so none of
the 98 entries is a link. The document does still carry 88 stale _Toc* bookmarks
from whenever that TOC was live, but several have drifted onto empty paragraphs
(_Toc234865788 sits on a blank line, not on "1.3 Aims and Objectives"), so they
are not trustworthy targets. This resolves every entry to its real target
paragraph by text, drops a fresh bookmark there, and wraps the entry's runs in a
w:hyperlink pointing at it. The stale bookmarks are left untouched.

Scope guard: the only body-side edit is inserting bookmarkStart/bookmarkEnd
markers, which carry no text and no formatting. Run with --check to resolve and
report without writing anything.

The dot leader and the page number go inside the hyperlink, the way Word's own
TOC field builds it, so the whole line is clickable rather than just the label.
No rStyle is applied, so entries keep their current appearance instead of turning
blue and underlined.
"""
import zipfile, re, sys, shutil, os
import xml.dom.minidom as M

SRC = '_docx/out.docx'
CHECK = '--check' in sys.argv
BM_ID = 5000

z = zipfile.ZipFile(SRC)
xml = z.read('word/document.xml').decode('utf8')
head, body, tail = re.match(r'(.*<w:body>)(.*)(</w:body>.*)', xml, re.S).groups()
PARA = r'<w:p(?: [^>]*)?>.*?</w:p>|<w:p/>'
paras = re.findall(PARA, body, re.S)

def txt(p): return ''.join(re.findall(r'<w:t[^>]*>([^<]*)</w:t>', p)).strip()
def sty(p):
    m = re.search(r'<w:pStyle w:val="([^"]+)"', p)
    return m.group(1) if m else ''
def norm(s):
    return re.sub(r'\s+', ' ', s.replace('’', "'").replace('–', '-')
                                .replace('—', '-')).strip().lower()

entries = [i for i, p in enumerate(paras) if 'w:leader="dot"' in p]
first_entry = entries[0]

def entry_label(i):
    ts = re.findall(r'<w:t[^>]*>([^<]*)</w:t>', paras[i])
    return ''.join(ts[:-1]).strip()

# ---- target resolution -------------------------------------------------------
# Everything after the last dot-leader entry is body; front-matter headings sit
# before it. A caption target must be the body caption, never the list entry.
def find_heading(pred, after=0):
    for i in range(after, len(paras)):
        if sty(paras[i]).startswith('Heading') and pred(txt(paras[i])):
            return i
    return None

def find_caption(num, kind):
    pat = re.compile(r'^%s\s+%s\s*:' % (kind, re.escape(num)))
    for i in range(first_entry + 1, len(paras)):
        if i in entries:
            continue
        if pat.match(txt(paras[i])):
            return i
    return None

FRONT = {
    'declaration':      r'^declaration$',
    'abstract':         r'^abstract$',
    'acknowledgements': r'^acknowledgements$',
    'table of contents': r'^table of contents$',
    'list of figures':  r'^list of figures$',
    'list of tables':   r'^list of tables$',
    'list of acronyms': r'^list of (acronyms|abbreviations)',
}

def resolve(i):
    lbl = entry_label(i)
    n = norm(lbl)
    if n in FRONT:
        pat = re.compile(FRONT[n], re.I)
        for j, p in enumerate(paras):
            if j in entries:
                continue
            if pat.match(norm(txt(p))) and txt(p):
                return j, 'front_' + re.sub(r'\W+', '_', n)
        return None, None
    m = re.match(r'^(\d+(?:\.\d+)*)\s', lbl)
    if m:
        sec = m.group(1)
        j = find_heading(lambda t, s=sec: re.match(r'^' + re.escape(s) + r'(?!\d)', t),
                         first_entry + 1)
        return j, 'sec_' + sec.replace('.', '_')
    m = re.match(r'^(Figure|Table)\s+([\dA-C]+\.\d+)\s*:', lbl)
    if m:
        kind, num = m.group(1), m.group(2)
        j = find_caption(num, kind)
        return j, ('%s_%s' % (kind[:3].lower(), num.replace('.', '_')))
    if n.startswith('references'):
        return find_heading(lambda t: t.upper() == 'REFERENCES', first_entry + 1), 'refs'
    m = re.match(r'^appendix\s+([a-c])', n)
    if m:
        L = m.group(1).upper()
        j = find_heading(lambda t, L=L: t.upper().startswith('APPENDIX ' + L),
                         first_entry + 1)
        return j, 'app_' + L
    return None, None

plan, unresolved = [], []
for i in entries:
    j, key = resolve(i)
    if j is None:
        unresolved.append((i, entry_label(i)))
    else:
        plan.append((i, j, '_Rms_' + key))

print('entries: %d   resolved: %d   unresolved: %d'
      % (len(entries), len(plan), len(unresolved)))
names = [n for _, _, n in plan]
dupe = {n for n in names if names.count(n) > 1}
too_long = [n for n in names if len(n) > 40]
print('duplicate anchor names:', sorted(dupe) or 'none')
print('names over 40 chars   :', too_long or 'none')
if unresolved:
    print('\nUNRESOLVED:')
    for i, l in unresolved:
        print('   p%-5d %r' % (i, l[:70]))
for i, j, nm in plan:
    print('   p%-5d -> p%-5d %-26s %-46s => %s'
          % (i, j, nm, entry_label(i)[:46], txt(paras[j])[:40]))

if unresolved or dupe or too_long:
    print('\nnot writing: resolve the problems above first')
    sys.exit(1)
if CHECK:
    print('\n--check: nothing written')
    sys.exit(0)

# ---- apply -------------------------------------------------------------------
# work back-to-front so earlier paragraph text stays valid as we splice
edits = {}
for k, (i, j, nm) in enumerate(plan):
    edits.setdefault(j, []).append(nm)

new_paras = list(paras)

# 1. bookmarks on targets
bid = BM_ID
for j, nms in edits.items():
    p = new_paras[j]
    starts, ends = '', ''
    for nm in nms:
        starts += '<w:bookmarkStart w:id="%d" w:name="%s"/>' % (bid, nm)
        ends = '<w:bookmarkEnd w:id="%d"/>' % bid + ends
        bid += 1
    m = re.match(r'(<w:p(?: [^>]*)?>)(<w:pPr>.*?</w:pPr>)?', p, re.S)
    at = m.end()
    new_paras[j] = p[:at] + starts + p[at:-len('</w:p>')] + ends + '</w:p>'

# 2. wrap each entry's runs in a hyperlink
for i, j, nm in plan:
    p = new_paras[i]
    m = re.match(r'(<w:p(?: [^>]*)?>)(<w:pPr>.*?</w:pPr>)?', p, re.S)
    openp, ppr = m.group(1), (m.group(2) or '')
    inner = p[m.end():-len('</w:p>')]
    new_paras[i] = (openp + ppr + '<w:hyperlink w:anchor="%s" w:history="1">' % nm
                    + inner + '</w:hyperlink></w:p>')

# splice the rewritten paragraphs back in, back-to-front
out = body
for idx in sorted(set(list(edits.keys()) + [i for i, _, _ in plan]), reverse=True):
    old = paras[idx]
    pos = -1
    start = 0
    for _ in range(idx + 1):
        pos = out.find(old, start) if _ == idx else pos
    # locate by rebuilding: safer to rebuild the whole body from new_paras
    break
rebuilt = []
cursor = 0
for idx, p in enumerate(paras):
    k = body.find(p, cursor)
    assert k >= 0, idx
    rebuilt.append(body[cursor:k])
    rebuilt.append(new_paras[idx])
    cursor = k + len(p)
rebuilt.append(body[cursor:])
out = ''.join(rebuilt)

xml = head + out + tail
M.parseString(xml.encode('utf8'))          # validate before writing anything
shutil.copy(SRC, '_docx/_t.docx')
zin = zipfile.ZipFile('_docx/_t.docx')
zo = zipfile.ZipFile('_docx/_l.docx', 'w', zipfile.ZIP_DEFLATED)
for it in zin.infolist():
    zo.writestr(it, xml.encode('utf8') if it.filename == 'word/document.xml'
                     else zin.read(it.filename))
zo.close(); zin.close()
shutil.move('_docx/_l.docx', SRC); os.remove('_docx/_t.docx')
print('\n%d bookmarks, %d hyperlinks; document.xml well-formed; written to %s'
      % (bid - BM_ID, len(plan), SRC))
