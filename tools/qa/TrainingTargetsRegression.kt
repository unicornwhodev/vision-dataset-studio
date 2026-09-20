import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import com.unicornwhodev.visiondatasetstudio.domain.training.TrainingTargets
import com.unicornwhodev.visiondatasetstudio.data.model.*

fun main() {
    var checked=0
    fun reject(block:()->Unit){check(runCatching(block).isFailure);checked++}
    val config=ModelConfig(task="classification",adapter="classification",labels=listOf("cat","dog"),inputWidth=32,inputHeight=32,resizeMode="stretch",training=TrainingContract(inferOutputs=listOf("scores"),targetShape=listOf(1,2),targetEncoding="multi_hot"))
    val a=SampleAnnotations(tags=listOf(TagTarget("1","cat",isHumanVerified=true)))
    reject{TrainingTargets.encode(a,config,100,100)} // Unknown dog must not become a negative.
    val complete=a.copy(quality=QualityAuditTarget(verifiedNegativeQueries=listOf("dog")))
    check(TrainingTargets.encode(complete,config,100,100).contentEquals(floatArrayOf(1f,0f)));checked++
    reject{TrainingTargets.encode(complete.copy(tags=listOf(TagTarget("1","cat",isHumanVerified=false))),config,100,100)}
    val single=config.copy(training=config.training!!.copy(targetEncoding="one_hot"))
    check(TrainingTargets.encode(a,single,100,100).contentEquals(floatArrayOf(1f,0f)));checked++
    reject{TrainingTargets.encode(SampleAnnotations(),single,100,100)}
    reject{TrainingTargets.encode(SampleAnnotations(tags=listOf(TagTarget("1","unknown",isHumanVerified=true))),single,100,100)}
    val points=config.copy(task="pointing",adapter="points",training=config.training!!.copy(targetEncoding="points_xyv",targetShape=listOf(1,2,3)))
    val p=SampleAnnotations(points=listOf(PointTarget("1",.2f,.3f,"cat",isHumanVerified=true),PointTarget("2",0f,0f,"dog",isAbsent=true,isHumanVerified=true)))
    check(TrainingTargets.encode(p,points,100,100).contentEquals(floatArrayOf(.2f,.3f,1f,0f,0f,0f)));checked++
    reject{TrainingTargets.encode(p.copy(points=p.points+PointTarget("3",0f,0f,"cat",isAbsent=true,isHumanVerified=true)),points,100,100)}
    reject{TrainingTargets.encode(p,points.copy(resizeMode="center_crop"),400,100)}
    val crop=InputTransform.create(400,100,32,32,"center_crop")
    check(crop.point(16f,16f,false)==(.5f to .5f));checked++
    val multi=config.copy(adapter="fireviewer_dinov3_multitask",spatialLabel="target",namedOutputIndices=mapOf("presence_logits" to 0,"point_logits" to 1,"abstention_logits" to 2,"segmentation_logits" to 3),training=TrainingContract(
        inferOutputs=listOf("abstention_logits","point_logits","presence_logits","segmentation_logits"),targetEncoding="segmentation_point_valid_mask_nchw",targetShape=listOf(1,3,4,4),
        auxiliaryTargets=mapOf("presence" to AuxiliaryTarget(listOf(1,2),labels=listOf("cat","dog")),"abstention" to AuxiliaryTarget(listOf(1)),"supervision" to AuxiliaryTarget(listOf(4),order=listOf("segmentation","point","abstention","presence")))))
    reject { TrainingTargets.encode(SampleAnnotations(),multi,32,32) }
    val partial=TrainingTargets.auxiliary(a,multi)
    check(partial.getValue("supervision").contentEquals(floatArrayOf(0f,0f,0f,0f)));checked++
    val presenceOnly=TrainingTargets.auxiliary(complete,multi)
    check(presenceOnly.getValue("supervision").contentEquals(floatArrayOf(0f,0f,0f,1f)));checked++
    val mask=MaskTarget("m","target",2,2,listOf(0,1,3),isHumanVerified=true)
    val reviewed=complete.copy(masks=listOf(mask),points=listOf(PointTarget("p",.25f,.25f,"target",isHumanVerified=true)))
    val encoded=TrainingTargets.encode(reviewed,multi,32,32)
    check(encoded.size==48 && encoded.take(16).sum()==4f && encoded.drop(32).all { it==1f });checked++
    val auxiliary=TrainingTargets.auxiliary(reviewed,multi)
    check(auxiliary.getValue("supervision").all { it==1f });checked++
    val outputs=listOf(TensorValues(listOf(1),floatArrayOf(0f)),TensorValues(listOf(1,1,4,4),FloatArray(16)),TensorValues(listOf(1,2),FloatArray(2)),TensorValues(listOf(1,1,4,4),FloatArray(16)))
    val loss=TrainingTargets.validationLoss(outputs,encoded,multi,auxiliary)
    check(kotlin.math.abs(loss-2*kotlin.math.ln(2.0))<1e-6);checked++
    println("PASS $checked training target checks: verified labels, explicit negatives, geometry, contradictory points")
}
