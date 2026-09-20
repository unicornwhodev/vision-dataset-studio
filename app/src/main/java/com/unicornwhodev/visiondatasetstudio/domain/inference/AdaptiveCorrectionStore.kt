package com.unicornwhodev.visiondatasetstudio.domain.inference

import android.content.Context
import android.util.AtomicFile
import com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Local numeric examples only. Never exports or fine-tunes model weights automatically. */
class AdaptiveCorrectionStore(private val context:Context) {
    private val adapter=com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi.adapter(CorrectionLedger::class.java)
    private fun file(projectId:Long)=File(context.filesDir,"corrections/$projectId.json").apply{parentFile?.mkdirs()}
    fun read(projectId:Long):CorrectionLedger {
        val f=file(projectId);val atomic=AtomicFile(f)
        if(!f.exists() && !File(f.path+".bak").exists())return CorrectionLedger()
        require(f.length()<=16L*1024*1024)
        return (adapter.fromJson(atomic.openRead().bufferedReader().use{it.readText()}) ?: error("Journal de correction invalide")).also{require(it.schema==1)}
    }
    suspend fun train(project:ProjectEntity,pairs:List<Pair<SampleEntity,SampleAnnotations>>):String = withContext(Dispatchers.IO) {
        check(pairs.isNotEmpty() && pairs.all{it.first.annotationStatus in setOf("VALIDATED","REJECTED","DUPLICATE")}) { "Le lot doit avoir une décision humaine finale pour chaque cas" }
        val additions=mutableMapOf<String,MutableList<CorrectionExample>>()
        pairs.filter{it.first.annotationStatus=="VALIDATED"}.forEach{(s,a)->
            val image=s.sha256 ?: return@forEach
            a.points.filter{it.explicitlyAdjusted && it.isHumanVerified && !it.isAbsent && !it.isAbstained && it.modelX!=null && it.modelY!=null && it.modelLabel==it.label && it.sourceProvenance.startsWith("model_litert:")}.forEach{p->
                val key=AdaptiveCorrection.groupKey(p.sourceProvenance,p.label)
                additions.getOrPut(key){mutableListOf()}.add(CorrectionExample(AdaptiveCorrection.hash("$image:${p.id}"),image,p.modelX!!.toDouble(),p.modelY!!.toDouble(),p.boxWidth.toDouble(),p.boxHeight.toDouble(),(p.modelScore ?: .5f).toDouble(),p.x.toDouble(),p.y.toDouble(),true,true))
            }
            a.boxes.filter{(it.correctionGeneration==null || it.modelCoordinatesVersion>=1) && it.explicitlyAdjusted && it.isHumanVerified && it.modelXmin!=null && it.modelYmin!=null && it.modelXmax!=null && it.modelYmax!=null && it.sourceProvenance.startsWith("model_litert:")}.forEach{b->
                val mx=(b.modelXmin!!+b.modelXmax!!)/2.0;val my=(b.modelYmin!!+b.modelYmax!!)/2.0
                val mw=(b.modelXmax!!-b.modelXmin!!).toDouble();val mh=(b.modelYmax!!-b.modelYmin!!).toDouble()
                val cx=(b.xmin+b.xmax)/2.0;val cy=(b.ymin+b.ymax)/2.0;val w=(b.xmax-b.xmin).toDouble();val h=(b.ymax-b.ymin).toDouble()
                val key=AdaptiveCorrection.groupKey(b.sourceProvenance,b.label,"box")
                additions.getOrPut(key){mutableListOf()}.add(CorrectionExample(AdaptiveCorrection.hash("$image:${b.id}:box"),image,mx,my,mw,mh,(b.modelScore ?: .5f).toDouble(),cx,cy,true,true,"box",w,h))
            }
        }
        if(additions.isEmpty())return@withContext "Aucune correction géométrique humaine supervisée éligible. Les propositions simplement acceptées ne sont pas des exemples d’entraînement."
        val old=read(project.id);val groups=old.groups.associateBy{it.key}.toMutableMap()
        additions.forEach{(key,rows)->groups[key]=AdaptiveCorrection.train(AdaptiveCorrection.add(groups[key] ?: CorrectionGroup(key),rows)).group}
        check(groups.size<=128){"Trop de contextes de correction : réinitialisation explicite nécessaire"}
        val ledger=CorrectionLedger(groups=groups.values.toList());val bytes=adapter.toJson(ledger).toByteArray(Charsets.UTF_8)
        require(bytes.size<=16*1024*1024){"Journal de corrections trop volumineux"}
        check(StorageManager(context).hasAvailableBudget(bytes.size.toLong(),project.diskBudgetMb,com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings.read(project).reserveFreeMb)){"Budget disque insuffisant; ancien correcteur conservé"}
        val atomic=AtomicFile(file(project.id));val stream=atomic.startWrite()
        try{stream.write(bytes);atomic.finishWrite(stream)}catch(e:Exception){atomic.failWrite(stream);throw e}
        additions.keys.joinToString("\n"){groups.getValue(it).report}
    }
    fun reset(projectId:Long){AtomicFile(file(projectId)).delete()}
    fun report(projectId:Long):String {
        val state=read(projectId)
        return if(state.groups.isEmpty())"Aucune correction locale apprise" else state.groups.joinToString("\n"){"${it.examples.size} corrections · ${it.report}"}
    }
}
