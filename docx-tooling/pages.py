"""Re-measure printed page numbers from the rendered PDF and rewrite every
dot-leader entry (TOC, List of Figures, List of Tables) to match.
Caption/section lookups are restricted to body (arabic-numbered) pages so a
front-matter list entry can never satisfy the match."""
import subprocess, re, zipfile, shutil, sys

PDF=sys.argv[1] if len(sys.argv)>1 else '_docx/out.pdf'
SRC=sys.argv[2] if len(sys.argv)>2 else '_docx/out.docx'
N=int(subprocess.run(['pdfinfo',PDF],capture_output=True,text=True).stdout.split('Pages:')[1].split()[0])
pages={}
for p in range(1,N+1):
    t=subprocess.run(['pdftotext','-layout','-f',str(p),'-l',str(p),PDF,'-'],
                     capture_output=True,text=True,errors='replace').stdout
    lines=[l.strip() for l in t.splitlines() if l.strip()]
    lab=lines[-1] if lines and re.fullmatch(r'[ivxlcdm]+|\d{1,3}',lines[-1],re.I) else None
    pages[p]=(lab,t)

def fh(pat, body_only=False):
    for p in sorted(pages):
        lab,t=pages[p]
        if not lab: continue
        if body_only and not lab.isdigit(): continue
        for line in t.splitlines():
            s=line.strip()
            if '...' in s: continue
            if re.match(pat,s): return lab
    return None

FRONT={'Declaration':r'^DECLARATION$','Abstract':r'^ABSTRACT$','Acknowledgements':r'^ACKNOWLEDGEMENTS$',
 'Table of Contents':r'^TABLE OF CONTENTS$','List of Figures':r'^LIST OF FIGURES$',
 'List of Tables':r'^LIST OF TABLES$','List of Acronyms':r'^LIST OF (ACRONYMS|ABBREVIATIONS)'}

x=zipfile.ZipFile(SRC).read('word/document.xml').decode('utf8')
out=[];changed=0;unresolved=[]
for para in re.split(r'(?=<w:p )', x):
    if 'w:leader="dot"' in para:
        ts=re.findall(r'<w:t[^>]*>([^<]*)</w:t>', para)
        if len(ts)>=2 and re.fullmatch(r'[ivxlcdm\d]+', ts[-1].strip(), re.I):
            lbl=ts[0].strip(); cur=ts[-1].strip(); act=None
            if lbl in FRONT: act=fh(FRONT[lbl])
            elif re.match(r'^\d\.\d', lbl):
                sec=re.match(r'^([\d.]+)',lbl).group(1); act=fh(r'^'+re.escape(sec)+r'\s+[A-Z]', True)
            elif lbl.startswith(('Figure ','Table ')):
                num=re.match(r'^((?:Figure|Table) [\dA-C.]+):',lbl).group(1)
                act=fh(r'^'+re.escape(num)+r':', True)
            elif lbl.startswith('References'): act=fh(r'^REFERENCES$', True)
            elif lbl.startswith('Appendix'):
                L=re.search(r'Appendix ([A-C])',lbl).group(1); act=fh(r'^APPENDIX '+L+r'\b', True)
            if act is None: unresolved.append(lbl)
            elif act!=cur:
                lm=list(re.finditer(r'(<w:t[^>]*>)([^<]*)(</w:t>)', para))[-1]
                para=para[:lm.start()]+lm.group(1)+act+lm.group(3)+para[lm.end():]
                changed+=1
    out.append(para)
x=''.join(out)
print('page numbers corrected:', changed)
if unresolved: print('UNRESOLVED:', unresolved)

zin=zipfile.ZipFile(SRC); shutil.copy(SRC,'_docx/_t.docx')
zout=zipfile.ZipFile('_docx/_t.docx','w',zipfile.ZIP_DEFLATED)
for it in zin.infolist():
    d=zin.read(it.filename)
    if it.filename=='word/document.xml': d=x.encode('utf8')
    zout.writestr(it,d)
zout.close(); zin.close(); shutil.move('_docx/_t.docx',SRC)
