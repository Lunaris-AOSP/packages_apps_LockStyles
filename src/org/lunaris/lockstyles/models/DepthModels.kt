package org.lunaris.lockstyles.models

import android.content.Context
import org.lunaris.lockstyles.R
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest

internal object DepthModels {
    private data class Model(val name: String, val bytes: Long, val hash: String)
    private val catalog = listOf(
        Model("deep_matting.tflite", 6376932L, "689735629af6b7b9f0110d44f1d5898c060a7addff83dfb772eb355b752e64fe"),
        Model("depth_model.tflite", 66338288L, "93d871071edff1218973ce25ee27ce95ccd20450c70a55e1b89efa3f5a772cdd"),
        Model("foreground_estimator_5680_512_512.tflite", 6063016L, "6c76210110483f9771d7e8a59f222d91790408a23572e158115281f717056b38"),
        Model("mobile_bg_removal_mosaic_dm1_w_metadata.f16.tflite", 14453962L, "3b250307f4cacb131602b34b2f58f6b882e0beb3928f3f3e0421a35c08ea3778"),
    )
    @Volatile var downloadedBytes = 0L
        private set
    val total = catalog.sumOf { it.bytes }
    @Volatile var running = false
        private set
    @Volatile var status = ""
        private set
    private fun directory(ctx: Context) = File(ctx.createDeviceProtectedStorageContext().filesDir, "depth-models")
    private fun target(ctx: Context, model: Model) = File(directory(ctx), model.name)
    fun installed(ctx: Context) = catalog.filter { target(ctx, it).length() == it.bytes }.sumOf { it.bytes }
    fun hasStoredFiles(ctx: Context) = directory(ctx).list()?.isNotEmpty() == true
    fun available(ctx: Context) = installed(ctx) == total
    fun open(ctx: Context, name: String?): File {
        val model = catalog.firstOrNull { it.name == name } ?: throw java.io.FileNotFoundException()
        return target(ctx, model).takeIf { it.length() == model.bytes }
            ?: throw java.io.FileNotFoundException("Download depth wallpaper models first")
    }
    @Synchronized fun download(ctx: Context) {
        if (running) return
        val source = ctx.getString(R.string.depth_model_base_url).trimEnd('/')
        if (!source.startsWith("https://")) {
            status = "The model download source has not been configured."
            return
        }
        downloadedBytes = installed(ctx)
        status = ""
        running = true
        Thread {
            try {
                directory(ctx).mkdirs()
                var done = installed(ctx)
                for (model in catalog) {
                    val file = target(ctx, model)
                    if (file.length() == model.bytes) continue
                    val temp = File(directory(ctx), model.name + ".part")
                    val connection = URL(source + "/" + model.name).openConnection() as javax.net.ssl.HttpsURLConnection
                    try {
                        connection.connectTimeout = 20000
                        connection.readTimeout = 30000
                        val digest = MessageDigest.getInstance("SHA-256")
                        var count = 0L
                        connection.inputStream.use { input ->
                            temp.outputStream().use { out ->
                                val buffer = ByteArray(65536)
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    count += n
                                    if (count > model.bytes) throw IOException("Unexpected model size")
                                    digest.update(buffer, 0, n)
                                    out.write(buffer, 0, n)
                                    downloadedBytes = done + count
                                }
                            }
                        }
                        val hash = digest.digest().joinToString("") { "%02x".format(it) }
                        if (count != model.bytes || hash != model.hash) throw IOException("Model verification failed")
                        if (!temp.renameTo(file)) throw IOException("Could not save model")
                        done += count
                    } finally {
                        connection.disconnect()
                        temp.delete()
                    }
                }
                status = ""
            } catch (e: Exception) {
                status = "Download failed: " + (e.localizedMessage ?: "Retry download")
            } finally {
                running = false
            }
        }.start()
    }
    @Synchronized fun delete(ctx: Context) {
        if (running) return
        status = if (directory(ctx).deleteRecursively()) "" else "Could not delete all models"
    }
}
