package com.unicornwhodev.visiondatasetstudio.domain.training

import com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig

data class TrainingPreflightInput(val config:ModelConfig?,val batchVerified:Boolean,val trainCount:Int,val validationCount:Int,
    val requiredBytes:Long,val availableBytes:Long,val targetsCompatible:Boolean,val checkpointAvailable:Boolean,val alreadyTrained:Boolean)
data class TrainingPreflight(val canStart:Boolean,val scope:String?,val checks:List<Check>) {
    data class Check(val label:String,val passed:Boolean,val detail:String)
    companion object {
        fun evaluate(i:TrainingPreflightInput):TrainingPreflight {
            val training=i.config?.training
            val checks=listOf(
                Check("Modèle entraînable",training!=null,if(training==null)"Signatures train / infer / save / restore absentes" else "Signatures disponibles"),
                Check("Lot exporté vérifié",i.batchVerified,if(i.batchVerified)"Copie relue" else "Export vérifié requis"),
                Check("Train",i.trainCount>=32,"${i.trainCount} / 32 minimum"),
                Check("Validation",i.validationCount>=8,"${i.validationCount} / 8 minimum"),
                Check("Stockage",i.availableBytes>=i.requiredBytes,"${i.availableBytes} disponibles / ${i.requiredBytes} nécessaires"),
                Check("Cibles compatibles",i.targetsCompatible,if(i.targetsCompatible)"Contrat compatible" else "Cibles incompatibles avec le contrat"),
                Check("Checkpoint",i.checkpointAvailable,if(i.checkpointAvailable)"Aucun checkpoint requis ou reçu disponible" else "Checkpoint configuré mais indisponible"),
                Check("Nouvel export",!i.alreadyTrained,if(i.alreadyTrained)"Apprentissage déjà effectué sur cet export" else "Aucun apprentissage achevé sur cet export")
            )
            return TrainingPreflight(checks.all{it.passed},training?.scope,checks)
        }
    }
}
