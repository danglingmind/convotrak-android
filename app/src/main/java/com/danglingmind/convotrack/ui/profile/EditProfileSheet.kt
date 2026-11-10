package com.danglingmind.convotrack.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.data.net.UpdateNameRequest
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.data.net.riderMessage
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.rememberAvatarPhoto
import com.danglingmind.convotrack.ui.designsystem.Avatar
import com.danglingmind.convotrack.ui.designsystem.DsBottomSheet
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsSpace
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.FieldLabel
import com.danglingmind.convotrack.ui.designsystem.GhostButton
import com.danglingmind.convotrack.ui.designsystem.PrimaryButton
import com.danglingmind.convotrack.ui.designsystem.TextFieldCard
import com.danglingmind.convotrack.ui.designsystem.dsClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

/** What riders may call themselves; matches the server's `NAME_MAX_LENGTH` and iOS. */
private const val NAME_MAX_LENGTH = 40

/** Collapses runs of whitespace and trims — the same normalisation the server applies. */
private fun normaliseName(raw: String): String = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

/**
 * Edit the name and photo other riders see (iOS `EditProfileSheet`).
 *
 * The photo is staged, not uploaded, when picked: Save commits both, Cancel or a swipe away
 * discards both. The system photo picker needs no storage permission.
 */
@Composable
fun EditProfileSheet(
    userId: String,
    initialName: String,
    currentAvatarUrl: String?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val api = remember { ServiceLocator.api }
    val auth = remember { ServiceLocator.auth }

    var name by rememberSaveable { mutableStateOf(initialName) }
    var picked by remember { mutableStateOf<PreparedPhoto?>(null) }
    var loadingPhoto by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val current = rememberAvatarPhoto(currentAvatarUrl)

    val trimmed = normaliseName(name)
    val nameValid = trimmed.isNotEmpty() && trimmed.length <= NAME_MAX_LENGTH

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        loadingPhoto = true
        error = null
        scope.launch {
            val photo = withContext(Dispatchers.Default) { runCatching { prepareAvatar(context, uri) }.getOrNull() }
            if (photo == null) error = "That photo couldn't be read. Try a different one." else picked = photo
            loadingPhoto = false
        }
    }
    fun openPicker() {
        if (saving) return
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun save() {
        if (!nameValid || saving || loadingPhoto) return
        focus.clearFocus()
        saving = true
        error = null
        scope.launch {
            try {
                var changed = false
                picked?.let { photo ->
                    apiCall { api.uploadProfilePhoto(photo.jpeg.toRequestBody("image/jpeg".toMediaType())) }
                    picked = null
                    changed = true
                }
                if (trimmed != initialName) {
                    apiCall { api.updateName(UpdateNameRequest(trimmed)) }
                    changed = true
                }
                // The server wrote Clerk; pull it so the launch-time sync never sends the old values.
                if (changed) auth.reloadUser()
                onSaved()
            } catch (e: Exception) {
                error = e.riderMessage
            } finally {
                saving = false
            }
        }
    }

    DsBottomSheet(onDismiss = { if (!saving) onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = DsSpace.gutter).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "Edit profile",
                style = DsType.cardTitle,
                color = DsColor.textPrimary,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
                textAlign = TextAlign.Center,
            )

            // Photo
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .semantics(mergeDescendants = true) { contentDescription = "Change profile photo"; role = Role.Button }
                        .dsClickable(onClick = ::openPicker),
                ) {
                    val photo = picked?.preview ?: current
                    Avatar(trimmed.ifEmpty { initialName }, size = 96.dp, photo = photo, colorKey = userId, ring = DsColor.accent)
                    if (loadingPhoto) {
                        Box(Modifier.size(96.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = DsColor.textPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        }
                    }
                    AvatarEditBadge(Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp))
                }
                Text(
                    if (picked == null) "Change photo" else "Choose another photo",
                    style = DsType.bodyStrong,
                    color = DsColor.accent,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .dsClickable(pressedScale = 1f, onClick = ::openPicker)
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                )
                Text(
                    "Your photo is visible to riders in your convoys.",
                    style = DsType.helper,
                    color = DsColor.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            // Name
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("NAME")
                TextFieldCard(
                    value = name,
                    onValueChange = { name = it.take(NAME_MAX_LENGTH) },
                    placeholder = "Your name",
                    isError = trimmed.isEmpty(),
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                )
                Text(nameNote(trimmed), style = DsType.helper, color = if (trimmed.isEmpty()) DsColor.danger else DsColor.textSecondary)
            }

            error?.let { Text(it, style = DsType.bodySmall, color = DsColor.danger) }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PrimaryButton(
                    text = "Save",
                    onClick = ::save,
                    enabled = nameValid && !loadingPhoto,
                    loading = saving,
                )
                GhostButton("Cancel", onClick = { if (!saving) onDismiss() })
            }
        }
    }
}

/** Live hint under the name: the rule while empty, a countdown near the limit. */
private fun nameNote(trimmed: String): String {
    if (trimmed.isEmpty()) return "Other riders need a name to see on the map."
    val left = NAME_MAX_LENGTH - trimmed.length
    if (left <= 10) return if (left == 1) "1 character left" else "$left characters left"
    return "Other riders see this name on the map, in lobbies and in ride summaries."
}

/** Camera badge on an avatar that opens photo editing. Decorative; its parent is labelled. */
@Composable
fun AvatarEditBadge(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(DsColor.accent)
            .border(3.dp, DsColor.sheet, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.PhotoCamera, null, tint = DsColor.onAccent, modifier = Modifier.size(16.dp))
    }
}

private class PreparedPhoto(val preview: ImageBitmap, val jpeg: ByteArray)

/**
 * Decodes the picked image upright, centre-crops it square and scales it to 512 px. Avatars
 * render as circles no larger than ~100dp, so anything bigger is upload time nobody sees.
 */
private fun prepareAvatar(context: Context, uri: Uri, side: Int = 512): PreparedPhoto? {
    val resolver = context.contentResolver
    // Bounds first, so a 48 MP photo is subsampled on decode instead of filling the heap.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val shortEdge = minOf(bounds.outWidth, bounds.outHeight)
    if (shortEdge <= 0) return null
    var sample = 1
    while (shortEdge / (sample * 2) >= side) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null

    val rotation = resolver.openInputStream(uri)?.use {
        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f

    val crop = minOf(decoded.width, decoded.height)
    val scale = side.toFloat() / crop
    val matrix = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
    val square = Bitmap.createBitmap(
        decoded, (decoded.width - crop) / 2, (decoded.height - crop) / 2, crop, crop, matrix, true,
    )
    if (square !== decoded) decoded.recycle()
    val jpeg = ByteArrayOutputStream().use { out ->
        square.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray()
    }
    return PreparedPhoto(square.asImageBitmap(), jpeg)
}
