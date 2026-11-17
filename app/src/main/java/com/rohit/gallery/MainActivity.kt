package com.rohit.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.rohit.gallery.core.ui.components.LocalThumbnailCache
import com.rohit.gallery.core.ui.components.ThumbnailCache
import com.rohit.gallery.core.ui.theme.GalleryTheme
import com.rohit.gallery.navigation.GalleryNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var thumbnailCache: ThumbnailCache

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalThumbnailCache provides thumbnailCache) {
                GalleryTheme {
                    GalleryNavHost()
                }
            }
        }
    }
}
