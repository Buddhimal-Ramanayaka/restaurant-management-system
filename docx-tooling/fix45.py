"""Correct 4.5 paragraph two.

It claimed "the Git log serves as a readable development history that a future
maintainer can follow" and quoted two commit messages as examples. Neither commit
exists - git log --grep finds nothing for either - and the real history is one
commit carrying the whole system plus a handful of documentation commits. Worse,
the claim contradicts the preceding paragraph, which already concedes that the
repository layout rather than the commit history is what the section describes.

Replaced with what is actually true and checkable: the four feature branches, by
name, record how the work was partitioned. Verified against git ls-remote.
"""
import zipfile, re, shutil, os
import xml.dom.minidom as M

SRC = '_docx/out.docx'
OLD_MARK = 'Commit messages follow a structured format'
NEW = ('The four feature branches record how the work was partitioned by functional '
       'area: recipe-deduction-engine, kitchen-display-websocket, '
       'billing-and-shift-reconciliation, and procurement-grn-workflow. The branch '
       'names, rather than the granularity of individual commits, are what document '
       'the division of development effort across the project.')

z = zipfile.ZipFile(SRC)
xml = z.read('word/document.xml').decode('utf8')
head, body, tail = re.match(r'(.*<w:body>)(.*)(</w:body>.*)', xml, re.S).groups()
paras = re.findall(r'<w:p(?: [^>]*)?>.*?</w:p>|<w:p/>', body, re.S)

def txt(p): return ''.join(re.findall(r'<w:t[^>]*>([^<]*)</w:t>', p)).strip()

hits = [i for i, p in enumerate(paras) if txt(p).startswith(OLD_MARK)]
assert len(hits) == 1, hits
i = hits[0]
old = paras[i]
print('replacing p%d:\n  %s\n' % (i, txt(old)[:150] + '...'))

# keep the paragraph's own pPr and the first run's rPr; drop every other run so
# the replacement inherits the surrounding body formatting exactly
runs = re.findall(r'<w:r>(?:(?!</w:r>).)*?</w:r>', old, re.S)
assert runs
first = runs[0]
rpr = re.search(r'<w:rPr>.*?</w:rPr>', first, re.S)
rpr = rpr.group(0) if rpr else '<w:rPr><w:sz w:val="24"/><w:szCs w:val="24"/></w:rPr>'
if '<w:sz ' not in rpr:                    # body prose must be 12pt
    rpr = rpr.replace('</w:rPr>', '<w:sz w:val="24"/><w:szCs w:val="24"/></w:rPr>')
newrun = '<w:r>%s<w:t xml:space="preserve">%s</w:t></w:r>' % (rpr, NEW)

ppr = re.search(r'<w:pPr>.*?</w:pPr>', old, re.S)
start = re.match(r'<w:p(?: [^>]*)?>', old).group(0)
new = start + (ppr.group(0) if ppr else '') + newrun + '</w:p>'

body = body.replace(old, new, 1)
print('with:\n  %s' % NEW)

xml = head + body + tail
M.parseString(xml.encode('utf8'))          # validate before writing anything
shutil.copy(SRC, '_docx/_t.docx')
zin = zipfile.ZipFile('_docx/_t.docx')
zo = zipfile.ZipFile('_docx/_f.docx', 'w', zipfile.ZIP_DEFLATED)
for it in zin.infolist():
    zo.writestr(it, xml.encode('utf8') if it.filename == 'word/document.xml'
                     else zin.read(it.filename))
zo.close(); zin.close()
shutil.move('_docx/_f.docx', SRC); os.remove('_docx/_t.docx')
print('\ndocument.xml well-formed; written to', SRC)
