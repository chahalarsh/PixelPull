package com.pixelpull.app

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

object WallpaperService {
    
    suspend fun downloadAndSetWallpaper(context: Context, url: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Download image
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .build()

                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Failed to download image: ${response.code}")
                    )
                }

                val imageBytes = response.body?.bytes()
                    ?: return@withContext Result.failure(
                        IOException("Empty response body")
                    )

                // Convert to bitmap
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    ?: return@withContext Result.failure(
                        IOException("Failed to decode image")
                    )

                // Set wallpaper
                val wallpaperManager = WallpaperManager.getInstance(context)
                wallpaperManager.setBitmap(bitmap)

                Result.success("Wallpaper updated successfully")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
