#!/usr/bin/env python3
"""Copy an external QA fixture into a dedicated debuggable Android test installation."""
import argparse
import os
from pathlib import Path
import subprocess
import tarfile

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--serial',required=True)
p.add_argument('--source',type=Path,required=True)
p.add_argument('--name',choices=('training-fixture','hf-runtime-fixture','long-training-fixture'),required=True)
a=p.parse_args()
if os.environ.get('VDS_ALLOW_TEST_INSTALL')!='1':
    p.error('Set VDS_ALLOW_TEST_INSTALL=1 for a dedicated test device')
if not a.source.is_dir():p.error('Missing fixture directory')
adb=['adb','-s',a.serial]
package='com.unicornwhodev.visiondatasetstudio'
target='files/'+a.name
subprocess.run([*adb,'shell','run-as',package,'mkdir','-p',target],check=True)
uid=int(subprocess.check_output([*adb,'shell','run-as',package,'id','-u']))
gid=int(subprocess.check_output([*adb,'shell','run-as',package,'id','-g']))
def ownership(info):
    info.uid=uid;info.gid=gid;info.uname='';info.gname=''
    info.mode=0o700 if info.isdir() else 0o600
    return info
files=[f for f in sorted(a.source.rglob('*')) if not f.is_symlink() and 'saved-model' not in f.relative_to(a.source).parts]
with subprocess.Popen([*adb,'exec-in','run-as',package,'tar','-x','-f','-','-C',target],stdin=subprocess.PIPE) as proc:
    with tarfile.open(fileobj=proc.stdin,mode='w|') as archive:
        for f in files:
            if f.is_file() or f.is_dir():archive.add(f,arcname=f.relative_to(a.source).as_posix(),recursive=False,filter=ownership)
    proc.stdin.close()
    if proc.wait()!=0:raise RuntimeError('Fixture copy failed')
print(f'Staged {a.name} on {a.serial}; the Android tests validate its contents.')
