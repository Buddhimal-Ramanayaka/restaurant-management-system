"""Independent check of every front-matter page number against the rendered PDF.

Deliberately does not reuse pages.py's lookup: it re-derives each target page from
the PDF on its own terms, so a bug in the writer cannot hide behind the checker.
Also reports figure/caption pairing, unresolved hyperlink anchors, and any
duplicate figure or table number.
"""
import subprocess, re, zipfile, sys

PDF = sys.argv[1] if len(sys.argv) > 1 else '_docx/out.pdf'
SRC = sys.argv[2] if len(sys.argv) > 2 else '_docx/out.docx'

n = int(subprocess.run(['pdfinfo', PDF], capture_output=True, text=True)
        .stdout.split('Pages:')[1].split()[0])
pages = {}
for p in range(1, n + 1):
    t = subprocess.run(['pdftotext', '-layout', '-f', str(p), '-l', str(p), PDF, '-'],
                       capture_output=True, text=True, errors='replace').stdout
    lines = [l.strip() for l in t.splitlines() if l.strip()]
    lab = None
    for l in reversed(lines[-3:]):
        if re.fullmatch(r'[ivxlcdm]+|\d{1,3}', l, re.I):
            lab = l; break
    pages[p] = (lab, [l for l in lines if '...' not in l])

def find(pat, body_only=False):
    for p in sorted(pages):
        lab, lines = pages[p]
        if not lab or (body_only and not lab.isdigit()):
            continue
        if any(re.match(pat, l) for l in lines):
            return lab
    return None

FRONT = {'Declaration': r'^DECLARATION$', 'Abstract': r'^ABSTRACT$',
         'Acknowledgements': r'^ACKNOWLEDGEMENTS$',
         'Table of Contents': r'^TABLE OF CONTENTS$',
         'List of Figures': r'^LIST OF FIGURES$', 'List of Tables': r'^LIST OF TABLES$',
         'List of Acronyms': r'^LIST OF (ACRONYMS|ABBREVIATIONS)'}

x = zipfile.ZipFile(SRC).read('word/document.xml').decode('utf8')
bad, checked = [], 0
for para in re.split(r'(?=<w:p[ >/])', x):
    if 'w:leader="dot"' not in para:
        continue
    ts = re.findall(r'<w:t[^>]*>([^<]*)</w:t>', para)
    if len(ts) < 2 or not re.fullmatch(r'[ivxlcdm\d]+', ts[-1].strip(), re.I):
        continue
    if len(ts) > 2 and any(re.fullmatch(r'[ivxlcdm\d]+', t.strip(), re.I) for t in ts[1:-1]):
        bad.append(('MULTI-ENTRY PARAGRAPH', ts[0].strip(), '', ''))
        continue
    lbl, cur = ts[0].strip(), ts[-1].strip()
    want = None
    if lbl in FRONT:
        want = find(FRONT[lbl])
    elif re.match(r'^\d+\.\d', lbl):
        sec = re.match(r'^([\d.]+)', lbl).group(1)
        want = find(r'^' + re.escape(sec) + r'\s+\S', True)
    elif lbl.startswith(('Figure ', 'Table ')):
        num = re.match(r'^((?:Figure|Table) [\d.]+):', lbl).group(1)
        want = find(r'^' + re.escape(num) + r':', True)
    elif lbl.startswith('References'):
        want = find(r'^REFERENCES$', True)
    elif lbl.startswith('Appendix'):
        L = re.search(r'Appendix ([A-Z])', lbl).group(1)
        want = find(r'^APPENDIX ' + L + r'\b', True)
    elif re.match(r'^CHAPTER \d', lbl, re.I):
        want = find(r'^' + re.escape(lbl.upper()), True)
    checked += 1
    if want is None:
        bad.append(('NOT FOUND IN PDF', lbl, cur, '-'))
    elif want != cur:
        bad.append(('WRONG PAGE', lbl, cur, want))

print('front-matter entries checked: %d' % checked)
for kind, lbl, cur, want in bad:
    print('  %-22s %-70s has %-4s want %s' % (kind, lbl[:70], cur, want))
if not bad:
    print('  all correct')

# --- anchors ---------------------------------------------------------------
anchors = set(re.findall(r'<w:bookmarkStart[^>]*w:name="([^"]+)"', x))
links = re.findall(r'<w:hyperlink w:anchor="([^"]+)"', x)
miss = [a for a in links if a not in anchors]
print('hyperlinks: %d, unresolved: %s' % (len(links), miss or 'none'))

# --- captions --------------------------------------------------------------
body = re.search(r'<w:body>(.*)</w:body>', x, re.S).group(1)
ps = re.findall(r'<w:p(?: [^>]*)?>.*?</w:p>|<w:p/>', body, re.S)
def txt(p): return ''.join(re.findall(r'<w:t[^>]*>([^<]*)</w:t>', p)).strip()
caps = [(i, txt(p)) for i, p in enumerate(ps)
        if re.match(r'^(Figure|Table) \d+\.\d+:', txt(p)) and 'w:leader="dot"' not in p]
nums = [re.match(r'^(\S+ \d+\.\d+)', t).group(1) for _, t in caps]
dupes = [v for v in set(nums) if nums.count(v) > 1]
print('captions: %d, duplicates: %s' % (len(caps), dupes or 'none'))
orphan = [t[:50] for i, t in caps
          if t.startswith('Figure')
          and not any('<w:drawing>' in ps[j] for j in range(max(0, i - 5), i))]
print('figures without a preceding image: %s' % (orphan or 'none'))

listed = [re.match(r'^(\S+ \d+\.\d+)', txt(p)).group(1) for p in ps
          if re.match(r'^(Figure|Table) \d+\.\d+:', txt(p)) and 'w:leader="dot"' in p]
for kind in ('Figure', 'Table'):
    a = [v for v in nums if v.startswith(kind)]
    b = [v for v in listed if v.startswith(kind)]
    print('%-7s captions %s' % (kind, 'match the list' if a == b else 'DIFFER\n  %s\n  %s' % (a, b)))
