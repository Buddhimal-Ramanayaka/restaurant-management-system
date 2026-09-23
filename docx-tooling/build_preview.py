"""Generate the report-preview artifact page from the live .docx.

Re-run this after any change to out.docx and republish to the SAME artifact url,
so the preview is regenerated rather than rebuilt by hand each iteration.

    python _docx/build_preview.py && <publish _docx/preview/index.html to the same url>

Content comes from report_content.json, which is extracted from word/document.xml
in document order: headings, body paragraphs, captions, tables and figure
placements. Nothing here is written by hand except the submission checklist.
"""
import json, io, os, re, html, datetime

# the deadline is fixed; the countdown is not, so it is computed rather than typed
DEADLINE = datetime.date(2026, 9, 25)
DAYS_LEFT = (DEADLINE - datetime.date.today()).days

BLOCKS = json.load(io.open('_docx/report_content.json', encoding='utf8'))
OUT = '_docx/preview/index.html'

# ---------------------------------------------------------------- checklist --
# state: done | active | blocked | yours
EXAMINER = [
    ('1',  'No colours on the topics', 'done',
     'Heading 1–6, TOC Heading and Subtitle styles set to black; 26 paragraphs of direct dark-blue stripped. Tables taken monochrome too: 154 blue header cells → grey, 151 light-blue rows → white.'),
    ('2',  'Abstract rewritten', 'done',
     '578 words over 7 paragraphs with bold internal headings → 334 words in 4 continuous paragraphs. Code-level detail removed, one page as the template requires.'),
    ('3',  'Compare existing systems — features and screenshots', 'done',
     'Table 2.1 compares all three against the proposed system on eleven capabilities, plus §2.1.5. Figure 2.2 is the Cloudbeds reservation calendar and Figure 2.3 the eZee Optimus POS, both from the vendors’ own product pages. Maestro publishes no interface image anywhere on its site, and the report says so rather than substituting a marketing photograph.'),
    ('4',  'Flow chart for the existing system', 'done',
     'Figure 2.1 and §2.1.1. Four swimlanes mirroring Figure 3.7, so the before and after read against each other. Every weakness on the chart is one of the five problems from §1.2 with its measured figure carried across.'),
    ('5',  'Reason for selecting the SDLC', 'done',
     '§1.5 Development Methodology, 547 words. Waterfall argued on four project-specific grounds, phases mapped to chapters, alternatives rejected, limitation acknowledged.'),
    ('6',  'Diagrams read as AI-generated', 'done',
     'All eight Chapter 2–3 diagrams redrawn in plain black-on-white UML. No palette, no per-actor colour coding; distinction comes from position and notation, which is what standard UML relies on.'),
    ('7',  'Formatting issues', 'yours',
     'Yours to handle. The document passes every check that can be measured against the IT5106 template: margins, font sizes, figure sizing, caption pairing, and 111 front-matter page numbers verified against the rendered PDF. Tell me what the examiner meant and I will apply it.'),
    ('8',  'Include use case diagram', 'done',
     'It was present, but at the end of §2.1 Review of Existing Systems — a model of the proposed system filed under the competitor reviews. Moved to the head of §2.2 Functional Requirements, where a reader looks for it. Now Figure 2.4.'),
    ('9',  'Screenshots of the project', 'done',
     'Figures 3.8–3.10 were rendered mock-ups, down to a drawn browser chrome and placeholder thumbnails. Replaced with captures of the running system, and the cashier billing terminal added as §3.9.4 / Figure 3.11. The admin panel is held back — its user table still shows three development accounts.'),
    ('10', 'Coding practices', 'done',
     '§4.5 Coding Practices and Standards, 642 words. Every figure counted from the repository: 54 DTO records, 43 constructor-injected classes, 18 transactional services, 24 validated entry points, 4 domain exceptions.'),
    ('11', 'Reports belong under the Evaluation chapter', 'done',
     'Appendix C is now §5.7 Sample Management Reports. C.1–C.3 became 5.7.1–5.7.3 and Tables C.1–C.3 renumbered to 5.5–5.7, continuing Chapter 5’s run.'),
    ('12', 'Evidence of user acceptance testing', 'yours',
     'UAT pack built and delivered — 31 role-specific tasks, rating scale, signature block. Testing genuinely happened; the forms record it. Needs printing and signing.'),
]

