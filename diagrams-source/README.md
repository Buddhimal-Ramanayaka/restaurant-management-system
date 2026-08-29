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
| 3.4 Sequence | `fig3_4_sequence.mmd` + `_seq.css` | mermaid-cli |
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

# mermaid (scale 3 for print resolution; 3.4 needs its stylesheet, 3.6 must not have one)
mmdc -i fig3_4_sequence.mmd -o fig3_4.png -C _seq.css -b white -s 3
mmdc -i fig3_6_dfd.mmd      -o fig3_6.png            -b white -s 3

# HTML mockups and the architecture diagram
node shot.mjs mock_3_8_pos.html fig3_8_pos.png 3
```

`shot.mjs` hard-codes a Chrome path — change `executablePath` for your machine. The
mockups pull the app's own fonts from `../frontend/src/assets/fonts/`, so render them
from this directory.

## Sizing for the page

The A4 text column is **5.80 in wide × 9.28 in tall** (37 mm binding margin, 25 mm
elsewhere, less room for a caption). A figure is scaled by whichever bound it hits first:

```
printed_pt = source_font_px × 417.6 ÷ canvas_width_px     when width binds
printed_pt = source_font_px × 668.2 ÷ canvas_height_px    when height binds
```

The two cross at aspect **0.625**. Two consequences that are easy to get wrong:

- Raising the font size does **not** help. It enlarges the canvas by the same factor and
  cancels out. Only reducing content, or moving toward the 0.625 aspect, changes the
  printed size.
- Once width binds, height is free up to 9.28 in. Wrapping a label onto more lines is
  then a straight win.

## Gotchas hit while building these

- **PlantUML** `#COLOR:text;` is deprecated: it prints a warning banner into the image
  and swallows following lines into the node label. Use `:text; <<#COLOR>>`.
- **PlantUML** `partition` draws its box across every swimlane it touches — use a note.
- **PlantUML** `detach`/`kill` end a branch without a terminator but place the node
  *beside* the decision, widening the lane. `stop` keeps it below.
- **PlantUML** does not carry `<i>` across a line break; tag each line.
- **mermaid** subgraphs with no edge between them render side by side — link them with
  `~~~` to stack.
- **mermaid** measures text before a `-C` stylesheet applies, so forcing a font there
  clips labels. Set `fontFamily` in `themeVariables` instead. Fig 3.6 renders without a
  stylesheet for exactly this reason.
