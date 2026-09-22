package com.unicornwhodev.visiondatasetstudio.domain.inference

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
/** Contract templates, not claims that arbitrary weights satisfy these contracts. No model is downloaded by a preset. */
object ModelPresets {
    val names get() =linkedMapOf("ssd" to "SSD", "yolo" to tr("YOLO sans objectness", "YOLO without objectness"), "yolo5" to tr("YOLO avec objectness", "YOLO with objectness"), "points" to tr("Points directs", "Direct points"), "heatmap" to "Heatmaps", "classification" to "Classification", "http_caption" to tr("Caption · serveur local", "Caption · local server"), "http_grounding" to tr("Grounding · serveur local", "Grounding · local server"))
    fun create(id:String,labels:List<String>):ModelConfig = when(id) {
        "ssd"->ModelConfig.defaultDetectionPreset(labels)
        "yolo","yolo5"->ModelConfig(adapter="yolo",inputWidth=640,inputHeight=640,mean=0f,std=255f,labels=labels,
            coordinates="pixels",outputLayout=if(id=="yolo5")"BNC" else "BCN",yoloObjectness=id=="yolo5")
        "points"->ModelConfig(task="pointing",adapter="points",inputWidth=224,inputHeight=224,mean=0f,std=255f,labels=labels,resizeMode="stretch")
        "heatmap"->ModelConfig(task="pointing",adapter="heatmap",inputWidth=256,inputHeight=256,mean=0f,std=255f,labels=labels,outputLayout="NHWC",resizeMode="stretch")
        "classification"->ModelConfig.defaultClassifierPreset(labels).copy(adapter="classification",scoreActivation="softmax",resizeMode="center_crop")
        "http_caption"->ModelConfig(task="captioning",runtime="local_http",labels=labels,endpoint="http://127.0.0.1:8080/predict",httpOutputMode="caption_text",responsePath="caption",prompt="Describe the visible image without speculation.")
        "http_grounding"->ModelConfig(task="grounding",runtime="local_http",labels=labels,endpoint="http://127.0.0.1:8080/predict",httpOutputMode="grounding_proposals",responsePath="predictions",prompt="Return phrases and explicit box or point proposal references.")
        else->error(tr("Gabarit inconnu", "Unknown template"))
    }
}
