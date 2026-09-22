package com.unicornwhodev.visiondatasetstudio.domain.training

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
import com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig

data class TrainingPreflightInput(val config:ModelConfig?,val batchVerified:Boolean,val trainCount:Int,val validationCount:Int,
    val requiredBytes:Long,val availableBytes:Long,val targetsCompatible:Boolean,val checkpointAvailable:Boolean,val alreadyTrained:Boolean,
    val modelPathConfigured:Boolean=true,val modelFileAvailable:Boolean=true,val modelFileReadable:Boolean=true)
data class TrainingPreflight(val canStart:Boolean,val scope:String?,val checks:List<Check>,val error:String?=null) {
    data class Check(val label:String,val passed:Boolean,val detail:String)
    companion object {
        fun evaluate(i:TrainingPreflightInput,error:String?=null):TrainingPreflight {
            val training=i.config?.training
            val checks=listOf(
                Check(tr("Modèle entraînable", "Trainable model"),training!=null,if(training==null)tr("Signatures train / infer / save / restore absentes", "train / infer / save / restore signatures missing") else tr("Signatures disponibles", "Signatures available")),
                Check(tr("Chemin des poids", "Weights path"),i.modelPathConfigured,if(i.modelPathConfigured)tr("Chemin configuré", "Path configured") else tr("Poids non configurés", "Weights not configured")),
                Check(tr("Fichier des poids", "Weights file"),i.modelFileAvailable,if(i.modelFileAvailable)tr("Fichier présent", "File present") else tr("Fichier absent", "File missing")),
                Check(tr("Lecture des poids", "Weights read access"),i.modelFileReadable,if(i.modelFileReadable)tr("Fichier lisible", "File readable") else tr("Fichier illisible", "File unreadable")),
                Check(tr("Lot exporté vérifié", "Verified exported batch"),i.batchVerified,if(i.batchVerified)tr("Copie relue", "Copy read back") else tr("Export vérifié requis", "Verified export required")),
                Check("Train",i.trainCount>=32,"${i.trainCount} / 32 minimum"),
                Check("Validation",i.validationCount>=8,"${i.validationCount} / 8 minimum"),
                Check(tr("Stockage", "Storage"),i.availableBytes>=i.requiredBytes,tr("${i.availableBytes} disponibles / ${i.requiredBytes} nécessaires", "${i.availableBytes} available / ${i.requiredBytes} required")),
                Check(tr("Cibles compatibles", "Compatible targets"),i.targetsCompatible,if(i.targetsCompatible)tr("Contrat compatible", "Compatible contract") else tr("Cibles incompatibles avec le contrat", "Targets incompatible with the contract")),
                Check("Checkpoint",i.checkpointAvailable,if(i.checkpointAvailable)tr("Aucun checkpoint requis ou reçu disponible", "No checkpoint required or receipt available") else tr("Checkpoint configuré mais indisponible", "Checkpoint configured but unavailable")),
                Check(tr("Nouvel export", "New export"),!i.alreadyTrained,if(i.alreadyTrained)tr("Apprentissage déjà effectué sur cet export", "Training already completed on this export") else tr("Aucun apprentissage achevé sur cet export", "No completed training on this export"))
            )
            return TrainingPreflight(checks.all{it.passed}&&error==null,training?.scope,checks,error)
        }
    }
}
