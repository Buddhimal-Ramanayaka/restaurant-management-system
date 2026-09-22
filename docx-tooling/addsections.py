"""Add the two sections the examiner asked for, and their TOC entries.

  1.5  Development Methodology        (examiner: "include reason for selecting SDLC")
  4.5  Coding Practices and Standards (examiner: "no coding practices")

Both are inserted before the section that currently holds that number, so
"1.5 Thesis Outline" becomes 1.6 and "4.5 Version Control and Development
Process" becomes 4.6. No prose anywhere in the document cites a section number,
so the renumber is confined to the two headings and their two TOC entries.

Every figure quoted in the coding-practices section was counted from the
repository rather than asserted: 54 DTO records, 43 classes using constructor
injection, 18 transactional services, 24 validated entry points, 4 domain
exception types, 16 controllers, 17 backend and 7 frontend test files.
"""
import zipfile, re, shutil, os
import xml.dom.minidom as M

SRC = '_docx/out.docx'

H2_PPR = ('<w:pPr><w:pStyle w:val="Heading2"/><w:spacing w:before="300" w:after="160" '
          'w:line="360" w:lineRule="auto"/></w:pPr>')
H2_RPR = '<w:rPr><w:b/><w:bCs/><w:sz w:val="32"/><w:szCs w:val="32"/></w:rPr>'
BD_PPR = ('<w:pPr><w:spacing w:before="100" w:after="100" w:line="360" '
          'w:lineRule="auto"/><w:jc w:val="both"/></w:pPr>')
BD_RPR = '<w:rPr><w:sz w:val="24"/><w:szCs w:val="24"/></w:rPr>'

def esc(s):
    return s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')

def heading(text, anchor, bid):
    return ('<w:p>%s<w:bookmarkStart w:id="%d" w:name="%s"/>'
            '<w:r>%s<w:t xml:space="preserve">%s</w:t></w:r>'
            '<w:bookmarkEnd w:id="%d"/></w:p>' % (H2_PPR, bid, anchor, H2_RPR, esc(text), bid))

def body_para(text):
    return '<w:p>%s<w:r>%s<w:t xml:space="preserve">%s</w:t></w:r></w:p>' % (
        BD_PPR, BD_RPR, esc(text))

def toc_entry(label, anchor, page, indent=360):
    return ('<w:p><w:pPr><w:spacing w:before="60" w:after="60" w:line="320" '
            'w:lineRule="auto"/><w:ind w:left="%d"/></w:pPr>'
            '<w:hyperlink w:anchor="%s" w:history="1">'
            '<w:r><w:rPr><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr><w:t>%s</w:t></w:r>'
            '<w:r><w:ptab w:relativeTo="margin" w:alignment="right" w:leader="dot"/></w:r>'
            '<w:r><w:rPr><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr><w:t>%s</w:t></w:r>'
            '</w:hyperlink></w:p>' % (indent, anchor, esc(label), page))

