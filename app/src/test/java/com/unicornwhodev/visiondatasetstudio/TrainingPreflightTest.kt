package com.unicornwhodev.visiondatasetstudio

import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import com.unicornwhodev.visiondatasetstudio.domain.training.*
import org.junit.Assert.*
import org.junit.Test

class TrainingPreflightTest {
    private val training=TrainingContract(scope="classification_head_only",targetShape=listOf(2),inferOutputs=listOf("output"))
    private fun input(config:ModelConfig?=ModelConfig(labels=listOf("a","b"),training=training),verified:Boolean=true,train:Int=32,validation:Int=8,available:Long=100,already:Boolean=false)=
        TrainingPreflightInput(config,verified,train,validation,50,available,true,true,already)
    @Test fun completePreflight() { assertTrue(TrainingPreflight.evaluate(input()).canStart) }
    @Test fun modelWithoutSignatures() { assertFalse(TrainingPreflight.evaluate(input(config=ModelConfig(labels=listOf("a")))).canStart) }
    @Test fun unverifiedBatch() { assertFalse(TrainingPreflight.evaluate(input(verified=false)).canStart) }
    @Test fun insufficientDataset() { assertFalse(TrainingPreflight.evaluate(input(train=31,validation=7)).canStart) }
    @Test fun insufficientStorage() { assertFalse(TrainingPreflight.evaluate(input(available=49)).canStart) }
    @Test fun alreadyTrainedExport() { assertFalse(TrainingPreflight.evaluate(input(already=true)).canStart) }
    @Test fun configuredCheckpointMustBeAvailable() { assertFalse(TrainingPreflight.evaluate(input().copy(checkpointAvailable=false)).canStart) }
    @Test fun modelPathAndFileAreCheckedSeparately() {
        assertFalse(TrainingPreflight.evaluate(input().copy(modelPathConfigured=false)).canStart)
        assertFalse(TrainingPreflight.evaluate(input().copy(modelFileAvailable=false)).canStart)
        assertFalse(TrainingPreflight.evaluate(input().copy(modelFileReadable=false)).canStart)
    }
}
