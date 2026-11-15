#!/usr/bin/env python3
"""Check version uniqueness in both supported Flyway resource sets."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[2]
db = root / 'xc/xc-domain/src/main/resources/db'

def version(name):
    match = re.fullmatch(r'V([0-9][0-9._]*)__.+\.sql', name)
    if not match:
        raise SystemExit(f'Invalid versioned migration name: {name}')
    parts = [int(x) for x in re.split(r'[._]', match[1])]
    while len(parts) > 1 and parts[-1] == 0:
        parts.pop()
    return tuple(parts)

for profile, folders in {'prod': ('migration', 'production'), 'local': ('migration', 'demo')}.items():
    seen = {}
    for folder in folders:
        for sql in sorted((db / folder).rglob('V*.sql')):
            key = version(sql.name)
            if key in seen:
                raise SystemExit(f'{profile}: duplicate migration version: {seen[key]} and {sql}')
            seen[key] = sql
    print(f'{profile}: {len(seen)} migration versions, no duplicates')
