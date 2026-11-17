package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/** Encodes a small, square avatar for the group's member document. */
internal object ProfilePhotoCodec {
  private const val MAX_JPEG_BYTES = 60_000

  fun encode(context: Context, uri: Uri): String {
    val source = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
      ?: throw IllegalArgumentException("Could not read that photo. Please choose another image.")
    val side = minOf(source.width, source.height)
    val crop = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
    source.recycle()

    var dimension = 256
    while (dimension >= 96) {
      val scaled = Bitmap.createScaledBitmap(crop, dimension, dimension, true)
      for (quality in listOf(82, 72, 62, 52)) {
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
        val bytes = output.toByteArray()
        if (bytes.size <= MAX_JPEG_BYTES) {
          scaled.recycle()
          crop.recycle()
          return Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
      }
      if (scaled !== crop) scaled.recycle()
      dimension -= 32
    }
    crop.recycle()
    throw IllegalArgumentException("That photo could not be compressed small enough. Choose another image.")
  }
}
