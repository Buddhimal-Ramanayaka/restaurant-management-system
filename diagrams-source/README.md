# Diagram sources

Source for every figure in Chapters 2 and 3 of the dissertation. Each figure is
regenerated from these files rather than edited as an image, so a diagram can be
re-checked against the code it documents.

| Figure | Source | Tool |
|---|---|---|
| 2.1 Use Case | `fig2_1_usecase.puml` | PlantUML |
| 3.1 System Architecture | `fig3_1_architecture.html` | headless Chrome via `shot.mjs` |
| 3.2 Enhanced ERD | `fig3_2_erd.puml` | PlantUML |
| 3.3 Class Diagram | `fig3_3_class.puml` | PlantUML |
| 3.4 Sequence | `fig3_4_sequence.puml` | PlantUML |
| 3.5 State Machines | `fig3_5_state.puml` | PlantUML |
| 3.6 Data Flow | `fig3_6_dfd.mmd` | mermaid-cli |
| 3.7 Activity | `fig3_7_activity.puml` | PlantUML |
| 3.8 POS Terminal | `mock_3_8_pos.html` + `_mockbase.css` | headless Chrome via `shot.mjs` |
| 3.9 Kitchen Display | `mock_3_9_kds.html` + `_mockbase.css` | headless Chrome via `shot.mjs` |
| 3.10 Manager Dashboard | `mock_3_10_manager.html` + `_mockbase.css` | headless Chrome via `shot.mjs` |

## Rendering

```bash
# PlantUML
java -jar plantuml.jar -tpng fig3_2_erd.puml

# mermaid - no -C stylesheet: mermaid measures label widths before an external
# stylesheet applies, so forcing a font there makes every label clip
mmdc -i fig3_6_dfd.mmd -o fig3_6.png -b white -s 3

# HTML mockups and the architecture diagram
node shot.mjs mock_3_8_pos.html fig3_8_pos.png 3
```

`shot.mjs` hard-codes a Chrome path — change `executablePath` for your machine. The
mockups pull the app's own fonts from `../frontend/src/assets/fonts/`, so render them
from this directory.

## Sizing for the page

The A4 text column is **5.80 in wide × 9.28 in tall** (37 mm binding margin, 25 mm
elsewhere, less room for a caption). A figure is scaled by whichever bound it hits first.

**Measure from the source units, not the PNG.** For PlantUML the PNG maps 1:1 to layout
units, so `font_pt × 417.6 ÷ png_width_px` is right. For mermaid the PNG is the SVG
rasterised at whatever `-w`/`-s` you passed, so the PNG tells you nothing about text
size — read the `viewBox` and the CSS `font-size` out of an `.svg` render instead:

```
printed_pt = svg_font_px × 72 × printed_width_in ÷ svg_viewbox_width
```

Getting this wrong overstated both mermaid figures by roughly 2× for a while.

With that in hand:

```
printed_pt = source_font × 417.6 ÷ canvas_width      when width binds
printed_pt = source_font × 668.2 ÷ canvas_height     when height binds
```

The two cross at aspect **0.625**. Three consequences that are easy to get wrong:

- For PlantUML, raising the font size does **not** help — it enlarges the canvas by the
  same factor and cancels. Only reducing content, or moving toward the 0.625 aspect,
  changes the printed size.
- For a mermaid **flowchart** raising `fontSize` *does* help, because node padding and
  subgraph containers are fixed and do not scale with it. Fig 3.6 gains 5.1 → 6.5 pt
  going from 20px to 32px. Above ~32px mermaid's label measurement diverges from what it
  renders and text starts clipping, so that is the ceiling.
- Once width binds, height is free up to 9.28 in. Wrapping a label onto more lines is
  then a straight win.

For a sequence diagram the width is the sum of the lifeline columns, and each column is
as wide as its widest label, so `printed_pt ≈ 95 ÷ (longest label in characters)`. Eight
lifelines cannot reach 12 pt at 5.80 in however the labels are tuned; the detail belongs
in notes anchored `over` a pair of lifelines, which cost no width at all.

## Gotchas hit while building these

- **mermaid ignores `sequence` config entirely** — from a `%%{init}%%` directive *and*
  from `mmdc -c`. Setting `messageFontSize` to 40 produces a byte-identical SVG. Every
  spacing and font value is inert, and the diagram renders at a fixed 16px. That is why
  Fig 3.4 is PlantUML. Flowchart config is honoured; sequence config is not.
- **mermaid** subgraphs with no edge between them render side by side — link them with
  `~~~` to stack.
- **PlantUML** `#COLOR:text;` is deprecated in activity diagrams: it prints a warning
  banner into the image and swallows every following line into the node label. Use
  `:text; <<#COLOR>>`.
- **PlantUML** `partition` draws its box across every swimlane it touches — use a note.
- **PlantUML** `detach`/`kill` end a branch without a terminator but place the node
  *beside* the decision, widening the lane. `stop` keeps it below.
- **PlantUML** does not carry `<i>` across a line break; tag each line.
- **PlantUML** places the two levels of a two-container diagram side by side unless a
  hidden edge links them.
- Editing `\n` separators inside a shell heredoc turns them into real newlines and breaks
  the file. Build the backslash with `chr(92)`, or write the file outright. `sed` with
  `\\n` silently matches nothing and still exits 0.
