"""Place the in-text IEEE citations.

The reference list carries [1]-[15] but no [n] ever appeared in the text - true of
the submitted original too. Each entry is cited here at the point where the report
actually leans on it, not scattered to make the list look used. Two sources earn a
second placement because the report draws on them in two distinct places:
Whitten & Bentley for both requirements derivation and DFD levelling, Sommerville
for both the non-functional quality attributes and the testing dimensions.

Vite and Lombok are the only two with no prose discussion anywhere - they appear
only as rows of Table 4.1, so that is where they are cited.

Insertion is offset-based: Word splits a sentence across many runs, so the anchor
is matched against the paragraph's concatenated text and the offset is then mapped
back to the individual <w:t> that contains it.
"""
import zipfile, re, shutil, os
import xml.dom.minidom as M

SRC = '_docx/out.docx'

# (paragraph, anchor text the citation follows, marker)
CITES = [
    (258, 'restaurant management software',                        '[6]'),
    (312, 'boutique accommodation providers',                      '[12]'),
    (315, 'conference centres, and resorts',                       '[13]'),
    (318, 'multi-currency settlement',                             '[14]'),
    (325, 'structured observation of manual restaurant workflows', '[1]'),
    (369, 'must exhibit in production deployment',                 '[2]'),
    (398, 'Spring WebSocket SimpleBroker for the MVP',             '[10]'),
    (409, '@Lock(LockModeType.PESSIMISTIC_WRITE)',                 '[4]'),
    (436, 'The Level 0 Context Diagram',                           '[1]'),
    (484, None,                                                    '[15]'),  # Table 4.1 row
    (488, None,                                                    '[11]'),  # Table 4.1 row
    (508, 'for three primary reasons',                             '[3]'),
    (511, 'for its row-level locking capability',                  '[7]'),
    (513, 'React 19 was selected for its mature Hooks API',        '[5]'),
    (513, 'Tailwind CSS was selected over Bootstrap',              '[9]'),
    (544, 'signature verification and expiry check',               '[8]'),
    (554, 'five distinct testing dimensions',                      '[2]'),
]

z = zipfile.ZipFile(SRC)
xml = z.read('word/document.xml').decode('utf8')
head, body, tail = re.match(r'(.*<w:body>)(.*)(</w:body>.*)', xml, re.S).groups()
PARA = r'<w:p(?: [^>]*)?>.*?</w:p>|<w:p/>'
paras = re.findall(PARA, body, re.S)

def concat(p):
    return ''.join(re.findall(r'<w:t[^>]*>([^<]*)</w:t>', p))

def insert(p, offset, text):
    """Insert text at a character offset in the paragraph's concatenated text."""
    pos = 0
    for m in re.finditer(r'(<w:t[^>]*>)([^<]*)(</w:t>)', p):
        seg = m.group(2)
        if pos <= offset <= pos + len(seg):
            k = offset - pos
            open_tag = m.group(1)
            if 'xml:space' not in open_tag:            # keep the leading space
                open_tag = open_tag[:-1] + ' xml:space="preserve">'
            new = open_tag + seg[:k] + text + seg[k:] + m.group(3)
            return p[:m.start()] + new + p[m.end():]
        pos += len(seg)
    return None

# group by paragraph, apply back-to-front so earlier offsets stay valid
by_para = {}
for pi, anchor, mark in CITES:
    by_para.setdefault(pi, []).append((anchor, mark))

done, failed = [], []
for pi, items in sorted(by_para.items()):
    p = paras[pi]
    txt = concat(p)
    plan = []
    for anchor, mark in items:
        if anchor is None:
            plan.append((len(txt), ' ' + mark, mark, '(end of cell)'))
            continue
        k = txt.find(anchor)
        if k < 0:
            failed.append((pi, anchor, mark)); continue
        plan.append((k + len(anchor), ' ' + mark, mark, anchor))
    new = p
    for off, ins, mark, anchor in sorted(plan, reverse=True):
        r = insert(new, off, ins)
        if r is None:
            failed.append((pi, anchor, mark)); continue
        new = r
        done.append((pi, mark, anchor))
    if new != p:
        body = body.replace(p, new, 1)
        paras[pi] = new

print('citations placed: %d' % len(done))
for pi, mark, anchor in sorted(done):
    print('   p%-5d %-5s after %r' % (pi, mark, anchor[:52]))
if failed:
    print('FAILED:', failed)

xml = head + body + tail
M.parseString(xml.encode('utf8'))              # validate before writing anything
shutil.copy(SRC, '_docx/_t.docx')
zin = zipfile.ZipFile('_docx/_t.docx')
zo = zipfile.ZipFile('_docx/_c.docx', 'w', zipfile.ZIP_DEFLATED)
for it in zin.infolist():
    zo.writestr(it, xml.encode('utf8') if it.filename == 'word/document.xml'
                     else zin.read(it.filename))
zo.close(); zin.close()
shutil.move('_docx/_c.docx', SRC); os.remove('_docx/_t.docx')
print('   document.xml well-formed; written to', SRC)