OTHER = [
    ('Sign the Declaration', 'yours', 'Names and dates are filled; signature lines are blank by design for print.'),
    ('Delete the test accounts', 'yours', 'asdas, asd and a duplicate NilanthiAdmin still exist. A database delete was blocked here, so remove them from the Admin panel and I will capture the figure and add it as §3.9.5.'),
    ('Decide the revenue-chart data', 'yours', 'Demo data stops 1 August, so the 7-day chart in Figure 3.10 shows one bar. Operate the system across a few days, or leave it sparse and honest.'),
    ('Restore your two figure sizes', 'done', 'Confirmed deliberate. 5.56 / 8.67 is exactly the class diagram’s aspect, so both were corner drags: a chosen height with the width following. Heights restored at 8.67 and 8.91 in and the widths derived from the new artwork, which keeps each figure and its caption on one page without stretching the drawing.'),

    ('Correct the eZee reference', 'done', 'Done, and it turned up an error: §2.1.4 claimed eZee has no ingredient-level deduction. eZee Optimus does — recipe management, thresholds, unit conversion, purchase management, USD 60 per outlet per month. Section rewritten and reference [14] repointed.'),
    ('Final repagination', 'done', 'Run. 101 entries rewritten, then re-rendered and re-checked: 111 front-matter numbers, all correct. Must run once more after any further content change.'),
    ('Final verification pass', 'done', 'verify.py re-derives every page number from the PDF independently of the writer: 111 entries correct, 111 hyperlink anchors resolving, 25 captions with no duplicates and none orphaned from its image.'),
]


STATE = {
    'done':    ('Done', 'ok'),
    'active':  ('In progress', 'warn'),
    'todo':    ('To do', 'idle'),
    'blocked': ('Blocked', 'stop'),
    'yours':   ('With you', 'you'),
}

# ------------------------------------------------------------------ helpers --
def esc(s): return html.escape(s, quote=False)

def slug(s):
    return re.sub(r'[^a-z0-9]+', '-', s.lower()).strip('-')[:60]

# ------------------------------------------------------------ build content --
nav, body, cur_h1 = [], [], None
fig_seq = 0
pending_fig = None

for i, b in enumerate(BLOCKS):
    k = b['k']
    if k == 'h1':
        cur_h1 = slug(b['t'])
        nav.append(('h1', b['t'], cur_h1))
        body.append(f'<h2 class="ch" id="{cur_h1}">{esc(b["t"])}</h2>')
    elif k == 'h2':
        sid = slug(b['t'])
        nav.append(('h2', b['t'], sid))
        body.append(f'<h3 id="{sid}">{esc(b["t"])}</h3>')
    elif k == 'h3':
        body.append(f'<h4>{esc(b["t"])}</h4>')
    elif k == 'p':
        body.append(f'<p>{esc(b["t"])}</p>')
    elif k == 'fig':
        pending_fig = b
        fig_seq += 1
    elif k == 'cap':
        t = b['t']
        if pending_fig and t.startswith('Figure'):
            f = pending_fig
            name = f['src'].replace('media/', '')
            meta = (f'{f["w"]:.2f} &times; {f["h"]:.2f} in &middot; {f["px"][0]}&times;{f["px"][1]} px '
                    f'&middot; {f["dpi"]} dpi')
            body.append(
                f'<figure><img src="fig/{name}" alt="{esc(t)}" loading="lazy">'
                f'<figcaption>{esc(t)}<span class="meta">{meta}</span></figcaption></figure>')
            pending_fig = None
        else:
            body.append(f'<p class="tcap">{esc(t)}</p>')
    elif k == 'table':
        rows = b['rows']
        if not rows: continue
        head = rows[0]; rest = rows[1:]
        th = ''.join(f'<th>{esc(c)}</th>' for c in head)
        tr = ''.join('<tr>' + ''.join(f'<td>{esc(c)}</td>' for c in r) + '</tr>' for r in rest)
        body.append(f'<div class="tw"><table><thead><tr>{th}</tr></thead><tbody>{tr}</tbody></table></div>')

# nav markup
nav_html = []
for lvl, t, sid in nav:
    cls = 'n1' if lvl == 'h1' else 'n2'
    nav_html.append(f'<a class="{cls}" href="#{sid}">{esc(t)}</a>')

