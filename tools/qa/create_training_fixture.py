#!/usr/bin/env python3
"""Compile an untrained, tiny image model for Android training QA. No host training.
Run only in the workstation's isolated TensorFlow conversion environment.
The internal visual dense layer AND output layer are mutable model variables.
"""
import argparse,json
from pathlib import Path
import numpy as np
import tensorflow as tf

class TrainableVisionFixture(tf.Module):
    def __init__(self):
        super().__init__();rng=np.random.default_rng(19)
        self.w1=tf.Variable(rng.normal(0,.04,(192,12)).astype('float32'),name='visual_kernel')
        self.b1=tf.Variable(np.full(12,.15,np.float32),name='visual_bias')
        self.w2=tf.Variable(rng.normal(0,.04,(12,2)).astype('float32'),name='classifier_kernel')
        self.b2=tf.Variable(np.zeros(2,np.float32),name='classifier_bias')
    def forward(self,x):
        flat=tf.reshape(tf.nn.avg_pool2d(x,ksize=4,strides=4,padding='VALID'),[1,192])
        hidden=tf.nn.relu(flat@self.w1+self.b1)
        logits=hidden@self.w2+self.b2
        return flat,hidden,logits
    @tf.function(input_signature=[tf.TensorSpec([1,32,32,3],tf.float32,name='x')])
    def infer(self,x):return {'scores':tf.nn.softmax(self.forward(x)[2])}
    @tf.function(input_signature=[tf.TensorSpec([1,32,32,3],tf.float32,name='x'),tf.TensorSpec([1,2],tf.float32,name='y'),tf.TensorSpec([],tf.float32,name='learning_rate')])
    def train(self,x,y,learning_rate):
        flat,hidden,logits=self.forward(x);scores=tf.nn.softmax(logits)
        loss=-tf.reduce_sum(y*tf.math.log(tf.maximum(scores,1e-7)))
        dz=scores-y
        dw2=tf.transpose(hidden)@dz;db2=tf.reduce_sum(dz,axis=0)
        dh=(dz@tf.transpose(self.w2))*tf.cast(hidden>0,tf.float32)
        dw1=tf.transpose(flat)@dh;db1=tf.reduce_sum(dh,axis=0)
        self.w1.assign_sub(learning_rate*dw1);self.b1.assign_sub(learning_rate*db1)
        self.w2.assign_sub(learning_rate*dw2);self.b2.assign_sub(learning_rate*db2)
        return {'loss':loss}
    @tf.function(input_signature=[tf.TensorSpec([],tf.string,name='checkpoint_path')])
    def save(self,checkpoint_path):
        tf.raw_ops.Save(filename=checkpoint_path,tensor_names=['visual_kernel','visual_bias','classifier_kernel','classifier_bias'],data=[self.w1,self.b1,self.w2,self.b2])
        return {'checkpoint_path':checkpoint_path}
    @tf.function(input_signature=[tf.TensorSpec([],tf.string,name='checkpoint_path')])
    def restore(self,checkpoint_path):
        for name,var in [('visual_kernel',self.w1),('visual_bias',self.b1),('classifier_kernel',self.w2),('classifier_bias',self.b2)]:
            var.assign(tf.raw_ops.Restore(file_pattern=checkpoint_path,tensor_name=name,dt=tf.float32))
        return {'restored':tf.constant(1)}
    @tf.function(input_signature=[tf.TensorSpec([],tf.float32,name="probe")])
    def weights(self,probe):return {'visual_kernel':self.w1.read_value()+probe,'classifier_kernel':self.w2.read_value()+probe}

p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);a=p.parse_args()
a.output.mkdir(parents=True,exist_ok=True);model=TrainableVisionFixture()
signatures={name:getattr(model,name).get_concrete_function() for name in ['train','infer','save','restore','weights']}
tf.saved_model.save(model,str(a.output/'saved-model'),signatures=signatures)
converter=tf.lite.TFLiteConverter.from_saved_model(str(a.output/'saved-model'))
converter.target_spec.supported_ops=[tf.lite.OpsSet.TFLITE_BUILTINS,tf.lite.OpsSet.SELECT_TF_OPS]
converter.experimental_enable_resource_variables=True
(a.output/'trainable-vision-fixture.tflite').write_bytes(converter.convert())
(a.output/'model_config.json').write_text(json.dumps(dict(task='classification',adapter='classification',inputWidth=32,inputHeight=32,inputLayout='NHWC',inputType='FLOAT32',mean=0.,std=255.,resizeMode='stretch',labels=['rouge','bleu'],threshold=.0,topK=2,training=dict(targetEncoding='one_hot',targetShape=[1,2],learningRateInput='learning_rate',inferOutputs=['scores'],weightProbeSignature='weights',weightProbeOutput='visual_kernel',scope='internal_visual_and_output_layers')),indent=2))
print('Compiled untrained fixture; no optimizer step executed on host.')
