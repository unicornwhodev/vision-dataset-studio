import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import com.unicornwhodev.visiondatasetstudio.data.model.SampleAnnotations

/** Independent regression expectation: raw model coordinates must survive correction + persistence. */
fun main() {
    val source="model_litert:synthetic-weight-hash:synthetic-contract-hash"
    val raw=ModelProposal("box","object",.9f,xmin=.2f,ymin=.2f,xmax=.4f,ymax=.4f,source=source)
    val weights=listOf(listOf(.03,0.0,0.0,0.0,0.0,0.0),List(6){0.0},List(6){0.0},List(6){0.0})
    val group=CorrectionGroup(AdaptiveCorrection.groupKey(source,"object","box"),head=CorrectionHead(weights,1,"box"))
    val adjusted=AdaptiveCorrection.apply(listOf(raw),CorrectionLedger(groups=listOf(group)))
    val persisted=ProposalMerger.merge(SampleAnnotations(),adjusted,"DETECTION").boxes.single()
    println("Raw xmin=${raw.xmin}; corrected xmin=${persisted.xmin}; recorded modelXmin=${persisted.modelXmin}")
    check(kotlin.math.abs(persisted.modelXmin!!-raw.xmin)<.000001f) {
        "FAIL: original box coordinates are replaced by calibrated coordinates; subsequent correction training learns the wrong baseline"
    }
    val twice=AdaptiveCorrection.apply(adjusted,CorrectionLedger(groups=listOf(group))).single()
    check(kotlin.math.abs(twice.xmin-adjusted.single().xmin)<.000001f) { "Calibration applied twice to an already calibrated box" }
    check(persisted.modelCoordinatesVersion==1)
    println("PASS original box provenance and version survive calibration; reapplication is idempotent")
}
