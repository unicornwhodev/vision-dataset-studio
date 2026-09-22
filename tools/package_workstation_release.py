#!/usr/bin/env python3
"""Package a source-matched workstation prerelease, without inventing CI/model results."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile
from build_android import weight_inventory

ROOT=Path(__file__).resolve().parents[1]
def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT)
def sha(path):
 with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def compiled(name):
 return name.startswith(('app/','gradle/')) and name!='app/.gitignore' or name in ('build.gradle.kts','settings.gradle.kts','gradle.properties','tools/build_android.py','tools/gradle_bootstrap.py')
def source_hashes(value):
 """Accept explicit path/hash records and historical manifests without losing duplicates."""
 if isinstance(value,dict):return value
 if not isinstance(value,list):raise ValueError('Invalid source manifest')
 result={}
 for row in value:
  name=row['path'];digest=row['sha256']
  if name in result:raise ValueError('Duplicate source path: '+name)
  if not isinstance(digest,str) or not re.fullmatch(r'[0-9a-f]{64}',digest):raise ValueError('Invalid source digest')
  result[name]=digest
 return result
def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--build-dir',type=Path,required=True)
 p.add_argument('--evidence',type=Path,required=True)
 p.add_argument('--version',required=True)
 a=p.parse_args()
 if not re.fullmatch(r'\d+\.\d+\.\d+-rc\d+',a.version):p.error('An explicit release-candidate version is required')
 if git('status','--porcelain').strip():raise RuntimeError('Commit the reviewed tree before packaging')
 evidence=a.evidence.resolve();folder=a.build_dir.resolve()
 evidence.relative_to(ROOT)
 source=json.loads((evidence/'source-manifest.json').read_text());head=git('rev-parse','HEAD').decode().strip()
 source['files']=source_hashes(source['files'])
 names=[n for n in git('ls-files','-z').decode().split('\0') if n]
 if set(source['files'])!={n for n in names if compiled(n)}:raise RuntimeError('Incomplete compiled-source manifest')
 for n,h in source['files'].items():
  if sha(ROOT/n)!=h or hashlib.sha256(git('show',source['source_commit']+':'+n)).hexdigest()!=h:raise RuntimeError('Compiled source differs: '+n)
 receipt=json.loads((folder/'status.json').read_text())
 if not receipt.get('all_build_checks_passed') or receipt.get('outcome')!='build_checks_passed':raise RuntimeError('Build checks failed')
 qualification=json.loads((evidence/'qualification.json').read_text())
 if qualification['build_run']!=receipt['run_id']:raise RuntimeError('Device evidence belongs to another build')
 device=json.loads((evidence/'final-device/build-receipt.json').read_text())
 if device['run_id']!=receipt['run_id'] or device['artifacts']!=receipt['artifacts']:raise RuntimeError('Device APKs differ')
 text=(evidence/'final-device/instrumentation.txt').read_text()
 expected=qualification['android_tests_passed']
 if not re.search(rf'OK \({expected} tests?\)',text) or re.search(r'INSTRUMENTATION_STATUS_CODE: -[1234]|FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=',text):raise RuntimeError('Android suite did not pass without skips')
 apks=[]
 for k in ('app','tests'):
  info=receipt['artifacts'][k];name=info['file']
  if Path(name).name!=name:raise RuntimeError('Unsafe APK name')
  path=folder/name
  if sha(path)!=info['sha256'] or path.stat().st_size!=info['bytes']:raise RuntimeError('APK bytes mismatch')
  apks.append(path)
 if weight_inventory(apks[0])['weight_files']:raise RuntimeError('Embedded model weights')
 package={'kind':'workstation-debug-prerelease','version':a.version,'stable_release':False,'source_commit':head,'compiled_source_commit':source['source_commit'],'compiled_files_verified':len(source['files']),'build_run':receipt['run_id'],'artifacts':receipt['artifacts'],'qualification':qualification,'ci_executed':False,'runnable_container':False}
 out=ROOT/'dist'/('release-'+a.version);out.mkdir(parents=True,exist_ok=True)
 (out/'PACKAGE.json').write_text(json.dumps(package,indent=2)+'\n')
 dest=out/('vision-dataset-studio-'+a.version+'-qualification.zip')
 with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED,compresslevel=3) as z:
  z.write(out/'PACKAGE.json','PACKAGE.json');z.write(folder/'status.json','BUILD_STATUS.json')
  for apk in apks:z.write(apk,apk.name)
  for name in names:
   if name.startswith(('docs/',evidence.relative_to(ROOT).as_posix()+'/')) or name in ('README.md','README.en.md','LICENSE','NOTICE','LICENSING_STATUS.md','KNOWN_LIMITATIONS.md','QUALIFICATION_STATUS.json','TEST_REPORT.md'):
    z.write(ROOT/name,name)
 target=out/'vision-dataset-studio.apk'
 if not target.exists():target.hardlink_to(apks[0])
 if sha(target)!=receipt['artifacts']['app']['sha256']:raise RuntimeError('Existing release APK differs')
 (out/'SHA256SUMS').write_text(''.join(sha(f)+'  '+f.name+'\n' for f in (target,dest,out/'PACKAGE.json')))
 print(dest)
if __name__=='__main__':main()
