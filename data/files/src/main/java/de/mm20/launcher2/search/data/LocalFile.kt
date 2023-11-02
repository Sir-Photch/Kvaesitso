package de.mm20.launcher2.search.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.location.Geocoder
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.format.DateUtils
import android.util.Size
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.files.R
import de.mm20.launcher2.icons.*
import de.mm20.launcher2.ktx.formatToString
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.media.ThumbnailUtilsCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.File as JavaIOFile

data class LocalFile(
    val id: Long,
    override val path: String,
    override val mimeType: String,
    override val size: Long,
    override val isDirectory: Boolean,
    override val metaData: List<Pair<Int, String>>,
    override val labelOverride: String? = null
) : File {

    override val label = path.substringAfterLast('/')

    override fun overrideLabel(label: String): LocalFile {
        return this.copy(labelOverride = label)
    }

    override val domain: String = Domain

    override val key = "$domain://$path"

    override val isStoredInCloud = false

    override suspend fun loadIcon(
        context: Context,
        size: Int,
        themed: Boolean,
    ): LauncherIcon? {
        if (!JavaIOFile(path).exists()) return null
        when {
            mimeType.startsWith("image/") -> {
                val thumbnail = withContext(Dispatchers.IO) {
                    ThumbnailUtils.extractThumbnail(
                        BitmapFactory.decodeFile(path),
                        size, size
                    )
                } ?: return null

                return StaticLauncherIcon(
                    foregroundLayer = StaticIconLayer(
                        icon = BitmapDrawable(context.resources, thumbnail),
                        scale = 1f,
                    ),
                    backgroundLayer = ColorLayer()
                )
            }
            mimeType.startsWith("video/") -> {
                val thumbnail = withContext(Dispatchers.IO) {
                    ThumbnailUtilsCompat.createVideoThumbnail(
                        JavaIOFile(path),
                        Size(size, size)
                    )
                } ?: return null

                return StaticLauncherIcon(
                    foregroundLayer = StaticIconLayer(
                        icon = BitmapDrawable(context.resources, thumbnail),
                        scale = 1f,
                    ),
                    backgroundLayer = ColorLayer()
                )
            }
            mimeType.startsWith("audio/") -> {
                val thumbnail = withContext(Dispatchers.IO) {
                    val mediaMetadataRetriever = MediaMetadataRetriever()
                    try {
                        mediaMetadataRetriever.setDataSource(path)
                        val thumbData = mediaMetadataRetriever.embeddedPicture
                        if (thumbData != null) {
                            val thumbnail = ThumbnailUtils.extractThumbnail(
                                BitmapFactory.decodeByteArray(thumbData, 0, thumbData.size),
                                size,
                                size
                            )
                            mediaMetadataRetriever.release()
                            return@withContext thumbnail
                        }
                    } catch (e: RuntimeException) {
                    }
                    mediaMetadataRetriever.release()
                    return@withContext null

                }
                thumbnail ?: return null

                return StaticLauncherIcon(
                    foregroundLayer = StaticIconLayer(
                        icon = BitmapDrawable(context.resources, thumbnail),
                        scale = 1f,
                    ),
                    backgroundLayer = ColorLayer()
                )

            }
            mimeType == "application/vnd.android.package-archive" -> {
                val pkgInfo = context.packageManager.getPackageArchiveInfo(path, 0)
                val icon = withContext(Dispatchers.IO) {
                    pkgInfo?.applicationInfo?.loadIcon(context.packageManager)
                } ?: return null
                when (icon) {
                    is AdaptiveIconDrawable -> {
                        return StaticLauncherIcon(
                            foregroundLayer = icon.foreground?.let {
                                StaticIconLayer(
                                    icon = it,
                                    scale = 1.5f,
                                )
                            } ?: TransparentLayer,
                            backgroundLayer = icon.background?.let {
                                StaticIconLayer(
                                    icon = it,
                                    scale = 1.5f,
                                )
                            } ?: TransparentLayer,
                        )
                    }
                    else -> {
                        return StaticLauncherIcon(
                            foregroundLayer = StaticIconLayer(
                                icon = icon,
                                scale = 0.7f,
                            ),
                            backgroundLayer = ColorLayer()
                        )
                    }
                }
            }
        }
        return null
    }


    private fun getLaunchIntent(context: Context): Intent {
        val uri = if (isDirectory) {
            Uri.parse(path)
        } else {
            FileProvider.getUriForFile(
                context,
                context.applicationContext.packageName + ".fileprovider", JavaIOFile(path)
            )
        }
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    override fun launch(context: Context, options: Bundle?): Boolean {
        if (context.tryStartActivity(getLaunchIntent(context), options))
            return true

        // startsWith allows path to end with a slash
        if (isDirectory && path.startsWith("/storage/emulated/0/Download")) {
            val aospViewDownloadsIntent = Intent()
                .setComponent(ComponentName("com.android.documentsui", "com.android.documentsui.ViewDownloadsActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)

            return context.tryStartActivity(aospViewDownloadsIntent, options)
        }

        return false
    }

    override val isDeletable: Boolean
        get() {
            val file = java.io.File(path)
            return file.canWrite() && file.parentFile?.canWrite() == true
        }

    override suspend fun delete(context: Context) {
        super.delete(context)

        val file = java.io.File(path)

        withContext(Dispatchers.IO) {
            file.deleteRecursively()

            context.contentResolver.delete(
                MediaStore.Files.getContentUri("external"),
                "${MediaStore.Files.FileColumns._ID} = ?",
                arrayOf(id.toString())
            )
        }
    }


    companion object {

        const val Domain = "file"

        internal fun getMimetypeByFileExtension(extension: String): String {
            return when (extension) {
                "apk" -> "application/vnd.android.package-archive"
                "zip" -> "application/zip"
                "jar" -> "application/java-archive"
                "txt" -> "text/plain"
                "js" -> "text/javascript"
                "html", "htm" -> "text/html"
                "css" -> "text/css"
                "gif" -> "image/gif"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "bmp" -> "image/bmp"
                "webp" -> "image/webp"
                "ico" -> "image/x-icon"
                "midi" -> "audio/midi"
                "mp3" -> "audio/mpeg3"
                "webm" -> "audio/webm"
                "ogg" -> "audio/ogg"
                "wav" -> "audio/wav"
                "mp4" -> "video/mp4"
                "kvaesitso" -> "application/vnd.de.mm20.launcher2.backup"
                "kvtheme" -> "application/vnd.de.mm20.launcher2.theme"
                else -> "application/octet-stream"
            }
        }


        internal fun getMetaData(
            context: Context,
            mimeType: String,
            path: String
        ): List<Pair<Int, String>> {
            val metaData = mutableListOf<Pair<Int, String>>()
            when {
                mimeType.startsWith("audio/") -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(path)
                        arrayOf(
                            R.string.file_meta_title to MediaMetadataRetriever.METADATA_KEY_TITLE,
                            R.string.file_meta_artist to MediaMetadataRetriever.METADATA_KEY_ARTIST,
                            R.string.file_meta_album to MediaMetadataRetriever.METADATA_KEY_ALBUM,
                            R.string.file_meta_year to MediaMetadataRetriever.METADATA_KEY_YEAR
                        ).forEach {
                            retriever.extractMetadata(it.second)
                                ?.let { m -> metaData.add(it.first to m) }
                        }
                        val duration =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                ?.toLong() ?: 0
                        val d = DateUtils.formatElapsedTime((duration) / 1000)
                        metaData.add(3, R.string.file_meta_duration to d)
                        retriever.release()
                    } catch (e: RuntimeException) {
                        retriever.release()
                    }
                }
                mimeType.startsWith("video/") -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(path)
                        val width =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                                ?.toLong() ?: 0
                        val height =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                                ?.toLong() ?: 0
                        metaData.add(R.string.file_meta_dimensions to "${width}x$height")
                        val duration =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                ?.toLong() ?: 0
                        val d = DateUtils.formatElapsedTime(duration / 1000)
                        metaData.add(R.string.file_meta_duration to d)
                        val loc =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                        if (Geocoder.isPresent() && loc != null) {
                            val lon =
                                loc.substring(0, loc.lastIndexOfAny(charArrayOf('+', '-')))
                                    .toDouble()
                            val lat = loc.substring(
                                loc.lastIndexOfAny(charArrayOf('+', '-')),
                                loc.indexOf('/')
                            ).toDouble()
                            val list = Geocoder(context).getFromLocation(lon, lat, 1)
                            if (list != null && list.size > 0) {
                                metaData.add(R.string.file_meta_location to list[0].formatToString())
                            }
                        }
                        retriever.release()
                    } catch (e: RuntimeException) {
                        CrashReporter.logException(e)
                    } catch (e: IOException) {
                        CrashReporter.logException(e)
                    } finally {
                        retriever.release()
                    }
                }
                mimeType.startsWith("image/") -> {
                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    BitmapFactory.decodeFile(path, options)
                    val width = options.outWidth
                    val height = options.outHeight
                    metaData.add(R.string.file_meta_dimensions to "${width}x$height")
                    try {
                        val exif = ExifInterface(path)
                        val loc = exif.latLong
                        if (loc != null && Geocoder.isPresent()) {
                            val list = Geocoder(context).getFromLocation(loc[0], loc[1], 1)
                            if (list != null && list.size > 0) {
                                metaData.add(R.string.file_meta_location to list[0].formatToString())
                            }
                        }
                    } catch (_: IOException) {

                    }
                }
                mimeType == "application/vnd.android.package-archive" -> {
                    val pkgInfo = context.packageManager.getPackageArchiveInfo(path, 0)
                        ?: return metaData
                    metaData.add(
                        R.string.file_meta_app_name to pkgInfo.applicationInfo.loadLabel(
                            context.packageManager
                        ).toString()
                    )
                    metaData.add(R.string.file_meta_app_pkgname to pkgInfo.packageName)
                    metaData.add(R.string.file_meta_app_version to pkgInfo.versionName)
                    metaData.add(R.string.file_meta_app_min_sdk to pkgInfo.applicationInfo.minSdkVersion.toString())
                }
            }
            return metaData
        }
    }

    override val canShare: Boolean
        get() = !isDirectory

    override fun share(context: Context) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val uri = FileProvider.getUriForFile(
            context,
            context.applicationContext.packageName + ".fileprovider",
            java.io.File(path)
        )
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        shareIntent.type = mimeType
        context.startActivity(Intent.createChooser(shareIntent, null))
    }
}