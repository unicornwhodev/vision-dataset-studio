#!/usr/bin/env python3
"""Render evidence-only figures. Python 3 + Matplotlib; no models or network used."""
import argparse,json,math
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np
BG='#101419'; PANEL='#192129'; FG='#ecf2f8'; MUTED='#9eabb8'; CYAN='#77dce3'; VIOLET='#b59af5'; RED='#fb997e'; GRID='#33404c'
plt.rcParams.update({'figure.facecolor':BG,'axes.facecolor':BG,'savefig.facecolor':BG,'text.color':FG,'axes.labelcolor':MUTED,'xtick.color':MUTED,'ytick.color':FG,'axes.edgecolor':GRID,'font.family':'DejaVu Sans','font.size':11,'svg.fonttype':'none','svg.hashsalt':'vds-evidence-20260921'})
def finish(fig,out,name,title,subtitle,footer):
 fig.suptitle(title,x=.06,y=.97,ha='left',fontsize=21,fontweight='bold')
 fig.text(.06,.91,subtitle,ha='left',va='top',fontsize=11,color=MUTED)
 fig.text(.06,.025,footer,fontsize=9,color=MUTED,va='bottom')
 for suffix in ('png','svg'):fig.savefig(out/(name+'.'+suffix),dpi=160,metadata={'Date':None} if suffix=='svg' else None)
 svg=out/(name+'.svg');svg.write_text('\n'.join(line.rstrip() for line in svg.read_text().splitlines())+'\n')
 plt.close(fig)
def loss_panels(rows,out,name,title,subtitle,footer,left='Initial',right='Last reported'):
 cols=min(3,len(rows));nr=math.ceil(len(rows)/cols)
 fig,axs=plt.subplots(nr,cols,figsize=(13,max(5,2.6*nr+2)),squeeze=False)
 fig.subplots_adjust(left=.08,right=.96,top=.79 if nr<3 else .84,bottom=.13,hspace=.8,wspace=.38)
 for ax,row in zip(axs.flat,rows):
  vals=[row['initial'],row['final']]
  ax.bar([0,1],vals,width=.46,color=[VIOLET,CYAN]);ax.set_xticks([0,1],[left,right]);ax.set_ylim(0,max(vals)*1.4 if max(vals)>0 else 1)
  ax.set_title(row['label'],loc='left',fontsize=11,pad=10,fontweight='bold')
  for i,v in enumerate(vals):ax.text(i,v+ax.get_ylim()[1]*.04,f'{v:.6g}',ha='center',fontsize=10)
  ax.spines[['top','right']].set_visible(False);ax.set_ylabel('Loss');ax.tick_params(axis='x',labelsize=9)
 for ax in list(axs.flat)[len(rows):]:ax.axis('off')
 finish(fig,out,name,title,subtitle,footer)
