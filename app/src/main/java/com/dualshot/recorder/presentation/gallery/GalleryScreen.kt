package com.dualshot.recorder.presentation.gallery

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dualshot.recorder.domain.model.VideoFile
import com.dualshot.recorder.domain.repository.VideoRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Gallery screen listing recent DualShot recordings from MediaStore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val videos = remember { mutableStateOf<List<VideoFile>>(emptyList()) }

    LaunchedEffect(Unit) {
        val repo = EntryPointAccessors.fromApplication(
            context.applicationContext,
            GalleryEntryPoint::class.java
        ).videoRepository()
        videos.value = repo.listRecentVideos()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Gallery") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(videos.value, key = { it.uri }) { file ->
                Text(
                    text = file.displayName,
                    modifier = Modifier
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(file.uri, "video/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        }
                        .padding(16.dp)
                )
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GalleryEntryPoint {
    fun videoRepository(): VideoRepository
}
