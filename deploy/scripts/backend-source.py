#!/usr/bin/env python3
"""Package a backend source allowlist and safely unpack it before a cloud build."""
import argparse
import hashlib
import io
from pathlib import Path, PurePosixPath
import re
import shutil
import tarfile
import tempfile

MODULES = ('xc-common', 'xc-domain', 'xc-api', 'xc-admin')
FIXED = {'pom.xml', 'Dockerfile', 'maven-settings.xml', 'source-revision.txt'} | {m + '/pom.xml' for m in MODULES}


def allowed(name):
    p = PurePosixPath(name)
    if p.is_absolute() or '..' in p.parts or str(p) != name:
        return False
    if any(part.startswith('.env') or part in {'.git', 'target', 'node_modules', '__pycache__'} for part in p.parts):
        return False
    if p.name.startswith('application-local.'):
        return False
    return name in FIXED or (len(p.parts) >= 4 and p.parts[0] in MODULES and p.parts[1:3] == ('src', 'main'))


def validate_tag(tag):
    if not re.fullmatch(r'[0-9a-f]{40}', tag):
        raise ValueError('Expected full source revision SHA')


def pack(root, output, tag):
    validate_tag(tag)
    root, output = Path(root), Path(output)
    paths = {root / name for name in FIXED - {'source-revision.txt'}}
    for module in MODULES:
        base = root / module / 'src' / 'main'
        if any(p.is_symlink() for p in (base, base.parent, base.parent.parent)):
            raise ValueError('Symlinked source directory')
        for p in base.rglob('*'):
            if p.is_symlink():
                raise ValueError('Symlinked source input')
            if p.is_file() and allowed(p.relative_to(root).as_posix()):
                paths.add(p)
    output.mkdir(parents=True, exist_ok=True)
    archive = output / 'source.tar.gz'
    with tarfile.open(archive, 'w:gz') as tar:
        for p in sorted(paths):
            if p.is_symlink() or not p.is_file():
                raise ValueError(f'Missing or symlinked source input: {p.name}')
            tar.add(p, arcname=p.relative_to(root).as_posix(), recursive=False)
        revision = (tag + '\n').encode()
        info = tarfile.TarInfo('source-revision.txt')
        info.size = len(revision)
        tar.addfile(info, io.BytesIO(revision))
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    (output / 'source.tar.gz.sha256').write_text(digest + '  source.tar.gz\n')
    return archive


def unpack(release, tag):
    validate_tag(tag)
    release = Path(release)
    archive = release / 'source.tar.gz'
    checksum = (release / 'source.tar.gz.sha256').read_text().split()
    if len(checksum) != 2 or checksum[1] != 'source.tar.gz' or hashlib.sha256(archive.read_bytes()).hexdigest() != checksum[0]:
        raise ValueError('Source checksum mismatch')
    with tarfile.open(archive, 'r:gz') as tar:
        members = tar.getmembers()
        if len(members) > 10000 or sum(m.size for m in members) > 20 * 1024**2:
            raise ValueError('Source archive exceeds limits')
        names = set()
        for m in members:
            if not m.isfile() or not allowed(m.name) or m.name in names:
                raise ValueError('Unsafe or duplicate source archive member')
            names.add(m.name)
        if not FIXED.issubset(names):
            raise ValueError('Missing required build inputs')
        if tar.extractfile('source-revision.txt').read().decode().strip() != tag:
            raise ValueError('Source revision mismatch')
        target = Path(tempfile.mkdtemp(prefix='source-', dir=release))
        for m in members:
            p = target / m.name
            p.parent.mkdir(parents=True, exist_ok=True)
            with tar.extractfile(m) as source, p.open('xb') as dest:
                shutil.copyfileobj(source, dest)
    return target


def check_resources(root, meminfo=Path('/proc/meminfo')):
    values = dict(line.split(':', 1) for line in meminfo.read_text().splitlines())
    if int(values['MemAvailable'].split()[0]) < 1536 * 1024:
        raise ValueError('Cloud build requires 1536 MiB available RAM; running services unchanged')
    if shutil.disk_usage(root).free < 4 * 1024**3:
        raise ValueError('Cloud build requires 4 GiB free disk; running services unchanged')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['pack', 'unpack', 'preflight'])
    parser.add_argument('path', type=Path)
    parser.add_argument('tag', nargs='?')
    args = parser.parse_args()
    if args.mode == 'pack':
        archive = pack(Path(__file__).resolve().parents[2] / 'xc', args.path, args.tag)
        print(f'Source release: {archive.stat().st_size:,} bytes')
    elif args.mode == 'unpack':
        print(unpack(args.path, args.tag))
    else:
        check_resources(args.path)
        print('Cloud build resources available')
