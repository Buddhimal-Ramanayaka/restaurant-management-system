# Dissertation docx tooling

Scripts that produced the revised dissertation from the submitted one. They edit
`word/document.xml` inside the `.docx` directly, because the changes are structural
(figure extents, run properties, generated appendix tables) rather than things worth
hand-editing across an 82-page document.

The `.docx` files themselves are not in this repository — `_docx/` is gitignored, and
these scripts expect to run from the repository root with the working document at
`_docx/out.docx`.

## The rule these all follow

**Parse before you write.** Every script that modifies the document builds the new XML
in memory, runs `xml.dom.minidom.parseString` on it, and only then rewrites the zip. An
earlier version wrote first and validated afterwards, which corrupted `document.xml` by
hand-computing string offsets while blanking runs; recovery meant restoring from a
delivered copy. The validate-then-write order makes that failure impossible — a bad edit
raises `ExpatError` and the file on disk is untouched.

The related trap: when splicing several matches into one string, work **back to front**.
Splicing forward invalidates every later offset. `finalfix.py` corrupted the Appendix C
tables exactly this way before it was changed to iterate `reversed(...)`.

## Order

| # | Script | What it does |
|---|---|---|
| 1 | `datadict.py` | Reads `backend/src/main/resources/schema.sql` and writes the A.4 data dictionary as markdown — 20 tables, 126 columns, 23 FKs. Generated so it cannot drift from the database. |
| 2 | `insert_a4.py` | Turns that markdown into Appendix A.4's Word tables. Idempotent: strips any existing A.4 block first. Uses `<w:tblLayout w:type="fixed"/>` with dxa widths — percentage widths let Word auto-fit and wrap headers as "Colum n". |
| 3 | `appendixc.py` | Builds the Appendix C sample-report tables. Held at 9 pt by request. |
| 4 | `comply.py` | Sets margins to 37 mm binding / 25 mm elsewhere and forces table text to 11 pt. **Its figure-refit step is superseded by `swapfigs.py`** — it caps height at 8.60 in, which needlessly shrinks any figure whose height binds. Run it for the margins, not the figures. |
| 5 | `swapfigs.py` | Swaps the eight regenerated Chapter 2/3 figures in and sizes each to the page, capped at 5.80 × 9.28 in, whichever bound it hits first. |
| 6 | `finalfix.py` | The compliance pass: body prose stranded at Word's 10 pt fallback back to 12 pt, A.4 headings to match the rest, Appendix C header cells to 9 pt, `keepNext` binding each figure to its caption. |
| 7 | `cite.py` | Places the in-text IEEE citations. Matches an anchor against the paragraph's concatenated text, then maps the offset back to the individual `<w:t>` that contains it — Word splits a sentence across many runs, so a phrase you can see often is not a contiguous string in the XML. |
| 8 | `fix45.py` | Corrects §4.5, which claimed a commit history the repository does not have. |
| 9 | `pages.py` | Re-measures printed page numbers from the rendered PDF and rewrites every dot-leader entry. Run render → `pages.py` → render until it reports 0 corrections. |

Render between steps with Word itself (COM automation), not LibreOffice — pagination
differs enough to make the measured page numbers wrong.

## Two sizing facts worth keeping

The A4 text column is **5.80 in × 9.28 in**. But 9.28 in is the cap for the *image*: a
figure whose caption wraps to two lines needs about 0.6 in more, against a 9.72 in
column. At the full 9.28 in Word overrides `keepNext` and drops the caption onto the next
page. Figures 3.4 and 3.6 are both capped at 9.00 in for this reason.

`docDefaults` in this document declares a font but **no size**, so any run without an
explicit `w:sz` renders at Word's 10 pt fallback rather than the 12 pt body size. This is
invisible in the XML and easy to introduce when adding a sentence. `finalfix.py` caught 18
such paragraphs; the check is worth re-running after any edit that adds text.