def render(d,out):
 out.mkdir(parents=True,exist_ok=True)
 foot=d['footer']
 if 'coverage' in d:
  c=d['coverage'];fig,ax=plt.subplots(figsize=(13,5));ax.axis('off')
  for x,k,col in [(.08,'passed',CYAN),(.39,'failed',RED),(.70,'pending',MUTED)]:
   fig.text(x,.48,str(c[k]),fontsize=70,fontweight='bold',color=col)
   fig.text(x,.39,{'passed':'PASS / Réussis','failed':'FAIL / Échec','pending':'PENDING / À tester'}[k],fontsize=14)
  finish(fig,out,'android-coverage','Android app · qualification coverage',f"{c['total']} conversions • {c['trainable']} trainable variants • snapshot 20 Sep 2026",foot)
 if d.get('android'):
  rows=d['android'];learn=[r for r in rows if 'training_loss' in r]
  loss_panels([{'label':r['label'],'initial':r['training_loss'],'final':r['resumed_loss']} for r in learn],out,'android-learning','Android app · two optimizer steps','Synthetic supervision • separate scales • heads/adapters only; visual backbones frozen',foot+'\nLoss values are not accuracy or generalization metrics. No cross-model ranking.',left='Step 1',right='Resumed step 2')
  fig,ax=plt.subplots(figsize=(13,6));fig.subplots_adjust(left=.30,right=.90,top=.76,bottom=.23)
  vals=[r['elapsed_ms']/1000 for r in rows];ys=range(len(rows));ax.barh(ys,vals,color=[VIOLET if 'training_loss' in r else CYAN for r in rows],height=.53)
  ax.set_yticks(list(ys),[r['label'] for r in rows]);ax.invert_yaxis();ax.set_xlim(0,max(vals)*1.22);ax.spines[['top','right','left']].set_visible(False)
  ax.set_xlabel('Whole Android test duration (seconds)');ax.xaxis.grid(True,alpha=.2);ax.set_axisbelow(True)
  for y,v in zip(ys,vals):ax.text(v+max(vals)*.02,y,f'{v:.3f} s',va='center',fontsize=11)
  finish(fig,out,'android-test-duration','Android app · complete test duration','Single execution per conversion • includes load, infer and, when exposed, train/save/restore/resume',foot+'\nNOT inference latency, FPS or a phone benchmark. Test workloads differ; do not rank model speed.')
  fig,ax=plt.subplots(figsize=(13,5.6));ax.axis('off');fig.subplots_adjust(top=.72,bottom=.2)
  data=[[r['label'],f"{r['output_max_change']:.8f}",str(r['restore_max_difference']),str(r['optimizer_steps']), 'PASS' if r['application_checkpoint_loaded'] else 'FAIL'] for r in learn]
  table=ax.table(cellText=data,colLabels=['Conversion','Output delta','Restore delta','Steps','App reload'],cellLoc='center',colWidths=[.37,.17,.16,.12,.16],loc='center');table.auto_set_font_size(False);table.set_fontsize(11);table.scale(1,2.05)
  for (i,j),cell in table.get_celld().items():cell.set_facecolor(PANEL if i else GRID);cell.set_edgecolor(BG);cell.set_text_props(color=FG if j==0 else CYAN)
  finish(fig,out,'android-checkpoints','Android app · checkpoint round trip','Fresh interpreter restore • resumed optimization • application checkpoint reload',foot+'\nOutput deltas are tensor changes, not comparable quality scores. Exact restore applies to this fixture.')
 if d.get('host_learning'):
  loss_panels(d['host_learning'],out,'host-learning','Host CPU · synthetic learning checks','Recorded initial/final endpoints • different objectives and scales • not an accuracy benchmark',foot+'\nNo interpolation. Repeated-example checks are not held-out evaluation. Visual backbones remain frozen.')
 if d.get('matrix'):
  m=d['matrix'];fig,ax=plt.subplots(figsize=(14,6.5));ax.axis('off');fig.subplots_adjust(top=.72,bottom=.17)
  table=ax.table(cellText=m['rows'],colLabels=m['columns'],cellLoc='center',colWidths=[.26]+[.123]*(len(m['columns'])-1),loc='center');table.auto_set_font_size(False);table.set_fontsize(10);table.scale(1,2)
  for (i,j),cell in table.get_celld().items():
   cell.set_facecolor(PANEL if i else GRID);cell.set_edgecolor(BG);v=cell.get_text().get_text();cell.set_text_props(color=CYAN if v=='PASS' else (FG if j==0 or i==0 else MUTED))
  finish(fig,out,'qualification-matrix','Qualification · separate execution contexts','Host conversion tests, standalone SDK and application tests are independent evidence',foot+'\nPENDING is unqualified, not a failure. N/A means the graph does not expose training. No physical ARM test.')
 if d.get('parity'):
  rows=d['parity'];fig,axs=plt.subplots(1,len(rows),figsize=(14,5.8),squeeze=False);fig.subplots_adjust(left=.07,right=.97,top=.70,bottom=.25,wspace=.7)
  for ax,r in zip(axs.flat,rows):
   vals=r['errors'];ax.scatter(range(1,len(vals)+1),vals,c=CYAN,s=40);ax.set_yscale('log');ax.set_title(r['label'],fontsize=10,fontweight='bold',pad=15);ax.set_xticks(range(1,len(vals)+1));ax.set_xlabel('Reported item',fontsize=9);ax.tick_params(labelsize=8);ax.spines[['top','right']].set_visible(False);ax.yaxis.grid(True,alpha=.2)
  axs[0,0].set_ylabel('Maximum absolute error (log scale)')
  finish(fig,out,'conversion-parity','Conversion · numerical fidelity','Per-output / per-shape errors against the source • each panel follows its own report',foot+'\nDifferent output units and protocols: not a model-quality ranking. Acceptance uses atol + rtol × |reference|.')
 if d.get('sdk'):
  loss_panels(d['sdk']['rows'],out,'android-sdk','Standalone Android SDK · recorded loss endpoints',d['sdk']['subtitle'],foot+'\nSeparate from app integration. Exact restore and optimizer resume reported PASS; no accuracy claim.')
def main():
 p=argparse.ArgumentParser();p.add_argument('data',type=Path);p.add_argument('output',type=Path);a=p.parse_args();render(json.loads(a.data.read_text()),a.output)
if __name__=='__main__':main()
