"""Bring out.docx into line with the IT5106 Final Project Report Template spec:
   left margin 37mm, top/bottom/right 25mm, table text 11pt, appendix line spacing 1.0.
Figures are re-fitted to the narrower printable column that the 37mm binding margin leaves.
"""
import zipfile, re, shutil

SRC='out.docx'
TW=1440/25.4                      # twips per mm
LEFT=round(37*TW); OTHER=round(25*TW)          # 2098 / 1417
PAGE_W=11906
PRINT_TW=PAGE_W-LEFT-OTHER                     # 8391 tw
EMU=914400
MAX_W=int((PRINT_TW/1440-0.03)*EMU)            # 5.80in, small slack for rounding
MAX_H=int(8.60*EMU)
FIGS=['media/image%d.png'%i for i in range(5,16)]

z=zipfile.ZipFile(SRC)
xml=z.read('word/document.xml').decode('utf8')

# ---- 1. margins -------------------------------------------------------------
n=0
def mar(m):
    global n; n+=1
    return ('<w:pgMar w:top="%d" w:right="%d" w:bottom="%d" w:left="%d" '
            'w:header="708" w:footer="708" w:gutter="0"/>'%(OTHER,OTHER,OTHER,LEFT))
xml=re.sub(r'<w:pgMar[^/]*/>',mar,xml)
print('1. margins  left=%dtw (37mm)  t/b/r=%dtw (25mm)   sections rewritten: %d'%(LEFT,OTHER,n))

# ---- 2. table text -> 11pt (T1..T6; Appendix C report facsimiles left alone) --
tbls=[m for m in re.finditer(r'<w:tbl>.*?</w:tbl>',xml,re.S)]
def force11(block):
    block=re.sub(r'<w:sz w:val="\d+"/>','<w:sz w:val="22"/>',block)
    block=re.sub(r'<w:szCs w:val="\d+"/>','<w:szCs w:val="22"/>',block)
    # runs with an rPr but no sz
    block=re.sub(r'(<w:rPr>(?:(?!</w:rPr>).)*?)</w:rPr>',
                 lambda m: m.group(1)+'</w:rPr>' if '<w:sz ' in m.group(1)
                 else m.group(1)+'<w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr>', block, flags=re.S)
    # runs with no rPr at all
    block=re.sub(r'<w:r>(?!<w:rPr>)','<w:r><w:rPr><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr>',block)
    return block
for m in reversed(tbls[:6]):
    xml=xml[:m.start()]+force11(m.group(0))+xml[m.end():]
print('2. table text -> 11pt in tables 1-6 (Appendix C report facsimiles kept at their generated size)')

# ---- 3. appendix line spacing 1.5 -> 1.0 (outside tables) --------------------
am=re.search(r'<w:p [^>]*>(?:(?!</w:p>).)*?APPENDIX A: SYSTEM MANUAL',xml,re.S)
head=xml[:am.start()]; tail=xml[am.start():]
parts=re.split(r'(<w:tbl>.*?</w:tbl>)',tail,flags=re.S)
c=0
for i,p in enumerate(parts):
    if p.startswith('<w:tbl>'): continue
    parts[i],k=re.subn(r'w:line="360"','w:line="240"',p); c+=k
xml=head+''.join(parts)
print('3. appendix line spacing 1.5 -> 1.0  (%d paragraphs)'%c)

# ---- 4. re-fit figures to the narrower printable column ---------------------
rels=z.read('word/_rels/document.xml.rels').decode('utf8')
rid={m.group(2):m.group(1) for m in re.finditer(r'Id="(rId\d+)"[^>]*Target="(media/[^"]+)"',rels)}
for t in FIGS:
    r=rid[t]
    for dm in re.finditer(r'<w:drawing>.*?</w:drawing>',xml,re.S):
        b=dm.group(0)
        if 'r:embed="%s"'%r not in b: continue
        e=re.search(r'<wp:extent cx="(\d+)" cy="(\d+)"/>',b)
        cx,cy=int(e.group(1)),int(e.group(2))
        if cx>MAX_W: cy=int(round(cy*MAX_W/cx)); cx=MAX_W
        if cy>MAX_H: cx=int(round(cx*MAX_H/cy)); cy=MAX_H
        nb=b.replace(e.group(0),'<wp:extent cx="%d" cy="%d"/>'%(cx,cy))
        nb=re.sub(r'<a:ext cx="\d+" cy="\d+"/>','<a:ext cx="%d" cy="%d"/>'%(cx,cy),nb)
        xml=xml[:dm.start()]+nb+xml[dm.end():]
        break
print('4. figures capped to %.2fin (printable column %.2fin)'%(MAX_W/EMU,PRINT_TW/1440))

shutil.copy(SRC,'_t.docx')
zin=zipfile.ZipFile('_t.docx'); zout=zipfile.ZipFile('_c.docx','w',zipfile.ZIP_DEFLATED)
for it in zin.infolist():
    zout.writestr(it, xml.encode('utf8') if it.filename=='word/document.xml' else zin.read(it.filename))
zout.close(); zin.close()
shutil.move('_c.docx',SRC)
import os; os.remove('_t.docx')
import xml.dom.minidom as M; M.parseString(zipfile.ZipFile(SRC).read('word/document.xml'))
print('   document.xml well-formed; written to',SRC)
