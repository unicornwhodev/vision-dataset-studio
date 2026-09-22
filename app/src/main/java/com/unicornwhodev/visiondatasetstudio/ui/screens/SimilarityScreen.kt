package com.unicornwhodev.visiondatasetstudio.ui.screens

import androidx.compose.ui.res.stringResource
import com.unicornwhodev.visiondatasetstudio.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.unicornwhodev.visiondatasetstudio.ui.*
import com.unicornwhodev.visiondatasetstudio.ui.components.StudioTopBar
import java.io.File

@Composable fun SimilarityScreen(vm:MainViewModel) {
    val rows by vm.similarImages.collectAsState()
    Scaffold(contentWindowInsets=WindowInsets(0),topBar={StudioTopBar(stringResource(R.string.screen_similarity),stringResource(R.string.subtitle_same_model),onBack=vm::back)}){inset->
        LazyColumn(Modifier.fillMaxSize().padding(inset),contentPadding=PaddingValues(16.dp)) {
            if(rows.isEmpty())item{Text("Aucune autre représentation disponible. Prétraitez le lot avec le même encodeur.",style=MaterialTheme.typography.bodyMedium)}
            items(rows,key={it.first.sampleId}){(sample,score)->
                Row(Modifier.fillMaxWidth().clickable{vm.openSampleInEditor(sample.sampleId)}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                    AsyncImage(sample.localImagePath?.let(::File),null,Modifier.size(96.dp,72.dp),contentScale=ContentScale.Fit)
                    Spacer(Modifier.width(12.dp));Text(sample.assetId,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                    Text("%.3f".format(score),style=MaterialTheme.typography.labelMedium)
                };HorizontalDivider()
            }
        }
    }
}
