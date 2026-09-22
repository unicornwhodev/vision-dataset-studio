package com.unicornwhodev.visiondatasetstudio.domain.inference

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
import com.unicornwhodev.visiondatasetstudio.core.storage.DurableFiles
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.metadata.MetadataExtractor
import org.tensorflow.lite.support.metadata.schema.AssociatedFileType
import org.tensorflow.lite.support.metadata.schema.NormalizationOptions
import org.tensorflow.lite.support.metadata.schema.ProcessUnitOptions
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

/** An allowlisted catalogue of published TensorFlow example models, not arbitrary executable adapters.
 * Every import reads the real metadata and checks tensors. No bundled weights, no claimed accuracy.
 */
object PublicModelCatalog {
    data class Entry(val id:String,val title:String,val purpose:String,val fileName:String,val size:Int,val family:String) {
        val url:String get() = "https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/" +
            (if(family=="ssd") "object_detection" else "image_classification") + "/android/" + fileName
        val sourcePage:String get() = "https://github.com/tensorflow/examples/tree/master/lite/examples/" +
            (if(family=="ssd") "object_detection" else "image_classification") + "/android"
        val maxBytes:Long get() = 64L*1024*1024
    }
    val entries get() =listOf(
        Entry("ssd-mobilenet-v1","SSD MobileNet V1",tr("Détection COCO · boîtes et points dérivés", "COCO detection · boxes and derived points"),"lite-model_ssd_mobilenet_v1_1_metadata_2.tflite",300,"ssd"),
        Entry("efficientdet-lite0","EfficientDet Lite0",tr("Détection COCO · boîtes et points dérivés", "COCO detection · boxes and derived points"),"lite-model_efficientdet_lite0_detection_metadata_1.tflite",320,"ssd"),
        Entry("mobilenet-v1-classification","MobileNet V1",tr("Classification ImageNet · tags proposés", "ImageNet classification · proposed tags"),"mobilenet_v1_1.0_224_quantized_1_metadata_1.tflite",224,"classification")
    )
    data class Inspection(val config:ModelConfig,val note:String)
    fun inspect(entry:Entry,file:File):Inspection {
        require(file.isFile && file.length() in 9..entry.maxBytes)
        RandomAccessFile(file,"r").use { raf ->
            val mapped=raf.channel.map(FileChannel.MapMode.READ_ONLY,0,file.length())
            val metadata=MetadataExtractor(mapped)
            require(metadata.hasMetadata() && metadata.isMinimumParserVersionSatisfied) { tr("Métadonnées du modèle absentes ou trop récentes", "Model metadata missing or too recent") }
            Interpreter(file,Interpreter.Options().setNumThreads(1)).use { interpreter ->
                require(interpreter.inputTensorCount==1)
                val input=interpreter.getInputTensor(0)
                require(input.shape().contentEquals(intArrayOf(1,entry.size,entry.size,3))) { tr("Dimensions différentes de la version cataloguée", "Dimensions differ from the cataloged version") }
                require(input.dataType().name in setOf("UINT8","FLOAT32")) { tr("Type d’entrée non prévu pour cette entrée de catalogue", "Input type not supported by this catalog entry") }
                val md=requireNotNull(metadata.getInputTensorMetadata(0)) { tr("Métadonnées du tenseur d’entrée absentes", "Input tensor metadata missing") }
                val norms=(0 until md.processUnitsLength()).mapNotNull { index ->
                    val unit=md.processUnits(index) ?: return@mapNotNull null
                    if(unit.optionsType()==ProcessUnitOptions.NormalizationOptions) unit.options(NormalizationOptions()) as? NormalizationOptions else null
                }
                require(norms.size<=1)
                require(input.dataType().name!="FLOAT32" || norms.size==1) { tr("Normalisation FLOAT32 non spécifiée", "FLOAT32 normalization unspecified") }
                val norm=norms.singleOrNull()
                val mean=norm?.let{n->List(n.meanLength()){n.mean(it)}} ?: listOf(0f)
                val std=norm?.let{n->List(n.stdLength()){n.std(it)}} ?: listOf(1f)
                require(mean.size in setOf(1,3) && std.size==mean.size && std.all{it.isFinite() && it>0})
                val labelOutput=if(entry.family=="ssd")1 else 0
                if(entry.family=="ssd") {
                    require(interpreter.outputTensorCount==4) { tr("Quatre sorties DetectionPostProcess requises", "Four DetectionPostProcess outputs required") }
                    val shapes=(0..3).map{interpreter.getOutputTensor(it).shape().toList()}
                    require(shapes[0].size==3 && shapes[0][0]==1 && shapes[0][2]==4)
                    require(shapes[1]==listOf(1,shapes[0][1]) && shapes[2]==shapes[1] && shapes[3]==listOf(1))
                    require((0..3).all{interpreter.getOutputTensor(it).dataType().name=="FLOAT32"})
                    // Only the known DetectionPostProcess order is supported; ambiguous tensors are rejected.
                    val className=metadata.getOutputTensorMetadata(1)?.name()?.lowercase().orEmpty()
                    val scoreName=metadata.getOutputTensorMetadata(2)?.name()?.lowercase().orEmpty()
                    require(className in setOf("category","classes","detection_classes") && scoreName in setOf("score","scores","detection_scores")) {
                        tr("Sémantique des sorties différente; contrat manuel requis", "Output semantics differ; a manual contract is required")
                    }
                } else require(interpreter.outputTensorCount==1)
                val outputMd=requireNotNull(metadata.getOutputTensorMetadata(labelOutput)) { tr("Métadonnées de sortie des labels absentes", "Output label metadata missing") }
                val type=if(entry.family=="ssd")AssociatedFileType.TENSOR_VALUE_LABELS else AssociatedFileType.TENSOR_AXIS_LABELS
                val associated=(0 until outputMd.associatedFilesLength()).mapNotNull{outputMd.associatedFiles(it)}
                    .firstOrNull{it.type()==type && it.locale().isNullOrBlank()} ?: error(tr("Étiquettes indexées absentes; aucun vocabulaire inventé", "Indexed labels missing; vocabulary is never fabricated"))
                val raw=ByteArrayOutputStream().also { out -> metadata.getAssociatedFile(associated.name() ?: error(tr("Nom de labels absent", "Label name missing"))).use {
                    DurableFiles.copyBounded(it,out,1024L*1024)
                } }.toString("UTF-8").removeSuffix("\n").split('\n').map{it.trimEnd('\r')}
                require(raw.size in 1..10000 && raw.all{it.length<=512})
                // Preserve every numeric slot. Reserved/duplicate labels get an explicit numeric suffix, never a shifted index.
                val labels=raw.mapIndexed { index,value ->
                    if(value.isBlank() || value=="???" || raw.count{it==value}>1) "${value.ifBlank{"reserved"}} [class_$index]" else value
                }
                if(entry.family=="classification") {
                    val shape=interpreter.getOutputTensor(0).shape()
                    require(shape[0]==1 && shape.fold(1L){a,v->a*v}==labels.size.toLong()) { tr("Nombre de labels incompatible", "Incompatible label count") }
                }
                val config=ModelConfig(task=if(entry.family=="ssd")"object_detection" else "classification",adapter=entry.family,
                    inputWidth=entry.size,inputHeight=entry.size,inputType=input.dataType().name,labels=labels,
                    mean=mean.first(),std=std.first(),channelMean=if(mean.size==3)mean else emptyList(),channelStd=if(std.size==3)std else emptyList(),
                    quantizationMode="raw",resizeMode="stretch",threshold=if(entry.family=="ssd")0.35f else 0.1f,
                    scoreActivation="none")
                ModelContract.validate(config)
                return Inspection(config,tr("Contrat et métadonnées inspectés à l’import. Essai sur image requis; précision non mesurée.\n", "Contract and metadata inspected on import. Image trial required; accuracy not measured.\n")+
                    tr("Source: ${entry.sourcePage}\nLicence déclarée dans les métadonnées: ${metadata.modelMetadata.license() ?: "non précisée; vérifier à la source"}", "Source: ${entry.sourcePage}\nLicense declared in metadata: ${metadata.modelMetadata.license() ?: "unspecified; check the source"}"))
            }
        }
    }
}
