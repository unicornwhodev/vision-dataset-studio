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
    println("PASS $checked training target checks: verified labels, explicit negatives, geometry, contradictory points")
}
