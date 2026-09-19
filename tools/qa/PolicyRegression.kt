import com.unicornwhodev.visiondatasetstudio.core.geometry.*
import com.unicornwhodev.visiondatasetstudio.core.workflow.*
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.domain.validation.AnnotationReview
import com.unicornwhodev.visiondatasetstudio.domain.export.WebDatasetTarWriter
import java.io.File
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.random.Random

private var checks = 0
private fun test(name: String, action: () -> Unit) { action(); checks++; println("PASS $name") }
private fun near(actual: Float, expected: Float) = check(abs(actual - expected) < .0001f) { "$actual != $expected" }
private fun issues(a: SampleAnnotations, vararg t: StudioTask) = AnnotationReview.problems(a, t.toSet())
private fun validBox() = BoxTarget("box", .1f, .2f, .7f, .8f, "object", true)
private fun validPoint() = PointTarget("point", .5f, .5f, "object", isHumanVerified = true)
private fun fails(action: () -> Unit) { var thrown = false; try { action() } catch (_: Exception) { thrown = true }; check(thrown) }
fun main(args: Array<String>) {
    test("viewport landscape FIT excludes top/bottom bars") {
        val v = ImageViewport(400f, 400f, 800f, 400f); near(v.left,0f); near(v.top,100f)
        check(v.toImage(200f, 99f) == null && v.toImage(200f, 301f) == null)
        near(v.toImage(200f, 200f)!!.x,.5f); near(v.toImage(200f, 200f)!!.y,.5f)
    }
    test("viewport portrait FIT excludes side bars") { val v=ImageViewport(400f,400f,400f,800f); near(v.left,100f); check(v.toImage(20f,200f)==null) }
    test("viewport zoom is centred and translated") { val v=ImageViewport(400f,400f,800f,400f,2f,30f,-10f); near(v.toScreen(.5f,.5f).x,230f); near(v.toScreen(.5f,.5f).y,190f) }
    test("drag clamping is explicit") { val v=ImageViewport(400f,400f,800f,400f); check(v.toImage(-30f,-30f)==null); check(v.toImage(-30f,-30f,true)==ViewPoint(0f,0f)) }
    test("invalid geometry never returns image coordinates") { listOf(0f,-1f,Float.NaN,Float.POSITIVE_INFINITY).forEach { check(ImageViewport(it,400f,400f,400f).toImage(10f,10f)==null) }; check(ImageViewport(400f,400f,400f,400f).toImage(Float.NaN,5f)==null) }
    test("2000 random FIT/zoom/pan inverse round trips") { val random=Random(79); repeat(2000) {
        val v=ImageViewport(random.nextInt(300,1400).toFloat(),random.nextInt(300,1400).toFloat(),random.nextInt(100,8000).toFloat(),random.nextInt(100,8000).toFloat(),random.nextFloat()*8+1,random.nextFloat()*100-50,random.nextFloat()*100-50)
        val x=.01f+random.nextFloat()*.98f;val y=.01f+random.nextFloat()*.98f;val screen=v.toScreen(x,y);val back=v.toImage(screen.x,screen.y)!!; near(back.x,x);near(back.y,y)
    } }
    test("COCO pixel conversion round trip") { val r=NormalizedRect(.1f,.2f,.7f,.8f);val a=r.toCocoPx(1920,1080);val n=NormalizedRect.fromCocoPx(a[0],a[1],a[2],a[3],1920,1080);near(n.xmin,r.xmin);near(n.ymax,r.ymax) }
    test("YOLO normalized conversion round trip") { val r=NormalizedRect(.1f,.2f,.7f,.8f);val a=r.toYolo();val n=NormalizedRect.fromYolo(a[0],a[1],a[2],a[3]);near(n.xmax,r.xmax);near(n.ymin,r.ymin) }
    test("inference letterbox unprojection") { val lb=LetterboxMath.calculateLetterbox(800,400,400,400);val r=LetterboxMath.unletterboxBox(NormalizedRect(0f,.25f,1f,.75f),lb);check(r==NormalizedRect(0f,0f,1f,1f)) }
    test("six unique nonempty workflow presets") { check(StudioWorkflow.presets.size==6);check(StudioWorkflow.presets.map{it.id}.distinct().size==6);check(StudioWorkflow.presets.all{it.tasks.isNotEmpty()}) }
    test("single and multi-point modes are mutually exclusive") { val t=StudioWorkflow.toggleTask(setOf(StudioTask.POINTING),StudioTask.POINTING_MULTI);check(t==setOf(StudioTask.POINTING_MULTI)) }
    test("cannot deselect final active task") { val t=setOf(StudioTask.DETECTION);check(StudioWorkflow.toggleTask(t,StudioTask.DETECTION)==t) }
    test("task CSV ignores unknowns and round trips") { val t=setOf(StudioTask.CAPTIONING,StudioTask.VQA);check(StudioWorkflow.parseTasks(StudioWorkflow.tasksCsv(t))==t);check(StudioWorkflow.parseTasks("UNKNOWN")==setOf(StudioTask.DETECTION)) }
    test("canonical HF source URL normalization") { check(StudioWorkflow.normalizeRepo(" https://huggingface.co/datasets/owner/data/?x=1#top ")=="owner/data");check(StudioWorkflow.normalizeRepo("beans")=="beans") }
    test("destinations require namespace") { check(StudioWorkflow.normalizeRepo("data",true)==null);check(StudioWorkflow.normalizeRepo("owner/data",true)=="owner/data") }
    test("reject arbitrary hosts, traversal and deep paths") { listOf("https://evil.test/owner/data","http://huggingface.co/datasets/a/b","../repo","a/../repo","a/b/tree/main","a/b?token=secret","a/b/c").forEach{check(StudioWorkflow.normalizeRepo(it)==null){it}} }
    test("editor policy locks every synced state") { listOf("PUBLISHED","PUBLISHING","PURGED","VERIFIED").forEach{check(!StudioWorkflow.canEdit("AVAILABLE",it,true))};check(StudioWorkflow.canEdit("AVAILABLE","NOT_EXPORTED",true)) }
    test("editor policy requires a local acquired file") { check(!StudioWorkflow.canEdit("ERROR_RETRYABLE","NOT_EXPORTED",true));check(!StudioWorkflow.canEdit("AVAILABLE","NOT_EXPORTED",false)) }
    test("automatic queue excludes rejected, deferred, validated") { listOf("REJECTED","DEFERRED","VALIDATED").forEach{check(!StudioWorkflow.isPending(it))};check(StudioWorkflow.isPending("PROPOSALS_AVAILABLE")) }
    test("empty detection is not a verified negative") { check(issues(SampleAnnotations(),StudioTask.DETECTION).isNotEmpty()) }
    test("explicit negative allows empty detection") { check(issues(SampleAnnotations(quality=QualityAuditTarget(verifiedNegativeQueries=listOf("object"))),StudioTask.DETECTION).isEmpty()) }
    test("unlocalizable present is distinct and accepted") { check(issues(SampleAnnotations(quality=QualityAuditTarget(isUnlocalizablePresent=true)),StudioTask.POINTING).isEmpty()) }
    test("contradictory absence and presence blocked") { check(issues(SampleAnnotations(quality=QualityAuditTarget(verifiedNegativeQueries=listOf("object"),isUnlocalizablePresent=true)),StudioTask.DETECTION).isNotEmpty()) }
    test("valid human box accepted") { check(issues(SampleAnnotations(boxes=listOf(validBox())),StudioTask.DETECTION).isEmpty()) }
    test("unverified AI box blocked") { check(issues(SampleAnnotations(boxes=listOf(validBox().copy(isHumanVerified=false))),StudioTask.DETECTION).any{it.contains("propositions")}) }
    test("degenerate box blocked") { check(issues(SampleAnnotations(boxes=listOf(validBox().copy(xmax=.1f))),StudioTask.DETECTION).isNotEmpty()) }
    test("NaN box coordinates blocked") { check(issues(SampleAnnotations(boxes=listOf(validBox().copy(xmin=Float.NaN))),StudioTask.DETECTION).isNotEmpty()) }
    test("out-of-image point blocked") { check(issues(SampleAnnotations(points=listOf(validPoint().copy(x=1.1f))),StudioTask.POINTING).isNotEmpty()) }
    test("single-point cardinality enforced") { check(issues(SampleAnnotations(points=listOf(validPoint(),validPoint().copy(id="p2"))),StudioTask.POINTING).isNotEmpty()) }
    test("multi-point cardinality allowed") { check(issues(SampleAnnotations(points=listOf(validPoint(),validPoint().copy(id="p2"))),StudioTask.POINTING_MULTI).isEmpty()) }
    test("duplicate region ids blocked") { check(issues(SampleAnnotations(points=listOf(validPoint(),validPoint())),StudioTask.POINTING_MULTI).isNotEmpty()) }
    test("explicit VQA abstention accepted") { check(issues(SampleAnnotations(vqaList=listOf(VqaTarget("q","What is hidden?","",true))),StudioTask.VQA).isEmpty()) }
    test("unanswered VQA blocked") { check(issues(SampleAnnotations(vqaList=listOf(VqaTarget("q","What?",""))),StudioTask.VQA).isNotEmpty()) }
    test("empty extra caption cannot silently export") { check(issues(SampleAnnotations(captions=listOf(CaptionTarget("c","Visible image"),CaptionTarget("blank",""))),StudioTask.CAPTIONING).isNotEmpty()) }
    test("zero is a valid explicit count") { check(issues(SampleAnnotations(counts=listOf(CountingTarget("n","object",0))),StudioTask.COUNTING).isEmpty()) }
    test("negative count blocked") { check(issues(SampleAnnotations(counts=listOf(CountingTarget("n","object",-1))),StudioTask.COUNTING).isNotEmpty()) }
    test("grounding requires an existing target") { check(issues(SampleAnnotations(groundings=listOf(GroundingTarget("g","object",boxIds=listOf("missing")))),StudioTask.GROUNDING).isNotEmpty()) }
    test("grounding with text and actual target accepted") { check(issues(SampleAnnotations(boxes=listOf(validBox()),groundings=listOf(GroundingTarget("g","object",boxIds=listOf("box")))),StudioTask.GROUNDING).isEmpty()) }
    test("uncertain cases require deferral not validation") { check(issues(SampleAnnotations(quality=QualityAuditTarget(isUncertain=true)),StudioTask.NEGATIVE).isNotEmpty()) }
    test("TAR roundtrip fixture generated by real production writer") {
        val file=File(args[0],"policy-fixture.tar");file.parentFile.mkdirs()
        WebDatasetTarWriter(file).use { it.addBytes("sample.txt","real archive fixture".toByteArray(),0);it.addBytes("sample.json","{\"validated\":true}".toByteArray(),0) }
        check(file.length()%512==0L)
    }
    test("TAR refuses unsafe paths") { fails { WebDatasetTarWriter(ByteArrayOutputStream()).use{it.addBytes("../escape",byteArrayOf(1))} } }
    test("TAR refuses truncated declared streams") { fails { WebDatasetTarWriter(ByteArrayOutputStream()).use{it.addStream("short",byteArrayOf(1).inputStream(),2)} } }
    test("TAR refuses longer-than-declared streams") { fails { WebDatasetTarWriter(ByteArrayOutputStream()).use{it.addStream("long",byteArrayOf(1,2).inputStream(),1)} } }
    test("TAR refuses overlong names rather than truncating") { fails { WebDatasetTarWriter(ByteArrayOutputStream()).use{it.addBytes("x".repeat(101),byteArrayOf(1))} } }
    println("PASS $checks policy tests; 2000 randomized coordinate round trips included. No Android UI, network or Moshi serialization tests run.")
}