METHODOLOGY = [
 "The project was developed using the Waterfall software development life cycle, in which "
 "requirements, design, implementation, testing and deployment are completed as distinct "
 "sequential phases, each concluded before the next begins. The model was chosen deliberately, "
 "for four reasons specific to this project.",

 "First, the requirements were known and stable before development began. They were derived from "
 "a written project specification agreed with the supervisor and from direct observation of an "
 "established workflow at a single restaurant. Restaurant service follows a fixed operational "
 "sequence: an order is taken, passed to the kitchen, prepared, served and billed. That sequence "
 "was not expected to change during the project. Waterfall is appropriate precisely where "
 "requirements volatility is low, and the principal risk that iterative models exist to manage "
 "was therefore largely absent.",

 "Second, the project had one developer and one primary stakeholder. The coordination benefits "
 "that Agile methods deliver, such as sprint reviews and continuous renegotiation of scope with a "
 "product owner, presuppose a development team and a stakeholder available for frequent review. "
 "Neither condition held here. Restaurant staff could be made available for one concentrated "
 "evaluation period, not for fortnightly reviews during service hours.",

 "Third, the decisions carrying the highest cost of change had to be settled before construction "
 "started. The database schema, the concurrency control strategy and the transaction boundaries "
 "are structural: retrofitting pessimistic row locking and a single transactional boundary into "
 "an application already built without them would have required rewriting the service layer. "
 "Completing the design phase in full, and recording it in the artefacts presented in Chapter 3, "
 "allowed these decisions to be reasoned about once rather than revised repeatedly.",

 "Fourth, the academic deliverable schedule is itself sequential. The project proposal, the "
 "interim report and the final dissertation fall at fixed points, and each requires a completed "
 "body of analysis or design documentation. The phase outputs of the Waterfall model map directly "
 "onto these deliverables, whereas an iterative process would have produced partial artefacts at "
 "each checkpoint.",

 "The phases map onto this dissertation directly. Requirements elicitation and analysis are "
 "presented in Chapter 2, system design in Chapter 3, construction in Chapter 4, and verification "
 "and validation in Chapter 5. Deployment guidance appears in Appendix A, and the maintenance and "
 "enhancement path in Chapter 6.",

 "Two alternatives were considered and set aside. Prototyping would have suited a project whose "
 "requirements were unclear or whose interface was highly novel, but neither applied: the "
 "operational requirements were documented in advance, and the interface conventions for a "
 "point-of-sale terminal are well established. The Spiral model, being risk-driven and "
 "iteration-heavy, carries an administrative overhead disproportionate to a single-developer "
 "project of this size.",

 "The principal limitation of the choice should be acknowledged. Because user validation occurs "
 "only after implementation is complete, feedback arrives too late to influence the design "
 "cheaply. This occurred during evaluation: kitchen staff identified the absence of an audible "
 "alert on the Kitchen Display as their highest-priority request, and because the sequential "
 "model provides no mechanism to fold that finding back into an earlier phase, it is recorded as "
 "future work in Chapter 6 rather than delivered in the system. An iterative process would have "
 "surfaced the requirement sooner. This is the accepted trade-off of the model, and it was judged "
 "acceptable given the stability of the requirements.",
]

CODING = [
 "A consistent set of conventions was applied across the codebase so that the system can be read "
 "and maintained by a developer other than its author. The conventions are stated here and are "
 "observable throughout the source.",

 "Layer separation is enforced strictly. A request passes from a controller to a service and from "
 "a service to a repository, and no layer reaches past its immediate neighbour. No controller "
 "class references a repository, so database access is reachable only through the service layer. "
 "Entity classes are confined to the data layer and are never returned from a controller: "
 "fifty-four request and response types are declared as Java records in the dto package, and "
 "controllers map service results onto those records before returning them. The persistence model "
 "can therefore change without altering the published API.",

 "Naming follows the established convention of each language. Java classes use PascalCase and "
 "carry a suffix denoting their role, so that a class's layer is evident from its name alone; the "
 "sixteen controllers, twenty-one services and eighteen repositories are named consistently in "
 "this way. Methods and fields use camelCase. Database tables and columns use snake_case and are "
 "mapped to their Java counterparts by JPA, rather than distorting either side to match the other.",

 "Dependencies are supplied by constructor injection rather than field injection. Forty-three "
 "classes declare their collaborators as final fields and rely on Lombok to generate the "
 "constructor, which makes each class's dependencies explicit and allows it to be instantiated "
 "directly in a test. One deliberate exception exists: InventoryAlertService injects a lazy "
 "reference to itself, because a call made on this rather than through the Spring proxy would "
 "bypass the transactional and asynchronous advice that the method depends upon. The exception is "
 "documented at the point of injection together with the reason for it.",

 "Transaction boundaries are declared at the service layer only. Eighteen service classes carry "
 "the @Transactional annotation and no controller does, which keeps the unit of work aligned with "
 "a business operation rather than with an HTTP request. This is what allows the whole of order "
 "submission, comprising table locking, stock deduction, ledger writing and order persistence, to "
 "commit or roll back as a single unit.",

 "Input validation and error handling are centralised rather than repeated. Request bodies are "
 "validated at twenty-four controller entry points using constraints declared on the record types "
 "themselves, so malformed input is rejected before any service method executes. Four "
 "domain-specific exception types express business failures, and a single class annotated "
 "@RestControllerAdvice translates them into HTTP responses. No controller contains "
 "error-formatting code, and every client receives errors in the same shape.",

 "Configuration is externalised. Database credentials, the JWT signing secret, the permitted CORS "
 "origins and the connection pool size are read from application.yml with environment variable "
 "overrides. No credential is written into a source file.",

 "Comments record reasoning rather than restating the code. Where an implementation choice is not "
 "self-evident, particularly along the concurrency-sensitive paths, the comment explains why the "
 "code is written as it is. The recipe deduction routine, for example, records that ingredient "
 "rows are locked in ascending identifier order specifically to prevent the deadlock that arises "
 "when two waiters submit orders sharing the same two ingredients in opposite sequence.",

 "On the frontend the source is organised by concern rather than by page. The api directory holds "
 "the HTTP client and the per-module call functions, context the providers for session and "
 "WebSocket state, hooks the reusable stateful logic, pages the top-level route components, and "
 "components the reusable elements grouped by domain. Logic shared between screens is extracted "
 "into a custom hook rather than duplicated, as with the cart reducer and the STOMP client.",

 "Test code is held to the same standards as production code. Seventeen backend test classes and "
 "seven frontend test files are named after the unit under test and are organised to mirror the "
 "structure they exercise.",
]

