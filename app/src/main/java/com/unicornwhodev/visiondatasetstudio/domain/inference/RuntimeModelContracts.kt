package com.unicornwhodev.visiondatasetstudio.domain.inference

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr

/** Known converter metadata, checked against tensor signatures by the installer.
 * No labels, normalization or output semantics are inferred from a filename. */
object RuntimeModelContracts {
    fun isDynamicYolo(contract:Map<*,*>) =
        contract["key"]?.toString()?.contains(Regex("(^|_)yolo[0-9]+[a-z](_|$)"))==true &&
            contract["task"]?.toString()?.endsWith("detection")==true && contract["route"]=="onnx2tf_dynamic"
    fun dynamicYolo(contract:Map<*,*>,labelMap:Map<*,*>):ModelConfig {
        require(isDynamicYolo(contract))
        val report=contract["report"] as? Map<*,*> ?: error(tr("Rapport de conversion absent", "Conversion report missing"))
        require(report["input_layout"]=="NCHW" && report["dynamic_validated"]==true)
        require(report["preprocessing"]=="RGB float32 in [0,1]; pad to multiples of 32; no resampling embedded") { tr("Prétraitement YOLO non pris en charge", "Unsupported YOLO preprocessing") }
        val bounds=report["spatial_bounds"] as? Map<*,*> ?: error(tr("Bornes dynamiques absentes", "Dynamic bounds missing"))
        val stride=(bounds["alignment"] as? Number)?.toInt() ?: 0
        val maximum=(bounds["tested_max"] as? Number)?.toInt() ?: 0
        require(stride==32 && maximum in 640..2048)
        require(labelMap.isNotEmpty() && labelMap.keys.map{it.toString()}.toSet()==labelMap.indicesAsStrings()) { tr("Indices de classes non contigus", "Class indices are not contiguous") }
        val labels=(0 until labelMap.size).map { (labelMap[it.toString()] as? String)?.takeIf(String::isNotBlank) ?: error(tr("Classe absente", "Missing class")) }
        val checks=report["checks"] as? List<*> ?: error(tr("Vérifications des sorties absentes", "Output checks missing"))
        require(checks.isNotEmpty() && checks.all { row ->
            val shapes=(row as? Map<*,*>)?.get("output_shapes") as? List<*>
            val shape=shapes?.singleOrNull() as? List<*>
            shape?.map{(it as? Number)?.toInt()}?.let { it.size==3 && it[0]==1 && it[1]==labels.size+4 && (it[2] ?: 0)>0 }==true
        }) { tr("Sorties YOLO incompatibles avec les classes", "YOLO outputs do not match the classes") }
        return ModelConfig(task="object_detection",adapter="yolo",inputWidth=640,inputHeight=640,
            inputLayout="NCHW",inputType="FLOAT32",mean=0f,std=255f,isRgb=true,resizeMode="letterbox",padValue=114,
            labels=labels,outputLayout="BCN",coordinates="pixels",yoloObjectness=false,scoreActivation="none",
            threshold=.35f,nmsIou=.45f,dynamicMinSize=stride,dynamicMaxSize=maximum,dynamicStride=stride).also(ModelContract::validate)
    }
    private fun Map<*,*>.indicesAsStrings()=(0 until size).map(Int::toString).toSet()
}