def checklist(items, withnum):
    out = []
    for it in items:
        if withnum:
            num, title, st, note = it
            badge = f'<span class="num">{num}</span>'
        else:
            title, st, note = it
            badge = ''
        label, tone = STATE[st]
        out.append(
            f'<li class="ci {tone}">{badge}<div class="cb"><div class="ct">{esc(title)}'
            f'<span class="pill {tone}">{label}</span></div>'
            f'<p class="cn">{esc(note)}</p></div></li>')
    return '\n'.join(out)

counts = {}
for _, _, st, _ in EXAMINER:
    counts[st] = counts.get(st, 0) + 1

PAGE = f'''<title>2019035 Report Preview</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500&family=IBM+Plex+Sans:wght@400;500;600&family=Source+Serif+4:opsz,wght@8..60,400;8..60,600&display=swap">
<style>
:root{{
  --paper:#f7f7f4; --surface:#ffffff; --ink:#15191f; --ink-2:#3e4650; --muted:#767d88;
  --rule:#e3e3dc; --rule-2:#eeeee8; --accent:#b0450f; --accent-soft:#f3e3d8;
  --ok:#2f6b46; --warn:#9a6212; --stop:#9c3327; --idle:#6b7280; --you:#3b5d97;
  --ok-bg:#e6f0e9; --warn-bg:#f8eeda; --stop-bg:#f7e4e1; --idle-bg:#ecedef; --you-bg:#e4eaf4;
  --measure:70ch;
}}
@media (prefers-color-scheme:dark){{:root:not([data-theme="light"]){{
  --paper:#14171c; --surface:#1a1e24; --ink:#e7e8e4; --ink-2:#b9bec6; --muted:#8b929c;
  --rule:#2a2f37; --rule-2:#22262d; --accent:#f08040; --accent-soft:#3a2418;
  --ok:#78c096; --warn:#d9a84a; --stop:#e0897c; --idle:#9aa1ac; --you:#8fb0e0;
  --ok-bg:#1c2a22; --warn-bg:#2c2517; --stop-bg:#2e1f1d; --idle-bg:#242830; --you-bg:#1d2534;
}}}}
:root[data-theme="dark"]{{
  --paper:#14171c; --surface:#1a1e24; --ink:#e7e8e4; --ink-2:#b9bec6; --muted:#8b929c;
  --rule:#2a2f37; --rule-2:#22262d; --accent:#f08040; --accent-soft:#3a2418;
  --ok:#78c096; --warn:#d9a84a; --stop:#e0897c; --idle:#9aa1ac; --you:#8fb0e0;
  --ok-bg:#1c2a22; --warn-bg:#2c2517; --stop-bg:#2e1f1d; --idle-bg:#242830; --you-bg:#1d2534;
}}
*{{box-sizing:border-box}}
body{{margin:0;background:var(--paper);color:var(--ink);
  font:400 15px/1.6 "IBM Plex Sans",system-ui,sans-serif;-webkit-font-smoothing:antialiased}}
a{{color:inherit}}
.wrap{{display:grid;grid-template-columns:270px minmax(0,1fr);gap:0;min-height:100%}}

/* ---------- rail ---------- */
.rail{{position:sticky;top:env(safe-area-inset-top,0px);align-self:start;height:100dvh;
  overflow-y:auto;border-right:1px solid var(--rule);background:var(--surface);padding:22px 0 40px}}
.brand{{padding:0 20px 16px;border-bottom:1px solid var(--rule-2);margin-bottom:14px}}
.brand h1{{margin:0;font:600 15px/1.3 "IBM Plex Sans",sans-serif;letter-spacing:-.01em}}
.brand .sub{{margin-top:3px;font:400 12px/1.45 "IBM Plex Mono",monospace;color:var(--muted);white-space:nowrap}}
.railnav{{display:flex;flex-direction:column}}
.railnav a{{text-decoration:none;color:var(--ink-2);padding:5px 20px;font-size:13px;
  border-left:2px solid transparent}}
.railnav a:hover{{background:var(--rule-2);color:var(--ink)}}
.railnav .n1{{font-weight:600;color:var(--ink);margin-top:12px;font-size:12px;
  text-transform:uppercase;letter-spacing:.06em}}
.railnav .n2{{padding-left:28px}}
.railnav a:focus-visible{{outline:2px solid var(--accent);outline-offset:-2px}}

/* ---------- main ---------- */
main{{padding-block:34px;padding-left:clamp(16px,4vw,56px);padding-right:clamp(16px,4vw,56px);
  max-width:calc(var(--measure) + 12vw)}}
.masthead{{border-bottom:2px solid var(--ink);padding-bottom:18px;margin-bottom:8px}}
.eyebrow{{font:500 11px/1 "IBM Plex Mono",monospace;letter-spacing:.14em;text-transform:uppercase;
  color:var(--accent);margin-bottom:10px}}
.masthead h2{{margin:0;font:600 clamp(26px,3.4vw,36px)/1.15 "Source Serif 4",Georgia,serif;
  letter-spacing:-.015em;text-wrap:balance}}
.masthead .byline{{margin-top:10px;font:400 13px/1.5 "IBM Plex Mono",monospace;color:var(--muted)}}
.stats{{display:flex;flex-wrap:wrap;gap:0;margin:20px 0 34px;border:1px solid var(--rule);
  border-radius:3px;overflow:hidden;background:var(--surface)}}
.stat{{flex:1 1 118px;padding:12px 14px;border-right:1px solid var(--rule)}}
.stat:last-child{{border-right:0}}
.stat b{{display:block;font:500 19px/1.2 "IBM Plex Mono",monospace;
  font-variant-numeric:tabular-nums}}
.stat span{{display:block;margin-top:3px;font-size:11px;letter-spacing:.05em;
  text-transform:uppercase;color:var(--muted)}}

/* ---------- checklist ---------- */
.sec-label{{font:600 11px/1 "IBM Plex Mono",monospace;letter-spacing:.14em;text-transform:uppercase;
  color:var(--muted);padding-bottom:8px;border-bottom:1px solid var(--rule);margin:40px 0 18px}}
ul.check{{list-style:none;margin:0 0 30px;padding:0;display:flex;flex-direction:column;gap:2px}}
.ci{{display:flex;gap:12px;align-items:flex-start;padding:11px 13px;background:var(--surface);
  border:1px solid var(--rule-2);border-left:3px solid var(--idle)}}
.ci.ok{{border-left-color:var(--ok)}} .ci.warn{{border-left-color:var(--warn)}}
.ci.stop{{border-left-color:var(--stop)}} .ci.you{{border-left-color:var(--you)}}
.num{{flex:none;width:22px;font:500 12px/1.7 "IBM Plex Mono",monospace;color:var(--muted);
  font-variant-numeric:tabular-nums}}
.cb{{min-width:0}}
.ct{{font-weight:500;font-size:14px;display:flex;gap:9px;align-items:baseline;flex-wrap:wrap}}
.cn{{margin:4px 0 0;font-size:12.5px;line-height:1.5;color:var(--muted)}}
.pill{{font:500 10px/1 "IBM Plex Mono",monospace;letter-spacing:.06em;text-transform:uppercase;
  padding:3px 6px;border-radius:2px;white-space:nowrap}}
.pill.ok{{background:var(--ok-bg);color:var(--ok)}}
.pill.warn{{background:var(--warn-bg);color:var(--warn)}}
.pill.stop{{background:var(--stop-bg);color:var(--stop)}}
.pill.idle{{background:var(--idle-bg);color:var(--idle)}}
.pill.you{{background:var(--you-bg);color:var(--you)}}

/* ---------- document ---------- */
.doc{{max-width:var(--measure)}}
.doc h2.ch{{font:600 25px/1.2 "Source Serif 4",Georgia,serif;margin:52px 0 16px;
  padding-top:22px;border-top:1px solid var(--rule);letter-spacing:-.01em;text-wrap:balance}}
.doc h3{{font:600 18px/1.3 "Source Serif 4",Georgia,serif;margin:32px 0 10px;text-wrap:balance}}
.doc h4{{font:600 15px/1.35 "IBM Plex Sans",sans-serif;margin:22px 0 7px;color:var(--ink-2)}}
.doc p{{font:400 16px/1.72 "Source Serif 4",Georgia,serif;margin:0 0 13px;color:var(--ink)}}
.doc p.tcap{{font:600 13px/1.5 "IBM Plex Sans",sans-serif;color:var(--muted);margin:22px 0 7px}}
figure{{margin:26px 0;background:var(--surface);border:1px solid var(--rule);border-radius:3px;
  padding:14px}}
figure img{{display:block;width:100%;height:auto;border:1px solid var(--rule-2)}}
figcaption{{margin-top:11px;font:600 13px/1.45 "IBM Plex Sans",sans-serif;color:var(--ink-2)}}
figcaption .meta{{display:block;margin-top:5px;font:400 11px/1.4 "IBM Plex Mono",monospace;
  color:var(--muted);font-variant-numeric:tabular-nums}}
.tw{{overflow-x:auto;margin:14px 0 24px;border:1px solid var(--rule);border-radius:3px;
  background:var(--surface)}}
table{{border-collapse:collapse;width:100%;font-size:12.5px}}
th,td{{text-align:left;padding:7px 11px;border-bottom:1px solid var(--rule-2);vertical-align:top}}
th{{background:var(--rule-2);font-weight:600;white-space:nowrap;font-size:11.5px;
  letter-spacing:.03em;text-transform:uppercase;color:var(--ink-2)}}
tbody tr:last-child td{{border-bottom:0}}
.foot{{margin-top:54px;padding-top:16px;border-top:1px solid var(--rule);
  font:400 12px/1.6 "IBM Plex Mono",monospace;color:var(--muted)}}

@media (max-width:880px){{
  .wrap{{grid-template-columns:1fr}}
  .rail{{position:static;height:auto;border-right:0;border-bottom:1px solid var(--rule);
    max-height:none;padding-bottom:18px}}
  .railnav{{max-height:220px;overflow-y:auto}}
}}
@media (prefers-reduced-motion:reduce){{*{{animation:none!important;transition:none!important}}}}
</style>

<div class="wrap">
  <aside class="rail">
    <div class="brand">
      <h1>Restaurant Management System</h1>
      <div class="sub">2019035 &middot; IT5106</div>
    </div>
    <nav class="railnav">
      <a class="n1" href="#top">Submission status</a>
      {chr(10).join('      ' + n for n in nav_html)}
    </nav>
  </aside>

  <main id="top">
    <header class="masthead">
      <div class="eyebrow">Report preview &middot; re-submission due 25 Sep</div>
      <h2>Restaurant Management System for SME Restaurants</h2>
      <div class="byline">H. R. B. Prasanga &middot; Index 2019035 &middot; supervised by I. V. Gimahana Mithuranga</div>
    </header>

    <div class="stats">
      <div class="stat"><b>{sum(1 for b in BLOCKS if b['k'].startswith('h'))}</b><span>headings</span></div>
      <div class="stat"><b>{sum(1 for b in BLOCKS if b['k']=='fig')}</b><span>figures</span></div>
      <div class="stat"><b>{sum(1 for b in BLOCKS if b['k']=='table')}</b><span>tables</span></div>
      <div class="stat"><b>{counts.get('done',0)}/12</b><span>examiner items done</span></div>
      <div class="stat"><b>{DAYS_LEFT}</b><span>days remaining</span></div>
    </div>

    <div class="sec-label">Examiner&rsquo;s report evaluation &mdash; 12 points</div>
    <ul class="check">
{checklist(EXAMINER, True)}
    </ul>

    <div class="sec-label">Everything else before the 25th</div>
    <ul class="check">
{checklist(OTHER, False)}
    </ul>

    <div class="sec-label">The report</div>
    <div class="doc">
{chr(10).join(body)}
    </div>

    <p class="foot">Generated from out.docx &mdash; headings, body text, captions, tables and figures in
    document order. Figure metadata shows printed size and resolution as placed in the Word file.</p>
  </main>
</div>
'''

os.makedirs('_docx/preview', exist_ok=True)
io.open(OUT, 'w', encoding='utf8').write(PAGE)
print('wrote %s  (%.0f KB)' % (OUT, os.path.getsize(OUT) / 1024))
print('  nav entries: %d   body blocks: %d' % (len(nav_html), len(body)))
print('  examiner items: %s' % ', '.join(f'{k}={v}' for k, v in sorted(counts.items())))