z = zipfile.ZipFile(SRC)
xml = z.read('word/document.xml').decode('utf8')
head, body, tail = re.match(r'(.*<w:body>)(.*)(</w:body>.*)', xml, re.S).groups()
paras = re.findall(r'<w:p(?: [^>]*)?>.*?</w:p>|<w:p/>', body, re.S)

def txt(p): return ''.join(re.findall(r'<w:t[^>]*>([^<]*)</w:t>', p)).strip()
def is_h2(p): return 'w:val="Heading2"' in p
def is_toc(p): return 'w:leader="dot"' in p

new = list(paras)
inserts = {}          # index -> xml to place BEFORE that paragraph
renames = []

def plan(old_num, new_num, title, anchor, bid, paras_text, toc_label, page_hint):
    # body heading to renumber
    h = [i for i, p in enumerate(paras) if is_h2(p) and txt(p).startswith(old_num + ' ')]
    assert len(h) == 1, (old_num, h)
    i = h[0]
    renames.append((i, txt(paras[i]), txt(paras[i]).replace(old_num, new_num, 1)))
    inserts[i] = heading(title, anchor, bid) + ''.join(body_para(t) for t in paras_text)
    # toc entry to renumber
    t = [k for k, p in enumerate(paras) if is_toc(p) and txt(p).startswith(old_num + ' ')]
    assert len(t) == 1, (old_num, t)
    k = t[0]
    renames.append((k, txt(paras[k]), None))      # None -> renumber label only
    inserts[k] = toc_entry(toc_label, anchor, page_hint)

plan('1.5', '1.6', '1.5  Development Methodology', '_Rms_sec_methodology', 7100,
     METHODOLOGY, '1.5 Development Methodology', '5')
plan('4.5', '4.6', '4.5  Coding Practices and Standards', '_Rms_sec_codingstd', 7101,
     CODING, '4.5 Coding Practices and Standards', '39')

# apply renumbering in place
for i, old, _ in renames:
    p = new[i]
    m = re.search(r'(<w:t[^>]*>)([^<]*)(</w:t>)', p)
    lbl = m.group(2)
    newlbl = re.sub(r'^1\.5', '1.6', lbl) if lbl.startswith('1.5') else re.sub(r'^4\.5', '4.6', lbl)
    new[i] = p[:m.start()] + m.group(1) + newlbl + m.group(3) + p[m.end():]
    print("  renumbered: %-46r -> %r" % (lbl[:46], newlbl[:46]))

# rebuild with insertions
out, cur = [], 0
for idx, p in enumerate(paras):
    k = body.find(p, cur); assert k >= 0
    out.append(body[cur:k])
    if idx in inserts:
        out.append(inserts[idx])
    out.append(new[idx])
    cur = k + len(p)
out.append(body[cur:])
body = ''.join(out)

print("\n  inserted 1.5 Development Methodology  (%d paragraphs, %d words)"
      % (len(METHODOLOGY), sum(len(t.split()) for t in METHODOLOGY)))
print("  inserted 4.5 Coding Practices and Standards (%d paragraphs, %d words)"
      % (len(CODING), sum(len(t.split()) for t in CODING)))

xml = head + body + tail
M.parseString(xml.encode('utf8'))
shutil.copy(SRC, '_docx/_t.docx')
zin = zipfile.ZipFile('_docx/_t.docx')
zo = zipfile.ZipFile('_docx/_s.docx', 'w', zipfile.ZIP_DEFLATED)
for it in zin.infolist():
    zo.writestr(it, xml.encode('utf8') if it.filename == 'word/document.xml'
                     else zin.read(it.filename))
zo.close(); zin.close()
shutil.move('_docx/_s.docx', SRC); os.remove('_docx/_t.docx')
print("\nwritten; well-formed")
