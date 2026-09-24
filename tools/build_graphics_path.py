#!/usr/bin/env python3
"""Rebuild the pinned AndroidX Path JNI with LOAD and RELRO alignment (Linux/WSL)."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request
import uuid
import zipfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools/qa'))
from check_apk_page_sizes import inspect_elf

def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--ndk',type=Path,required=True,help='Existing Android NDK r25b; no SDK licence is accepted.')
    a=p.parse_args()
    if sys.platform!='linux':p.error('Run under Linux/WSL.')
    if '25.1.8937393' not in (a.ndk/'source.properties').read_text():p.error('Pinned NDK r25b is required.')
    pin=json.loads((ROOT/'config/graphics-path-source.json').read_text())
    out=ROOT/'dist/native-graphics/runs'/(datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ-')+uuid.uuid4().hex[:8])
    out.mkdir(parents=True,exist_ok=False)
    state=dict(outcome='running',runtime_tested=False,inputs=pin,builder_sha256=sha(__file__),libraries={},commands={})
    def save(): (out/'status.json').write_text(json.dumps(state,indent=2)+'\n')
    def fetch(name,url,expected):
        dest=out/name;dest.parent.mkdir(parents=True,exist_ok=True)
        data=urllib.request.urlopen(url,timeout=60).read()
        if hashlib.sha256(data).hexdigest()!=expected:raise ValueError('Input hash mismatch: '+name)
        dest.write_bytes(data);return dest
    def command(label,args):
        result=subprocess.run([str(v) for v in args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
        (out/(label+'.log')).write_bytes(result.stdout)
        state['commands'][label]=dict(args=[str(v) for v in args],exit_code=result.returncode);save()
        if result.returncode:raise RuntimeError(label+' failed')
        return result.stdout.decode()
    save()
    try:
        for item in pin['files']:
            fetch('source/'+item['name'],'https://raw.githubusercontent.com/androidx/androidx/'+pin['commit']+'/graphics/graphics-path/src/main/cpp/'+item['name'],item['sha256'])
        upstream={ext:fetch('upstream.'+ext,'https://dl.google.com/dl/android/maven2/androidx/graphics/graphics-path/1.0.1/graphics-path-1.0.1.'+ext,digest) for ext,digest in pin['upstream'].items()}
        tool=a.ndk/'toolchains/llvm/prebuilt/linux-x86_64/bin'
        state['compiler']=command('compiler',[tool/'clang++','--version'])
        replacements={}
        for abi,target in [('arm64-v8a','aarch64-linux-android28'),('x86_64','x86_64-linux-android28')]:
            library=out/(abi+'.so')
            command('compile-'+abi,[tool/'clang++','--target='+target,'--sysroot='+str(tool.parent/'sysroot'),'-shared','-fPIC','-O2','-std=c++17','-static-libstdc++',
                '-Wl,--version-script='+str(out/'source/libandroidx.graphics.path.map'),'-Wl,-z,max-page-size=16384','-Wl,-z,common-page-size=16384','-Wl,-soname,libandroidx.graphics.path.so',
                *[out/'source'/name for name in ('Conic.cpp','PathIterator.cpp','pathway.cpp')],'-landroid','-o',library])
            command('strip-'+abi,[tool/'llvm-strip','--strip-unneeded',library])
            segments=inspect_elf(library.read_bytes())
            if not all(s['aligned_16k'] for s in segments):raise ValueError('Native alignment failure: '+abi)
            entry='jni/'+abi+'/libandroidx.graphics.path.so'
            with zipfile.ZipFile(upstream['aar']) as z:(out/(abi+'-upstream.so')).write_bytes(z.read(entry))
            def exports(path,label):
                text=command(label,[tool/'llvm-nm','--dynamic','--defined-only',path])
                return sorted(line.split()[-1] for line in text.splitlines() if 'Java_' in line or 'JNI_OnLoad' in line)
            if exports(library,'exports-'+abi)!=exports(out/(abi+'-upstream.so'),'upstream-exports-'+abi):raise ValueError('JNI exports differ: '+abi)
            state['libraries'][abi]=dict(sha256=sha(library),segments=segments)
            replacements[entry]=library.read_bytes()
        name='graphics-path-'+pin['version']
        aar=out/(name+'.aar')
        with zipfile.ZipFile(upstream['aar']) as src,zipfile.ZipFile(aar,'w',zipfile.ZIP_DEFLATED) as dst:
            for entry in sorted(src.namelist()):
                if entry.endswith('/'):continue
                item=zipfile.ZipInfo(entry,(2024,4,24,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED
                dst.writestr(item,replacements.get(entry,src.read(entry)))
            dst.writestr('NOTICE.vds-16k','AndroidX Graphics Path 1.0.1: ARM64/x86_64 JNI rebuilt with NDK r25b.\nSource: '+pin['source_url']+'\nJava classes, resources and 32-bit binaries retained from the official AAR.\nLink flags: max-page-size=16384 and common-page-size=16384. No ELF header patching.\n')
        state.update(outcome='native_build_passed',artifact_sha256=sha(aar));save()
        dest=ROOT/'dist/native-graphics/maven/androidx/graphics/graphics-path'/pin['version'];dest.mkdir(parents=True,exist_ok=True)
        temporary=dest/(name+'.aar.tmp');shutil.copyfile(aar,temporary);os.replace(temporary,dest/(name+'.aar'))
        (dest/(name+'.pom')).write_text(upstream['pom'].read_text().replace('<version>1.0.1</version>','<version>'+pin['version']+'</version>',1))
        shutil.copyfile(out/'status.json',dest/'build-receipt.json')
        print('Built and strictly aligned:',dest)
    except Exception as error:
        state.update(outcome='failed',error=str(error));save();raise
    finally:print('Evidence:',out)

if __name__=='__main__':main()
