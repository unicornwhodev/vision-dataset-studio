package com.unicornwhodev.visiondatasetstudio.domain.inference

/** Contract templates, not claims that arbitrary weights satisfy these contracts. No model is downloaded by a preset. */
object ModelPresets {
    val names=linkedMapOf("ssd" to "SSD", "yolo" to "YOLO sans objectness", "yolo5" to "YOLO avec objectness", "points" to "Points directs", "heatmap" to "Heatmaps", "classification" to "Classification", "http_caption" to "Caption · serveur local")
    fun create(id:String,labels:List<String>):ModelConfig = when(id) {
        "ssd"->ModelConfig.defaultDetectionPreset(labels)
        "yolo","yolo5"->ModelConfig(adapter="yolo",inputWidth=640,inputHeight=640,mean=0f,std=255f,labels=labels,
            coordinates="pixels",outputLayout=if(id=="yolo5")"BNC" else "BCN",yoloObjectness=id=="yolo5")
        "points"->ModelConfig(task="pointing",adapter="points",inputWidth=224,inputHeight=224,mean=0f,std=255f,labels=labels,resizeMode="stretch")
        "heatmap"->ModelConfig(task="pointing",adapter="heatmap",inputWidth=256,inputHeight=256,mean=0f,std=255f,labels=labels,outputLayout="NHWC",resizeMode="stretch")
        "classification"->ModelConfig.defaultClassifierPreset(labels).copy(adapter="classification",scoreActivation="softmax",resizeMode="center_crop")
        "http_caption"->ModelConfig(task="captioning",runtime="local_http",labels=labels,endpoint="http://127.0.0.1:8080/predict",httpOutputMode="caption_text",responsePath="caption",prompt="Describe the visible image without speculation.")
        else->error("Gabarit inconnu")
    }
}
