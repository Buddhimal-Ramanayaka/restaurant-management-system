"""Insert Appendix A.4 (Database Schema and Data Dictionary) into out.docx.

The tables are generated from schema.sql rather than hand-written, so the appendix
cannot drift from the database. Figure 3.2 carries keys and defining columns only;
this carries every column.

Inserted immediately before APPENDIX B. The TOC lists only "Appendix A - System
Manual" with no A.n subsections, so no TOC entry is needed.
"""
import zipfile, re, shutil, os, io
import xml.dom.minidom as MD

SRC = '_docx/out.docx'
SQL = 'backend/src/main/resources/schema.sql'

esc = lambda s: (s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;'))

# ---------------------------------------------------------------- parse schema
sql = io.open(SQL, encoding='utf8').read()
tables = []
for m in re.finditer(r'CREATE TABLE IF NOT EXISTS (\w+) \((.*?)\n\) ENGINE', sql, re.S):
    name, body = m.group(1), m.group(2)
    cols, fks, uqs, pk, pending = [], {}, [], None, None
    for raw in body.splitlines():
        t = raw.strip().rstrip(',')
        if not t or t.startswith('--'):
            continue
        if pending:
            r = re.match(r'REFERENCES (\w+)\((\w+)\)(?: ON DELETE (.+))?', t)
            if r:
                fks[pending] = (r.group(1), (r.group(3) or 'NO ACTION').strip())
            pending = None
            continue
        if t.upper().startswith('CONSTRAINT'):
            f = re.search(r'FOREIGN KEY \((\w+)\)', t)
            u = re.search(r'UNIQUE \(([^)]+)\)', t)
            if f:
                r = re.search(r'REFERENCES (\w+)\((\w+)\)(?: ON DELETE (.+))?', t)
                if r:
                    fks[f.group(1)] = (r.group(1), (r.group(3) or 'NO ACTION').strip())
                else:
                    pending = f.group(1)
            elif u:
                uqs.append([c.strip() for c in u.group(1).split(',')])
            continue
        c = re.match(r'(\w+)\s+(.+)', t)
        if not c:
            continue
        col, rest = c.group(1), c.group(2)
        if 'PRIMARY KEY' in rest.upper():
            pk = col
        typ = re.match(r"((?:ENUM\([^)]*\))|(?:\w+(?:\([\d,]+\))?))", rest).group(1)
        nn = 'NOT NULL' in rest.upper() or 'PRIMARY KEY' in rest.upper()
        d = re.search(r"DEFAULT ([^,]+?)(?:\s+(?:NOT NULL|UNIQUE)|$)", rest, re.I)
        if 'UNIQUE' in rest.upper() and 'PRIMARY KEY' not in rest.upper():
            uqs.append([col])
        cols.append({'name': col, 'type': typ, 'nn': nn,
                     'default': d.group(1).strip() if d else ''})
    tables.append({'name': name, 'cols': cols, 'fks': fks, 'uqs': uqs, 'pk': pk})

# ---------------------------------------------------------------- docx builders
WID = [1900, 2450, 620, 700, 930, 1791]          # twips, sums to 8391 = text column
SZ = 22                                          # 11pt, per the IT5106 table rule

def cell(text, w, bold=False, shade=None):
    sh = '<w:shd w:val="clear" w:color="auto" w:fill="%s"/>' % shade if shade else ''
    b = '<w:b/><w:bCs/>' if bold else ''
    col = '<w:color w:val="FFFFFF"/>' if shade else ''
    return ('<w:tc><w:tcPr><w:tcW w:w="%d" w:type="dxa"/>%s</w:tcPr>'
            '<w:p><w:pPr><w:spacing w:before="10" w:after="10" w:line="240" w:lineRule="auto"/></w:pPr>'
            '<w:r><w:rPr>%s%s<w:sz w:val="%d"/><w:szCs w:val="%d"/></w:rPr>'
            '<w:t xml:space="preserve">%s</w:t></w:r></w:p></w:tc>'
            % (w, sh, b, col, SZ, SZ, esc(text)))

def row(cells, header=False):
    trPr = '<w:trPr><w:tblHeader/></w:trPr>' if header else ''
    return '<w:tr>%s%s</w:tr>' % (trPr, ''.join(cells))

def table(t):
    single = ''.join('<w:%s w:val="single" w:sz="4" w:space="0" w:color="auto"/>' % s
                     for s in ('top', 'left', 'bottom', 'right', 'insideH', 'insideV'))
    out = ['<w:tbl><w:tblPr><w:tblW w:w="8391" w:type="dxa"/><w:tblBorders>%s</w:tblBorders>'
           '<w:tblLayout w:type="fixed"/>'
           '<w:tblLook w:val="04A0" w:firstRow="1" w:lastRow="0" w:firstColumn="1"'
           ' w:lastColumn="0" w:noHBand="0" w:noVBand="1"/></w:tblPr>' % single]
    out.append('<w:tblGrid>%s</w:tblGrid>'
               % ''.join('<w:gridCol w:w="%d"/>' % w for w in WID))
    hdr = ['Column', 'Type', 'Null', 'Key', 'Default', 'References']
    out.append(row([cell(h, WID[i], bold=True, shade='1565C0') for i, h in enumerate(hdr)],
                   header=True))
    single_uq = {u[0] for u in t['uqs'] if len(u) == 1}
    for c in t['cols']:
        key = []
        if c['name'] == t['pk']: key.append('PK')
        if c['name'] in t['fks']: key.append('FK')
        if c['name'] in single_uq: key.append('UQ')
        ref = ''
        if c['name'] in t['fks']:
            tgt, act = t['fks'][c['name']]
            ref = '%s(id) ON DELETE %s' % (tgt, act)
        out.append(row([cell(c['name'], WID[0]), cell(c['type'], WID[1]),
                        cell('no' if c['nn'] else 'yes', WID[2]), cell(' '.join(key), WID[3]),
                        cell(c['default'], WID[4]), cell(ref, WID[5])]))
    for u in [u for u in t['uqs'] if len(u) > 1]:
        out.append(row([cell('', WID[0]), cell('', WID[1]), cell('', WID[2]),
                        cell('UQ', WID[3]), cell('', WID[4]),
                        cell('composite unique (%s)' % ', '.join(u), WID[5])]))
    out.append('</w:tbl>')
    return ''.join(out)

def para(text, style=None, italic=False):
    st = '<w:pStyle w:val="%s"/>' % style if style else ''
    it = '<w:i/><w:iCs/>' if italic else ''
    return ('<w:p><w:pPr>%s<w:spacing w:before="80" w:after="80" w:line="240" w:lineRule="auto"/>'
            '%s</w:pPr><w:r><w:rPr>%s<w:sz w:val="24"/><w:szCs w:val="24"/></w:rPr>'
            '<w:t xml:space="preserve">%s</w:t></w:r></w:p>'
            % (st, '' if style else '<w:jc w:val="both"/>', it, esc(text)))

ncols = sum(len(t['cols']) for t in tables)
nfks = sum(len(t['fks']) for t in tables)

blocks = [para('A.4  Database Schema and Data Dictionary', style='Heading2'),
          para('Figure 3.2 shows each entity with its primary key, its foreign keys and the '
               'columns that carry its business meaning. This appendix lists every column of '
               'all %d tables with its type, nullability, key role, default and referential '
               'action. It is generated directly from schema.sql, the file the application '
               'validates its mappings against at start-up, so it cannot drift from the live '
               'database. %d columns and %d foreign keys are recorded.' % (len(tables), ncols, nfks))]
for i, t in enumerate(tables, 1):
    blocks.append(para('A.4.%d  %s' % (i, t['name']), style='Heading3'))
    blocks.append(table(t))
    blocks.append('<w:p><w:pPr><w:spacing w:after="60"/></w:pPr></w:p>')
NEW = ''.join(blocks)

# ---------------------------------------------------------------- splice it in
xml = zipfile.ZipFile(SRC).read('word/document.xml').decode('utf8')
b = re.search(r'<w:p[^>]*>(?:(?!</w:p>).)*?A\.4&#160;&#160;Database Schema|<w:p[^>]*>(?:(?!</w:p>).)*?A\.4  Database Schema', xml, re.S)
m = re.search(r'<w:p [^>]*>(?:(?!</w:p>).)*?APPENDIX B: USER MANUAL.*?</w:p>', xml, re.S)
assert m, 'APPENDIX B heading not found'
if b:
    xml = xml[:b.start()] + xml[m.start():]
    m = re.search(r'<w:p [^>]*>(?:(?!</w:p>).)*?APPENDIX B: USER MANUAL.*?</w:p>', xml, re.S)
    print('removed the previous A.4 block')
xml = xml[:m.start()] + NEW + xml[m.start():]

MD.parseString(xml.encode('utf8'))               # validate before writing
shutil.copy(SRC, '_docx/_t.docx')
zin = zipfile.ZipFile('_docx/_t.docx')
zout = zipfile.ZipFile('_docx/_n.docx', 'w', zipfile.ZIP_DEFLATED)
for it in zin.infolist():
    zout.writestr(it, xml.encode('utf8') if it.filename == 'word/document.xml'
                  else zin.read(it.filename))
zout.close(); zin.close()
shutil.move('_docx/_n.docx', SRC); os.remove('_docx/_t.docx')
print('Appendix A.4 inserted: %d tables, %d columns, %d foreign keys' % (len(tables), ncols, nfks))
