"""Generate the Appendix A.4 data dictionary straight from schema.sql.

Fig 3.2 shows only keys and defining columns; this carries every column, its type,
nullability and key role for all 20 tables. Generated rather than hand-written so it
cannot drift from the database.
"""
import re, io, sys

SRC = 'backend/src/main/resources/schema.sql'
OUT = '_docx/appendix_a4_data_dictionary.md'

sql = io.open(SRC, encoding='utf8').read()

tables = []
for m in re.finditer(r'CREATE TABLE IF NOT EXISTS (\w+) \((.*?)\n\) ENGINE', sql, re.S):
    name, body = m.group(1), m.group(2)
    cols, fks, uqs, pk = [], {}, [], None
    pending = None
    for raw in body.splitlines():
        t = raw.strip().rstrip(',')
        if not t or t.startswith('--'):
            continue
        if pending:                                    # REFERENCES line of a split FK
            r = re.match(r'REFERENCES (\w+)\((\w+)\)(?: ON DELETE (.+))?', t)
            if r:
                fks[pending] = (r.group(1), (r.group(3) or 'NO ACTION').strip())
            pending = None
            continue
        if t.upper().startswith('CONSTRAINT'):
            f = re.search(r'FOREIGN KEY \((\w+)\)', t)
            u = re.search(r'UNIQUE \(([^)]+)\)', t)
            if f:
                r = re.search(r'REFERENCES (\w+)\((\w+)\)(?: ON DELETE (.+))?', t)
                if r:
                    fks[f.group(1)] = (r.group(1), (r.group(3) or 'NO ACTION').strip())
                else:
                    pending = f.group(1)
            elif u:
                uqs.append([c.strip() for c in u.group(1).split(',')])
            continue
        c = re.match(r'(\w+)\s+(.+)', t)
        if not c:
            continue
        col, rest = c.group(1), c.group(2)
        if 'PRIMARY KEY' in rest.upper():
            pk = col
        typ = re.match(r"((?:ENUM\([^)]*\))|(?:\w+(?:\([\d,]+\))?))", rest).group(1)
        nn = 'NOT NULL' in rest.upper() or 'PRIMARY KEY' in rest.upper()
        dflt = re.search(r"DEFAULT ([^,]+?)(?:\s+(?:NOT NULL|UNIQUE)|$)", rest, re.I)
        if 'UNIQUE' in rest.upper() and 'PRIMARY KEY' not in rest.upper():
            uqs.append([col])
        cols.append({'name': col, 'type': typ, 'nn': nn,
                     'default': dflt.group(1).strip() if dflt else ''})
    tables.append({'name': name, 'cols': cols, 'fks': fks, 'uqs': uqs, 'pk': pk})

out = []
out.append('# A.4  Database Schema and Data Dictionary\n')
out.append('Every column of the %d tables that make up the RMS database, generated from '
           'the authoritative `schema.sql`. Figure 3.2 shows only the primary key, the '
           'foreign keys and the columns that carry each entity\'s business meaning; the '
           'full definition of every column is given here.\n' % len(tables))

nfk = 0
for t in tables:
    single_uq = {u[0] for u in t['uqs'] if len(u) == 1}
    multi_uq = [u for u in t['uqs'] if len(u) > 1]
    out.append('\n## A.4.%d  %s\n' % (tables.index(t) + 1, t['name']))
    out.append('| Column | Type | Null | Key | Default | References |')
    out.append('|---|---|---|---|---|---|')
    for c in t['cols']:
        key = []
        if c['name'] == t['pk']:
            key.append('PK')
        if c['name'] in t['fks']:
            key.append('FK')
        if c['name'] in single_uq:
            key.append('UQ')
        ref = ''
        if c['name'] in t['fks']:
            tgt, act = t['fks'][c['name']]
            ref = '%s(id) ON DELETE %s' % (tgt, act)
            nfk += 1
        out.append('| `%s` | %s | %s | %s | %s | %s |' % (
            c['name'], c['type'], 'no' if c['nn'] else 'yes',
            ' '.join(key), ('`%s`' % c['default']) if c['default'] else '', ref))
    for u in multi_uq:
        out.append('| | | | UQ | | composite unique (%s) |' % ', '.join(u))

out.append('\n---\n')
out.append('**%d tables · %d columns · %d foreign keys.** Columns marked FK carry the '
           'ON DELETE rule shown; `orders.table_id`, `restaurant_tables.current_order_id` '
           'and `audit_logs.user_id` are deliberately not constrained (an enforced '
           '`orders` / `restaurant_tables` pair would be circular) and so appear here '
           'without a reference.\n'
           % (len(tables), sum(len(t['cols']) for t in tables), nfk))

io.open(OUT, 'w', encoding='utf8').write('\n'.join(out))
print('%s: %d tables, %d columns, %d FKs'
      % (OUT, len(tables), sum(len(t['cols']) for t in tables), nfk))
