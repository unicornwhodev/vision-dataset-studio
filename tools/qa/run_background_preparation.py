#!/usr/bin/env python3
"""Prepare synthetic data with Debug instrumentation; never counts as background qualification."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time
from run_device_qualification import APP_ID, parse_instrumentation

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--serial',required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--images',type=int,default=256)
    a=p.parse_args()
    if os.environ.get('VDS_ALLOW_TEST_INSTALL')!='1':p.error('Dedicated QA installation required.')
    a.output.mkdir(parents=True,exist_ok=False)
    adb=['adb','-s',a.serial]
    state=dict(outcome='running',background_test_executed=False)
    def save(): (a.output/'status.json').write_text(json.dumps(state,indent=2)+'\n')
    save()
    try:
        with (a.output/'instrumentation.txt').open('wb') as log:
            command=[*adb,'shell','am','instrument','-w','-r','-e','class',APP_ID+'.BackgroundTrainingPreparationTest','-e','prepareLongTraining','true','-e','trainingImages',str(a.images),APP_ID+'.test/androidx.test.runner.AndroidJUnitRunner']
            process=subprocess.Popen(command,stdout=log,stderr=subprocess.STDOUT)
            try:
                deadline=time.monotonic()+180;visible=False
                while process.poll() is None:
                    if time.monotonic()>deadline:raise TimeoutError('Preparation exceeded 180 seconds.')
                    output=(a.output/'instrumentation.txt').read_text(encoding='utf-8',errors='replace')
                    if not visible and 'INSTRUMENTATION_STATUS_CODE: 1' in output:
                        subprocess.run([*adb,'shell','am','start','-f','0x20000000','-n',APP_ID+'/.qa.QaPresenceActivity'],capture_output=True,check=True)
                        visible=True
                    time.sleep(.5)
            finally:
                if process.poll() is None:process.kill()
                subprocess.run([*adb,'shell','am','force-stop',APP_ID],capture_output=True)
        state['tests']=parse_instrumentation((a.output/'instrumentation.txt').read_text(encoding='utf-8',errors='replace'))
        if not state['tests']['complete']:raise RuntimeError('Preparation instrumentation did not pass.')
        data=subprocess.check_output([*adb,'exec-out','run-as',APP_ID,'cat','files/long-training-fixture/preparation.json'])
        state['preparation']=json.loads(data);state['outcome']='prepared_only'
        print(json.dumps(state['preparation'],indent=2))
    except Exception as error:
        state.update(outcome='failed',error=str(error));raise
    finally:save()

if __name__=='__main__':main()
