import zipfile, re, shutil

SRC='_docx/out.docx'
x=zipfile.ZipFile(SRC).read('word/document.xml').decode('utf8')
def esc(s): return s.replace('&','&amp;').replace('<','&lt;').replace('>','&gt;')

MONO='<w:rFonts w:ascii="Consolas" w:hAnsi="Consolas" w:cs="Consolas"/><w:sz w:val="16"/><w:szCs w:val="16"/>'

def hdr(lines):
    runs=[]
    for i,l in enumerate(lines):
        if i: runs.append('<w:r><w:rPr>'+MONO+'</w:rPr><w:br/></w:r>')
        b='<w:b/><w:bCs/>' if i<2 else ''
        runs.append('<w:r><w:rPr>'+b+MONO+'</w:rPr><w:t xml:space="preserve">'+esc(l)+'</w:t></w:r>')
    return ('<w:p w:rsidR="00BA52E2" w:rsidRDefault="00BA52E2"><w:pPr>'
      '<w:pBdr><w:top w:val="single" w:sz="6" w:space="2" w:color="999999"/>'
      '<w:bottom w:val="single" w:sz="6" w:space="2" w:color="999999"/></w:pBdr>'
      '<w:shd w:val="clear" w:color="auto" w:fill="F5F5F5"/>'
      '<w:spacing w:before="120" w:after="0" w:line="240" w:lineRule="auto"/></w:pPr>'
      + ''.join(runs) + '</w:p>')

def ftr(text):
    return ('<w:p w:rsidR="00BA52E2" w:rsidRDefault="00BA52E2"><w:pPr>'
      '<w:spacing w:before="40" w:after="200" w:line="240" w:lineRule="auto"/></w:pPr>'
      '<w:r><w:rPr><w:i/><w:iCs/><w:color w:val="666666"/>'+MONO+'</w:rPr>'
      '<w:t xml:space="preserve">'+esc(text)+'</w:t></w:r></w:p>')

REPORTS=[
 (['DAIYA FOOD RESTAURANT  |  RESTAURANT MANAGEMENT SYSTEM v1.0',
   'DAILY SALES REPORT',
   'Report ID : RPT-DSR-20250713-001        Generated : 2025-07-13 23:58:12 +05:30',
   'Requested : manager (D. Jayasinghe)     Source    : GET /api/analytics/daily-sales?date=2025-07-13'],
  'End of report  |  RPT-DSR-20250713-001  |  Restaurant Management System v1.0'),
 (['DAIYA FOOD RESTAURANT  |  RESTAURANT MANAGEMENT SYSTEM v1.0',
   'SHIFT CASH RECONCILIATION',
   'Report ID : RPT-SCR-20250713-004        Generated : 2025-07-13 16:18:42 +05:30',
   'Requested : cashier (K. Wijesinghe)     Source    : POST /api/billing/shifts/{id}/close'],
  'End of report  |  RPT-SCR-20250713-004  |  Restaurant Management System v1.0'),
 (['DAIYA FOOD RESTAURANT  |  RESTAURANT MANAGEMENT SYSTEM v1.0',
   'INVENTORY VARIANCE REPORT',
   'Report ID : RPT-IVR-20250713-002        Generated : 2025-07-13 22:05:31 +05:30',
   'Requested : manager (D. Jayasinghe)     Source    : inventory_ledger reconciled against physical cycle count'],
  'End of report  |  RPT-IVR-20250713-002  |  Restaurant Management System v1.0'),
]

# the last three tables in the document are Appendix C's
tbls=[(m.start(),m.end()) for m in re.finditer(r'<w:tbl>.*?</w:tbl>', x, re.S)]
targets=tbls[-3:]
assert len(targets)==3
for (s,e),(lines,foot) in sorted(zip(targets,REPORTS), key=lambda z:-z[0][0]):
    x = x[:e] + ftr(foot) + x[e:]      # footer after table
    x = x[:s] + hdr(lines) + x[s:]     # header before table
print('Appendix C: %d report headers + footers added' % len(REPORTS))

zin=zipfile.ZipFile(SRC); shutil.copy(SRC,'_docx/_t.docx')
zout=zipfile.ZipFile('_docx/_t.docx','w',zipfile.ZIP_DEFLATED)
for it in zin.infolist():
    d=zin.read(it.filename)
    if it.filename=='word/document.xml': d=x.encode('utf8')
    zout.writestr(it,d)
zout.close(); zin.close(); shutil.move('_docx/_t.docx',SRC)
