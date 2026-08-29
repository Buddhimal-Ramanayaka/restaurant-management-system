"""Swap the regenerated Chapter 2/3 figures into out.docx and size them to the page.

The A4 text column is 8391tw wide and 14004tw tall (37mm binding margin, 25mm elsewhere).
A full-page figure gets the column width and the column height less room for its caption:
    5.80in x 9.28in
Each figure is scaled by whichever of the two binds first, preserving aspect. The earlier
version of this script capped height at 8.60in, which needlessly shrank every figure whose
height was the binding constraint.
"""
import zipfile, re, shutil, struct, os

SRC = '_docx/out.docx'
NEW = {
    'media/image5.png':  '_diagrams/fig2_1_usecase.png',       # Fig 2.1  Use Case
    'media/image6.png':  '_diagrams/fig3_1_architecture.png',  # Fig 3.1  Architecture
    'media/image7.png':  '_diagrams/fig3_2_erd.png',           # Fig 3.2  ERD
    'media/image8.png':  '_diagrams/fig3_3_class.png',         # Fig 3.3  Class
    'media/image9.png':  '_diagrams/fig3_4_sequence.png',      # Fig 3.4  Sequence (PlantUML)
    'media/image10.png': '_diagrams/fig3_5_state.png',         # Fig 3.5  State machines
    'media/image11.png': '_diagrams/fig3_6.png',               # Fig 3.6  DFD
    'media/image12.png': '_diagrams/fig3_7_activity.png',      # Fig 3.7  Activity
}
EMU = 914400
MAX_W = int(5.80 * EMU)
MAX_H = int(9.28 * EMU)

png = lambda p: struct.unpack('>II', open(p, 'rb').read(33)[16:24])

z = zipfile.ZipFile(SRC)
xml = z.read('word/document.xml').decode('utf8')
rels = z.read('word/_rels/document.xml.rels').decode('utf8')
rid = {m.group(2): m.group(1)
       for m in re.finditer(r'Id="(rId\d+)"[^>]*Target="(media/[^"]+)"', rels)}

for target, newfile in NEW.items():
    nw, nh = png(newfile)
    scale = min(MAX_W / nw, MAX_H / nh)
    cx, cy = int(round(nw * scale)), int(round(nh * scale))
    r = rid[target]
    for dm in re.finditer(r'<w:drawing>.*?</w:drawing>', xml, re.S):
        blk = dm.group(0)
        if 'r:embed="%s"' % r not in blk:
            continue
        em = re.search(r'<wp:extent cx="(\d+)" cy="(\d+)"/>', blk)
        nb = blk.replace(em.group(0), '<wp:extent cx="%d" cy="%d"/>' % (cx, cy))
        nb = re.sub(r'<a:ext cx="\d+" cy="\d+"/>', '<a:ext cx="%d" cy="%d"/>' % (cx, cy), nb)
        xml = xml[:dm.start()] + nb + xml[dm.end():]
        print('  %-18s <- %-26s %5dx%-5d  %.2f x %.2f in  %s-bound  %d dpi'
              % (target, os.path.basename(newfile), nw, nh, cx/EMU, cy/EMU,
                 'width' if MAX_W/nw <= MAX_H/nh else 'height', nw/(cx/EMU)))
        break

import xml.dom.minidom as M
M.parseString(xml.encode('utf8'))            # validate before writing anything

shutil.copy(SRC, '_docx/_t.docx')
zin = zipfile.ZipFile('_docx/_t.docx')
zout = zipfile.ZipFile('_docx/_s.docx', 'w', zipfile.ZIP_DEFLATED)
for it in zin.infolist():
    n = it.filename
    if n == 'word/document.xml':
        data = xml.encode('utf8')
    elif n.replace('word/', '') in NEW:
        data = open(NEW[n.replace('word/', '')], 'rb').read()
    else:
        data = zin.read(n)
    zout.writestr(it, data)
zout.close(); zin.close()
shutil.move('_docx/_s.docx', SRC)
os.remove('_docx/_t.docx')
print('%d figures swapped into %s' % (len(NEW), SRC))
