package io.github.tejesh_zephaniah.folio.core.storage

import android.content.Context
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import org.json.JSONObject
import java.io.File

object ReadiumManager {
    private const val TAG = "ReadiumManager"

    suspend fun openPublication(context: Context, file: File): Publication? {
        val appContext = context.applicationContext
        if (!file.exists() || !file.canRead()) {
            android.util.Log.e(TAG, "Cannot open publication: file does not exist or is unreadable: ${file.absolutePath}")
            return null
        }

        val httpClient = DefaultHttpClient()
        val assetRetriever = AssetRetriever(appContext.contentResolver, httpClient)
        val assetResult = assetRetriever.retrieve(file)
        val asset = assetResult.getOrNull()
        if (asset == null) {
            val err = (assetResult as? org.readium.r2.shared.util.Try.Failure)?.value
            android.util.Log.e(TAG, "AssetRetriever failed for ${file.absolutePath}: $err")
            return null
        }

        var publication: Publication? = null
        try {
            val parser = DefaultPublicationParser(
                context = appContext,
                httpClient = httpClient,
                assetRetriever = assetRetriever,
                pdfFactory = PdfiumDocumentFactory(appContext)
            )
            val pubResult = PublicationOpener(parser).open(asset, allowUserInteraction = false)
            publication = pubResult.getOrNull()
            if (publication == null) {
                val err = (pubResult as? org.readium.r2.shared.util.Try.Failure)?.value
                android.util.Log.e(TAG, "PublicationOpener failed for ${file.absolutePath}: $err")
            }
            return publication
        } finally {
            if (publication == null) asset.close()
        }
    }

    fun parseLocatorJson(jsonString: String?): Locator? {
        if (jsonString.isNullOrBlank()) return null
        return try {
            Locator.fromJSON(JSONObject(jsonString))
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Could not parse saved reading location", e)
            null
        }
    }

    fun locatorToJson(locator: Locator?): String? {
        return locator?.toJSON()?.toString()
    }
}
