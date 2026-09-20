import com.unicornwhodev.visiondatasetstudio.core.workflow.*
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import com.unicornwhodev.visiondatasetstudio.domain.validation.AnnotationReview
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.random.Random

private var count=0
private fun test(name:String,block:()->Unit){block();count++;println("PASS $name")}
private fun near(a:Float,b:Float,e:Float=1e-5f){check(abs(a-b)<e){"$a != $b"}}
private fun fails(block:()->Unit){check(runCatching(block).isFailure){"Expected explicit refusal"}}
private fun tensor(vararg shape:Int,values:FloatArray)=TensorValues(shape.toList(),values)
private fun decode(c:ModelConfig,values:FloatArray,shape:List<Int>,transform:InputTransform=InputTransform.create(640,640,c.inputWidth,c.inputHeight,"stretch"))=ModelAdapters.decode(mapOf(c.outputIndex to TensorValues(shape,values)),c,transform)

fun main(){
    test("model contract version rejects unknown schemas") { fails { ModelContract.validate(ModelConfig(labels=listOf("a"),schemaVersion=2)) } }
    test("source identifiers preserve exact strings and safe integers") { check(SourceIdentity.string("9007199254740993")=="9007199254740993");check(SourceIdentity.string(42.0)=="42");check(SourceIdentity.string(42L)=="42");check(SourceIdentity.string(null)==null) }
    test("source identifiers refuse lossy doubles, fractions and objects") { fails { SourceIdentity.string(9007199254740992.0) };fails { SourceIdentity.string(1.25) };fails { SourceIdentity.string(mapOf("id" to 1)) };fails { SourceIdentity.string(Double.NaN) } }

    test("batch size differs from HTTP page size: 1/99/100/101/500/1000") { for(n in listOf(1,99,100,101,500,1000)){val pages=ProcessingSettings.pageSizes(n);check(pages.sum()==n && pages.all{it in 1..100})};check(ProcessingSettings.pageSizes(500)==List(5){100}) }
    test("batch boundaries and revisions fail closed"){fails{ProcessingSettings(batchSize=0).validate()};fails{ProcessingSettings.pageSizes(1001)};fails{ProcessingSettings(destBranch="../main").validate()};check(ProcessingSettings.validRevision("review/stage-2"))}
    test("manifest/output paths reject traversal, absolute, encoded and empty segments"){for(p in listOf("../a","/a","a//b","a/../b","a%2fb","a\\b","a?x","a#x","a:x","."))check(!ProcessingSettings.safeRelativePath(p));check(ProcessingSettings.safeRelativePath("shards/metadata.jsonl"))}
    test("network and storage bounds enforced"){fails{ProcessingSettings(downloadConcurrency=5).validate()};fails{ProcessingSettings(reserveFreeMb=1).validate()};fails{ProcessingSettings(retryCount=6).validate()}}
    test("every offered LiteRT template validates"){for(id in ModelPresets.names.keys)ModelContract.validate(ModelPresets.create(id,listOf("a","b")))}
    test("unsupported runtime/backbone/shape contracts rejected"){fails{ModelContract.validate(ModelConfig(adapter="dinov3",labels=listOf("a")))};fails{ModelContract.validate(ModelConfig(runtime="npu",labels=listOf("a")))};fails{ModelContract.validate(ModelConfig(labels=listOf("a"),inputLayout="CHWN"))} }
    test("zero std, invalid class list and INT8 raw rejected"){fails{ModelContract.validate(ModelConfig(labels=listOf("a"),std=0f))};fails{ModelContract.validate(ModelConfig(labels=listOf("a","a")))};fails{ModelContract.validate(ModelConfig(labels=listOf("a"),inputType="INT8"))} }
    test("tensor extent matches its values"){fails{TensorValues(listOf(1,2),floatArrayOf(1f))};fails{TensorValues(listOf(0),floatArrayOf())}}
    test("letterbox exact geometry for landscape"){val t=InputTransform.create(640,320,640,640,"letterbox");check(t.top==160 && t.fittedHeight==320);near(t.point(320f,320f,false).second,.5f);near(t.point(.5f,.5f,true).first,.5f)}
    test("center crop inverts to original frame"){val t=InputTransform.create(640,320,320,320,"center_crop");near(t.point(0f,0f,true).first,.25f);near(t.point(1f,1f,true).first,.75f)}
    test("1500 randomized transform inverse tests including odd padding"){val r=Random(778);repeat(1500){val iw=r.nextInt(10,4000);val ih=r.nextInt(10,4000);val w=r.nextInt(32,1025);val h=r.nextInt(32,1025);val t=InputTransform.create(iw,ih,w,h,if(it%2==0)"letterbox" else "center_crop");val x=r.nextFloat();val y=r.nextFloat();val back=t.point(x*t.fittedWidth+t.left,y*t.fittedHeight+t.top,false);near(back.first,x,2e-4f);near(back.second,y,2e-4f)}}
    val yolo=ModelPresets.create("yolo",listOf("a","b"))
    val rows=arrayOf(floatArrayOf(320f,320f,160f,160f,.9f,.1f),floatArrayOf(321f,320f,160f,160f,.8f,.1f),floatArrayOf(320f,320f,160f,160f,.1f,.95f))
    val bnc=rows.flatMap{it.toList()}.toFloatArray();val bcn=FloatArray(18){i->rows[i%3][i/3]}
    test("YOLO BCN per-class NMS preserves separate labels"){val p=decode(yolo,bcn,listOf(1,6,3));check(p.size==2 && p.map{it.label}.toSet()==setOf("a","b"));near(p.first{it.label=="a"}.xmin,.375f)}
    test("YOLO BNC matches BCN numerically"){check(decode(yolo,bcn,listOf(1,6,3))==decode(yolo.copy(outputLayout="BNC"),bnc,listOf(1,3,6)))}
    test("YOLO refuses mismatched class/channel contract"){fails{decode(yolo,bcn,listOf(1,3,6))}}
    test("YOLO objectness multiplied exactly once"){val c=ModelPresets.create("yolo5",listOf("a"));val p=decode(c,floatArrayOf(320f,320f,100f,100f,.5f,.8f),listOf(1,1,6));check(p.size==1);near(p.single().score,.4f)}
    test("YOLO low objectness does not create a detection"){val c=ModelPresets.create("yolo5",listOf("a"));check(decode(c,floatArrayOf(320f,320f,100f,100f,.2f,1f),listOf(1,1,6)).isEmpty())}
    test("YOLO box to bottom point uses unletterboxed coordinates"){val c=yolo.copy(outputMode="points",pointAnchor="bottom_center");val t=InputTransform.create(640,320,640,640,"letterbox");val p=decode(c,floatArrayOf(320f,320f,160f,160f,.9f,.1f),listOf(1,6,1),t).single();near(p.pointX,.5f);near(p.pointY,.75f);near(p.boxHeight,.5f)}
    test("derived counts are post-NMS proposals"){val p=decode(yolo.copy(deriveCounts=true),bcn,listOf(1,6,3));check(p.filter{it.type=="count"}.map{it.count}==listOf(1,1))}
    test("negative/degenerate boxes are excluded"){check(decode(yolo,floatArrayOf(320f,320f,-2f,100f,.9f,.1f),listOf(1,6,1)).isEmpty())}
    test("NaN scores fail rather than fabricate output"){fails{decode(yolo,floatArrayOf(320f,320f,100f,100f,Float.NaN,.1f),listOf(1,6,1))}}
    test("SSD explicit indices and class offset"){val c=ModelConfig(labels=listOf("a"),classOffset=1,resizeMode="stretch");val p=ModelAdapters.decode(mapOf(0 to tensor(1,1,4,values=floatArrayOf(.1f,.2f,.3f,.4f)),1 to tensor(1,1,values=floatArrayOf(1f)),2 to tensor(1,1,values=floatArrayOf(.9f)),3 to tensor(1,values=floatArrayOf(1f))),c,InputTransform.create(640,640,300,300,"stretch")).single();near(p.xmin,.2f);near(p.ymin,.1f);check(p.label=="a")}
    test("post-NMS xyxy score class is explicit, not guessed as raw YOLO"){val c=ModelConfig(adapter="xyxy_score_class",labels=listOf("a"));val p=decode(c,floatArrayOf(.1f,.2f,.4f,.7f,.9f,0f),listOf(1,1,6)).single();near(p.ymax,.7f)}
    test("unknown class output rejected"){fails{decode(ModelConfig(adapter="xyxy_score_class",labels=listOf("a")),floatArrayOf(.1f,.2f,.4f,.7f,.9f,7f),listOf(1,1,6))}}
    test("classifier stable softmax large logits"){val c=ModelPresets.create("classification",listOf("a","b")).copy(threshold=0f);val p=decode(c,floatArrayOf(1001f,1000f),listOf(1,2));near(p.sumOf{it.score.toDouble()}.toFloat(),1f);near(p[0].score,.7310586f)}
    test("classifier sigmoid multi-label and topK"){val c=ModelConfig(adapter="classification",labels=listOf("a","b"),scoreActivation="sigmoid",topK=1);val p=decode(c,floatArrayOf(0f,2f),listOf(1,2));check(p.single().label=="b")}
    test("probability outputs out of range refused"){fails{decode(ModelConfig(adapter="classification",labels=listOf("a")),floatArrayOf(2f),listOf(1,1))}}
    test("points one global class, invalid coordinates excluded"){val c=ModelPresets.create("points",listOf("p"));val p=decode(c,floatArrayOf(.1f,.2f,.9f,1.5f,.2f,.8f,.4f,.8f,.1f),listOf(1,3,3));check(p.size==1);near(p[0].pointY,.2f)}
    test("points per-slot labels preserved"){val c=ModelPresets.create("points",listOf("left","right"));val p=decode(c,floatArrayOf(.1f,.2f,.8f,.4f),listOf(1,2,2));check(p.map{it.label}==listOf("left","right"))}
    test("point cardinality/class mapping mismatch refused"){fails{decode(ModelPresets.create("points",listOf("a","b")),floatArrayOf(.1f,.2f),listOf(1,1,2))}}
    val heat=ModelPresets.create("heatmap",listOf("a","b"));val hv=floatArrayOf(.1f,.8f,.2f,.1f,.9f,.1f,.2f,.3f)
    test("NHWC heatmap class-specific argmax at cell centre"){val p=decode(heat,hv,listOf(1,2,2,2));near(p[0].pointX,.25f);near(p[0].pointY,.75f);near(p[1].pointY,.25f)}
    test("NCHW heatmap matches NHWC"){val arr=FloatArray(8){i->hv[(i%4)*2+i/4]};check(decode(heat,hv,listOf(1,2,2,2))==decode(heat.copy(outputLayout="NCHW"),arr,listOf(1,2,2,2)))}
    test("heatmap softmax mode deliberately refused"){fails{decode(heat.copy(scoreActivation="softmax"),hv,listOf(1,2,2,2))}}
    test("RGB FLOAT32 normalizes channel values"){val c=ModelConfig(inputWidth=1,inputHeight=1,mean=0f,std=255f);val b=TensorCodec.encode(intArrayOf(0xffff8000.toInt()),c);near(b.float,1f);near(b.float,128/255f);near(b.float,0f)}
    test("NCHW actually transposes pixels"){val c=ModelConfig(inputWidth=2,inputHeight=1,mean=0f,std=255f,inputLayout="NCHW");val b=TensorCodec.encode(intArrayOf(0xffff0000.toInt(),0xff0000ff.toInt()),c);check(FloatArray(6){b.float}.contentEquals(floatArrayOf(1f,0f,0f,0f,0f,1f)))}
    test("BGR and channel-specific means/stds"){val c=ModelConfig(inputWidth=1,inputHeight=1,isRgb=false,channelMean=listOf(10f,20f,30f),channelStd=listOf(2f,4f,5f));val b=TensorCodec.encode(intArrayOf((40 shl 16) or (24 shl 8) or 12),c);near(b.float,1f);near(b.float,1f);near(b.float,2f)}
    test("UINT8 raw preserves V2 normalization semantics"){val c=ModelConfig(inputWidth=1,inputHeight=1,inputType="UINT8");val b=TensorCodec.encode(intArrayOf(0xff12abef.toInt()),c);check((b.get().toInt() and 255)==18);check((b.get().toInt() and 255)==171);check((b.get().toInt() and 255)==239)}
    test("INT8 tensor quantization and saturation"){val c=ModelConfig(inputWidth=1,inputHeight=1,inputType="INT8",quantizationMode="tensor",mean=0f,std=255f);val b=TensorCodec.encode(intArrayOf(0xffff0000.toInt()),c,.1f,-2);check(b.get().toInt()==8);check(b.get().toInt()==-2);val sat=TensorCodec.encode(intArrayOf(0xffffffff.toInt()),c,.001f,0);check(sat.get().toInt()==127)}
    test("output dequantization uses real tensor scale and zero"){val b=ByteBuffer.wrap(byteArrayOf(0,128.toByte(),255.toByte()));val v=TensorCodec.decode(b,"UINT8",3,.5f,128);near(v[0],-64f);near(v[1],0f);near(v[2],63.5f)}
    test("invalid tensor quantization scale rejected"){fails{TensorCodec.encode(intArrayOf(0),ModelConfig(inputWidth=1,inputHeight=1,inputType="INT8",quantizationMode="tensor"),0f,0)}}
    test("local endpoints cannot target LAN, Internet, credentials or redirects"){val c=ModelPresets.create("http_caption",listOf("a"));for(url in listOf("https://example.org","http://192.168.1.2/predict","http://127.0.0.1.evil/predict","http://user@127.0.0.1/predict","http://127.0.0.1/predict#part"))fails{LocalCallContract.validate(c.copy(endpoint=url))};LocalCallContract.validate(c)}
    test("response path traverses fields and indexed arrays only"){val data=mapOf("choices" to listOf(mapOf("message" to mapOf("content" to "caption"))));check(LocalCallContract.select(data,"choices.0.message.content")=="caption");check(LocalCallContract.select(data,"choices.2")==null)}
    test("request placeholders are whole JSON values, not string code interpolation"){val result=LocalCallContract.replace(mapOf("prompt" to "\$prompt","literal" to "prefix \$prompt"),mapOf("\$prompt" to "a \"quoted\" prompt")) as Map<*,*>;check(result["prompt"]=="a \"quoted\" prompt" && result["literal"]=="prefix \$prompt")}
    test("human points, boxes and text survive rerun"){val a=SampleAnnotations(points=listOf(PointTarget("p",.2f,.2f,"a",isHumanVerified=true,sourceProvenance="model_litert:old")),captions=listOf(CaptionTarget("c","human",isHumanVerified=true)));val result=ProposalMerger.merge(a,listOf(ModelProposal("point","a",.9f,pointX=.8f,pointY=.8f)),"POINTING,CAPTIONING",replaceTypes=setOf("point","caption"));check(result.points==a.points && result.captions==a.captions)}
    test("empty new output replaces only untouched proposals of declared types"){val a=SampleAnnotations(points=listOf(PointTarget("p",.2f,.2f,"a",sourceProvenance="model_litert:old")),boxes=listOf(BoxTarget("b",.1f,.1f,.2f,.2f,"a",sourceProvenance="model_litert:old")));val b=ProposalMerger.merge(a,emptyList(),"POINTING,DETECTION",replaceTypes=setOf("point"));check(b.points.isEmpty() && b.boxes==a.boxes && b.quality.verifiedNegativeQueries.isEmpty())}
    test("referenced machine regions survive rerun"){val a=SampleAnnotations(points=listOf(PointTarget("p",.2f,.2f,"a",sourceProvenance="model_litert:old")),groundings=listOf(GroundingTarget("g","target",pointIds=listOf("p"))));check(ProposalMerger.merge(a,emptyList(),"POINTING_MULTI",replaceTypes=setOf("point")).points==a.points)}
    test("disabled tasks retain all their annotations"){val a=SampleAnnotations(captions=listOf(CaptionTarget("c","draft",sourceProvenance="model_local_http")));check(ProposalMerger.merge(a,emptyList(),"DETECTION",replaceTypes=setOf("caption")).captions==a.captions)}
    test("import is never human verified and drops quality decisions"){val a=SampleAnnotations(points=listOf(PointTarget("p",.1f,.2f,"a",isHumanVerified=true)),quality=QualityAuditTarget(verifiedNegativeQueries=listOf("a")));val b=ProposalMerger.draft(a);check(!b.points[0].isHumanVerified && b.quality.verifiedNegativeQueries.isEmpty())}
    test("machine captions and counts cannot bypass review"){val a=SampleAnnotations(captions=listOf(CaptionTarget("c","a photo",sourceProvenance="model_http")));check(AnnotationReview.problems(a,setOf(StudioTask.CAPTIONING)).isNotEmpty());val b=SampleAnnotations(counts=listOf(CountingTarget("n","a",1,sourceProvenance="model_litert")));check(AnnotationReview.problems(b,setOf(StudioTask.COUNTING)).isNotEmpty())}
    test("point proposal captures model origin and correction generation"){val p=ModelProposal("point","a",.9f,pointX=.4f,pointY=.6f,modelX=.35f,modelY=.58f,correctionGeneration=2);val a=ProposalMerger.merge(SampleAnnotations(),listOf(p),"POINTING_MULTI");check(a.points.single().modelLabel=="a" && a.points.single().correctionGeneration==2 && !a.points.single().isHumanVerified)}
    test("pack cannot contain mutually exclusive pointing cardinalities"){fails{StudioPack(name="x",classes=listOf("a"),tasks=listOf("POINTING","POINTING_MULTI")).validate()};StudioPack(name="x",classes=listOf("a"),tasks=listOf("POINTING_MULTI","CAPTIONING"),batchSize=500).validate()}
    test("pack schema, task and class constraints"){fails{StudioPack(schema="x",name="x",classes=listOf("a"),tasks=listOf("DETECTION")).validate()};fails{StudioPack(name="x",classes=listOf("a,b"),tasks=listOf("DETECTION")).validate()};fails{StudioPack(name="x",classes=listOf("a"),tasks=listOf("AUDIO")).validate()}}
    fun example(i:Int)=CorrectionExample("instance-$i","image-$i",.2+(i%17)/30.0,.2+(i%11)/30.0,.2,.3,.85,.2+(i%17)/30.0+((i%17)/30.0-.25)*.12,.2+(i%11)/30.0-.02,true,true)
    test("accepted predictions are never supervised correction examples"){val e=example(0).copy(explicitlyAdjusted=false);check(AdaptiveCorrection.add(CorrectionGroup("a"),listOf(e)).examples.isEmpty())}
    test("unverified and implausibly large corrections rejected"){check(AdaptiveCorrection.add(CorrectionGroup("a"),listOf(example(0).copy(humanVerified=false),example(1).copy(correctedX=1.0))).examples.isEmpty())}
    test("correction reservoir deterministic, bounded and de-duplicated"){val entries=(0..2500).map(::example);val a=AdaptiveCorrection.add(CorrectionGroup("a"),entries);val b=AdaptiveCorrection.add(CorrectionGroup("a"),entries.reversed());check(a.examples==b.examples && a.examples.size==2048);check(AdaptiveCorrection.add(a,a.examples).examples==a.examples)}
    test("minimum evidence is distinct images, not number of points"){val g=CorrectionGroup("a",(0..100).map{example(it).copy(imageKey="single-image")});val r=AdaptiveCorrection.train(g);check(!r.promoted && r.group.head==null)}
    val evaluation=AdaptiveCorrection.train(AdaptiveCorrection.add(CorrectionGroup("learn"),(0..499).map(::example)))
    test("ridge head can learn controlled spatial residuals"){check(evaluation.promoted && evaluation.group.head!=null){evaluation.group.report}}
    test("train and control sets never share the same image"){check(evaluation.trainImages.intersect(evaluation.holdoutImages).isEmpty());check(evaluation.trainImages.size>=32 && evaluation.holdoutImages.size>=8)}
    test("head cannot move a point more than eight percent"){val h=CorrectionHead(listOf(List(6){10.0},List(6){-10.0}),1);val result=AdaptiveCorrection.predict(h,doubleArrayOf(1.0,1.0,1.0,1.0,1.0,1.0))!!;check(result[0]==.08 && result[1]==-.08)}
    test("reused control set does not blindly replace the champion"){val again=AdaptiveCorrection.train(evaluation.group);check(!again.promoted && again.group.head==evaluation.group.head)}
    test("corrector stays isolated by model contract and label"){val src="model_litert:abc:contract";val key=AdaptiveCorrection.groupKey(src,"a");val head=CorrectionHead(listOf(listOf(.04,0.0,0.0,0.0,0.0,0.0),List(6){0.0}),1);val ledger=CorrectionLedger(groups=listOf(CorrectionGroup(key,head=head)));val a=ModelProposal("point","a",.9f,pointX=.5f,pointY=.5f,source=src);val b=a.copy(label="b");val out=AdaptiveCorrection.apply(listOf(a,b),ledger);near(out[0].pointX,.54f);check(out[1]==b);near(out[0].modelX!!,.5f)}
    test("shared work policy requires stable worker identity"){fails{ProcessingSettings(collaborationEnabled=true,collaborationWorkerId="").validate()};ProcessingSettings(collaborationEnabled=true,collaborationWorkerId="worker-123",claimLeaseMinutes=720).validate();fails{ProcessingSettings(collaborationEnabled=true,collaborationWorkerId="bad space").validate()}}
    test("shared claim policy skips completed and active foreign work but recovers expired leases"){val now=1000L;check(SharedClaimPolicy.unavailable("DONE","other",Long.MAX_VALUE,"me",now));check(SharedClaimPolicy.unavailable("CLAIMED","other",2000L,"me",now));check(!SharedClaimPolicy.unavailable("CLAIMED","other",999L,"me",now));check(!SharedClaimPolicy.unavailable("CLAIMED","me",2000L,"me",now));check(SharedClaimPolicy.reusableByCurrentWorker("CLAIMED","me",2000L,"me",now));check(!SharedClaimPolicy.reusableByCurrentWorker("CLAIMED","me",999L,"me",now))}
    test("embedding and inspect-only contracts permit empty class vocabularies"){ModelContract.validate(ModelConfig(task="embedding",adapter="embedding",labels=emptyList()));ModelContract.validate(ModelConfig(task="inspection",adapter="inspect_only",labels=emptyList()))}
    test("box correction head can adjust centre and size without touching another label"){val src="model_litert:boxmodel:contract";val key=AdaptiveCorrection.groupKey(src,"a","box");val head=CorrectionHead(listOf(listOf(.02,0.0,0.0,0.0,0.0,0.0),listOf(-.01,0.0,0.0,0.0,0.0,0.0),listOf(.04,0.0,0.0,0.0,0.0,0.0),listOf(.02,0.0,0.0,0.0,0.0,0.0)),1,"box");val ledger=CorrectionLedger(groups=listOf(CorrectionGroup(key,head=head)));val a=ModelProposal("box","a",.9f,xmin=.2f,ymin=.2f,xmax=.4f,ymax=.5f,source=src);val b=a.copy(label="b");val out=AdaptiveCorrection.apply(listOf(a,b),ledger);near(out[0].xmin,.20f);near(out[0].xmax,.44f);near(out[0].ymin,.18f);near(out[0].ymax,.50f);check(out[1]==b)}
    test("mask canonical RLE and COCO column order remain distinct") {
        val pixels=booleanArrayOf(true,false,false,true,true,false)
        val m=MaskTarget("m","target",2,3,MaskCodec.encode(pixels))
        check(MaskCodec.decode(m).contentEquals(pixels))
        check(MaskCodec.coco(m,2,3)["counts"]==listOf(0,1,1,1,1,1,1))
        fails { MaskCodec.decode(m.copy(runs=listOf(8))) }
        fails { MaskCodec.decode(m.copy(runs=listOf(-1,7))) }
    }
    test("mask brush, erase and human corrections survive new inference") {
        val blank=MaskTarget("m","target",64,64,listOf(4096))
        val painted=MaskCodec.stroke(blank,.2f,.5f,.8f,.5f,.1f,false)
        check(MaskCodec.decode(painted).count { it }>100 && painted.isHumanVerified && painted.explicitlyAdjusted)
        val erased=MaskCodec.stroke(painted,.2f,.5f,.8f,.5f,.1f,true)
        check(MaskCodec.decode(erased).none { it })
        val merged=ProposalMerger.merge(SampleAnnotations(masks=listOf(painted)),emptyList(),"SEGMENTATION",replaceTypes=setOf("mask"))
        check(merged.masks==listOf(painted))
        check(AnnotationReview.problems(SampleAnnotations(masks=listOf(blank)),setOf(StudioTask.SEGMENTATION)).isNotEmpty())
    }
    test("wide masks and original camera resolution export without a full-size raster") {
        val wide=MaskTarget("wide","target",4096,512,listOf(0,4096*512))
        MaskCodec.validate(wide)
        val tiny=MaskTarget("tiny","target",2,2,listOf(0,1,3))
        val projected=MaskCodec.projectCoco(tiny,4000,3000)
        check(projected.area==3_000_000 && projected.bbox==listOf(0,0,2000,1500))
        val runs=projected.segmentation["counts"] as List<*>
        check(runs.sumOf{it as Int}==12_000_000 && runs.take(3)==listOf(0,1500,1500))
        check(MaskCodec.projectCoco(tiny.copy(runs=listOf(4)),4000,3000).area==0)
        fails { MaskCodec.projectCoco(tiny,100_001,100_001) }
    }
    test("named signature output order differs from default graph") {
        val c=ModelConfig(adapter="yolo",labels=listOf("a"),outputIndex=2,namedOutputIndices=mapOf("logits" to 0,"boxes" to 1,"detections" to 2),training=TrainingContract(inferOutputs=listOf("detections"),targetShape=listOf(1,100,6),targetEncoding="boxes_xyxy_class_mask"))
        check(c.signatureConfig().outputIndex==0)
        val p=decode(c.signatureConfig(),floatArrayOf(.5f,.5f,.2f,.4f,.9f),listOf(1,5,1)).single()
        near(p.xmin,.4f);near(p.ymax,.7f)
    }
    test("RTMDet uses zero-offset grid and stride-scaled distances") {
        val c=ModelConfig(adapter="rtmdet",labels=listOf("a","b"),inputWidth=16,inputHeight=16,featureStrides=listOf(8),resizeMode="stretch")
        val values=FloatArray(24){-20f};val base=18
        values[base]=10f;values[base+1]=-10f
        for(k in 2..5)values[base+k]=.5f
        val p=ModelAdapters.decode(mapOf(0 to TensorValues(listOf(1,2,2,6),values)),c,InputTransform.create(16,16,16,16,"stretch")).single()
        near(p.xmin,.25f);near(p.xmax,.75f);check(p.label=="a")
        fails { ModelAdapters.decode(mapOf(0 to TensorValues(listOf(1,1,4,6),values)),c,InputTransform.create(16,16,16,16,"stretch")) }
    }
    println("PASS $count V4.2 engine tests; 1500 transform trials included. Numeric fixtures only: no Android runtime, live HF, actual weights, Room or real correction benchmark executed.")
}
