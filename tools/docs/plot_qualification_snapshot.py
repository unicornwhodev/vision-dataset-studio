#!/usr/bin/env python3
"""Draw documentation figures from recorded public Android results, without running models."""
import argparse
import json
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch


def save_figure(fig, output, stem):
    fig.savefig(output/f'{stem}.png',dpi=160)
    svg=output/f'{stem}.svg'
    fig.savefig(svg,metadata={'Date':None})
    svg.write_text('\n'.join(line.rstrip() for line in svg.read_text(encoding='utf-8').splitlines())+'\n',encoding='utf-8',newline='\n')


def render(evidence, output, total):
    rows=json.loads(evidence.read_text(encoding='utf-8'))['results']
    assert all(r['case'].startswith('Charlbi-') for r in rows)
    assert len({r['case'] for r in rows})==len(rows)
    passed=sum(r['passed'] for r in rows)
    timeouts=sum(r['timed_out'] for r in rows)
    failed=sum(not r['passed'] and not r['timed_out'] for r in rows)
    pending=total-len(rows)
    learned=sum(r['passed'] and r.get('android',{}).get('training')=='passed' for r in rows)
    assert min(pending,passed,timeouts,failed)>=0
    output.mkdir(parents=True,exist_ok=True)
    plt.rcParams.update({'font.family':'DejaVu Sans','svg.fonttype':'none','svg.hashsalt':'vds-docs-20260923','savefig.facecolor':'#0d131c'})
    fig=plt.figure(figsize=(12,3.7),facecolor='#0d131c')
    fig.text(.055,.86,'ANDROID MODEL QUALIFICATION',color='#8da4bc',size=11,weight='bold')
    fig.text(.055,.65,f'{passed} / {total}',color='#eaf3ff',size=36,weight='bold')
    fig.text(.28,.67,'variants passed',color='#eaf3ff',size=20)
    fig.text(.28,.56,f'{learned} include train / save / restore / resume',color='#95dfda',size=12)
    ax=fig.add_axes([.055,.35,.89,.105]); left=0
    for count,color in ((passed,'#70d9d0'),(timeouts,'#edb761'),(failed,'#e4778e'),(pending,'#34445b')):
        if count: ax.barh(0,count,left=left,color=color,height=.9); left+=count
    ax.set_xlim(0,total); ax.set_ylim(-.6,.6); ax.axis('off')
    fig.text(.055,.24,f'{passed} passed     {timeouts} timed out     {pending} not run     {failed} failed',color='#d9e4f4',size=12)
    fig.text(.055,.095,'Recorded 2026-09-22 · API 28 x86_64 emulator · functional checks, not phone speed or accuracy',color='#8da4bc',size=10)
    save_figure(fig,output,'qualification-20260922')
    plt.close(fig)

    fig,ax=plt.subplots(figsize=(12,4),facecolor='#0d131c'); ax.set_facecolor('#0d131c'); ax.axis('off'); ax.set_xlim(0,12); ax.set_ylim(0,4)
    def box(x,y,title,detail,color):
        ax.add_patch(FancyBboxPatch((x,y),3.1,1.1,boxstyle='round,pad=0.12,rounding_size=0.12',facecolor='#162231',edgecolor=color,linewidth=1.5))
        ax.text(x+.2,y+.73,title,color='#edf5ff',size=15,weight='bold')
        ax.text(x+.2,y+.32,detail,color='#a9bbd1',size=10)
    ax.text(.3,3.55,'KEEP THE ORIGINAL. CONTINUE THE LEARNED VERSION.',color='#eaf3ff',size=15,weight='bold')
    box(.35,1.35,'Original','Graph + profile preserved','#70d9d0')
    box(4.4,1.35,'Learned · generation 1','Separate graph + checkpoint','#ab96f5')
    box(8.45,1.35,'Learned · generation 2','Latest validated weights restored','#ab96f5')
    for a,b,label in ((3.6,4.2,'First training'),(7.65,8.25,'Next batch')):
        ax.annotate('',xy=(b,1.91),xytext=(a,1.91),arrowprops={'arrowstyle':'->','color':'#70d9d0','lw':1.8})
        ax.text((a+b)/2,2.8,label,color='#a9bbd1',size=10,ha='center')
    ax.text(.55,.7,'The original remains available.',color='#70d9d0',size=11)
    ax.text(4.55,.7,'Inference activation is an explicit choice.',color='#bbaafb',size=11)
    ax.text(.55,.15,'Vision Dataset Studio rc5 · conceptual flow; evaluation and checkpoint verification precede promotion',color='#8da4bc',size=9)
    fig.subplots_adjust(left=.01,right=.99,top=.98,bottom=.02)
    save_figure(fig,output,'model-lineage')
    plt.close(fig)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('evidence',type=Path);p.add_argument('output',type=Path);p.add_argument('--total',type=int,default=25)
    a=p.parse_args();render(a.evidence,a.output,a.total)
