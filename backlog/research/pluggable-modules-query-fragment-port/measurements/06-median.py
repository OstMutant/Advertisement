# Reads the unaligned output of 05-repeat.sql (phase|round|step|info) and prints, per scenario, the median
# planning+execution ms of each statement and the per-list-page sums: A = 2 lookups + page + count
# (today's code resolves the taxon filter in getFiltered and again in count), A1 = 1 lookup + page + count,
# F/G/B = page + count. Usage: python3 -I 06-median.py <result file>
import re, sys, statistics
from collections import defaultdict

rows = defaultdict(list)
scenario = None
for line in open(sys.argv[1]):
    parts = line.rstrip('\n').split('|')
    if len(parts) < 4:
        continue
    phase, rnd, step, info = parts[0], parts[1], parts[2], parts[3]
    if step.startswith('##### '):
        scenario = step[6:]
        continue
    m = re.match(r'A\.lookup: (\d+) ids, ([\d.]+) ms', step)
    if m:
        rows[(phase, scenario, 'ids')].append(int(m.group(1)))
        rows[(phase, scenario, 'A.lookup')].append(float(m.group(2)))
        continue
    t = re.findall(r'([\d.]+) ms', info)
    if len(t) == 2:
        rows[(phase, scenario, step + '.plan')].append(float(t[0]))
        rows[(phase, scenario, step)].append(float(t[0]) + float(t[1]))

med = {k: statistics.median(v) for k, v in rows.items()}
spread = {k: (min(v), max(v)) for k, v in rows.items()}
keys = sorted({(p, s) for p, s, _ in rows}, key=lambda k: (k[0], k[1]))
print('phase | scenario | ids | A (2 lookups) | A1 (1 lookup) | F exists | G array | B local | F page | F count | A page | A count | A plan page')
for p, s in keys:
    g = lambda step: med.get((p, s, step), float('nan'))
    a_sum = 2 * g('A.lookup') + g('A.page') + g('A.count')
    a1_sum = g('A.lookup') + g('A.page') + g('A.count')
    print(f"{p} | {s} | {int(g('ids'))} | {a_sum:.0f} | {a1_sum:.0f} | {g('F.page') + g('F.count'):.0f} | "
          f"{g('G.page') + g('G.count'):.0f} | {g('B.page') + g('B.count'):.0f} | {g('F.page'):.1f} | {g('F.count'):.1f} | "
          f"{g('A.page'):.1f} | {g('A.count'):.1f} | {g('A.page.plan'):.1f}")
print()
print('min..max of F.page and F.count over the rounds')
for p, s in keys:
    print(p, '|', s, '| F.page', spread.get((p, s, 'F.page')), '| F.count', spread.get((p, s, 'F.count')))
