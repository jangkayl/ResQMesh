package edu.cit.resqmesh.feature.comms.ui.components.composer

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import edu.cit.resqmesh.R
import edu.cit.resqmesh.core.audio.ImageQuality
import edu.cit.resqmesh.core.audio.MediaHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** The composer's image actions and the level of the pending image. */
internal class ComposerImageController(
    val quality: ImageQuality,
    val encoding: Boolean,
    val pickFromGallery: () -> Unit,
    val takePhoto: () -> Unit,
    val selectQuality: (ImageQuality) -> Unit
)

/**
 * Keeps the pending image's source so a level change re-encodes from the original, not from a shrunken copy.
 * Camera captures are temp files, removed once the pending image is sent, cleared or replaced.
 */
@Suppress("CyclomaticComplexMethod")
@Composable
internal fun rememberComposerImage(
    mediaHelper: MediaHelper,
    pendingImage: String?,
    onImageSelected: (String, ImageQuality) -> Unit
): ComposerImageController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by rememberSaveable { mutableStateOf<Uri?>(null) }
    var captureTarget by rememberSaveable { mutableStateOf<Uri?>(null) }
    var quality by rememberSaveable { mutableStateOf(ImageQuality.STANDARD) }
    var encoding by remember { mutableStateOf(false) }
    var encodeJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(pendingImage) {
        if (pendingImage == null) {
            // A level change still encoding must not restore an image the user just removed.
            encodeJob?.cancel()
            mediaHelper.deleteCapture(source)
            source = null
            quality = ImageQuality.STANDARD
        }
    }

    fun load(uri: Uri, level: ImageQuality) {
        encodeJob = scope.launch {
            encoding = true
            val image = try {
                withContext(Dispatchers.Default) { mediaHelper.compressImageUriToBase64(uri, level) }
            } finally {
                encoding = false
            }
            if (image != null) {
                if (source != uri) mediaHelper.deleteCapture(source)
                source = uri
                quality = level
                onImageSelected(image, level)
            } else if (source != uri) {
                mediaHelper.deleteCapture(uri)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { load(it, ImageQuality.STANDARD) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = captureTarget
        captureTarget = null
        if (uri != null) {
            if (saved) load(uri, ImageQuality.STANDARD) else mediaHelper.deleteCapture(uri)
        }
    }

    fun showUnavailable(message: Int) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    return ComposerImageController(
        quality = quality,
        encoding = encoding,
        pickFromGallery = {
            try {
                galleryLauncher.launch("image/*")
            } catch (_: ActivityNotFoundException) {
                showUnavailable(R.string.composer_gallery_unavailable)
            }
        },
        takePhoto = {
            try {
                val uri = mediaHelper.createCaptureUri(keep = source)
                captureTarget = uri
                cameraLauncher.launch(uri)
            } catch (_: ActivityNotFoundException) {
                mediaHelper.deleteCapture(captureTarget)
                captureTarget = null
                showUnavailable(R.string.composer_camera_unavailable)
            } catch (_: IOException) {
                showUnavailable(R.string.composer_camera_unavailable)
            }
        },
        selectQuality = { level -> source?.let { if (level != quality && !encoding) load(it, level) } }
    )
}
