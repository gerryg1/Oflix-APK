package com.oflix.app.api

import com.google.gson.JsonObject

/**
 * Handles fetching stream URLs from the play and download endpoints.
 * Mirrors the logic from api/play/route.js and lib/moviebox.js transformStream().
 */
object StreamRepository {

    data class StreamResult(
        val success: Boolean,
        val videoUrl: String = "",
        val downloads: List<DownloadOption> = emptyList(),
        val error: String = ""
    )

    data class DownloadOption(
        val url: String,
        val resolution: Int,
        val label: String
    )

    /**
     * Fetch stream for a given subjectId + season/episode.
     * Tries netnaija.film first (as requested), then fallback to aoneroom.
     */
    suspend fun fetchStream(subjectId: String, se: String, ep: String, detailPath: String): StreamResult {
        val aoneroomReferer = if (detailPath.isNotEmpty())
            "https://themoviebox.org/movies/$detailPath?id=$subjectId&type=/movie/detail&detailSe=&detailEp=&lang=en"
        else "https://themoviebox.org/"
        
        val netnaijaReferer = if (detailPath.isNotEmpty())
            "https://netnaija.film/spa/videoPlayPage/movies/$detailPath?id=$subjectId&type=/movie/detail&detailSe=&detailEp=&lang=en"
        else "https://netnaija.film/"

        // Try primary (netnaija.film - currently more reliable for play)
        try {
            val netnaijaApi = ApiClient.getFallbackClient()
            val playRes = netnaijaApi.getPlay(referer = netnaijaReferer, id = subjectId, subjectId = subjectId, se = se, ep = ep)
            val dlRes = netnaijaApi.getDownload(referer = netnaijaReferer, id = subjectId, subjectId = subjectId, se = se, ep = ep)

            val playData = if (playRes.isSuccessful && playRes.body()?.get("code")?.asInt == 0)
                playRes.body()?.getAsJsonObject("data") else null
            val dlData = if (dlRes.isSuccessful && dlRes.body()?.get("code")?.asInt == 0)
                dlRes.body()?.getAsJsonObject("data") else null

            val result = transformStream(playData, dlData)
            if (result.success) return result
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Try fallback (aoneroom)
        try {
            val aoneroomApi = ApiClient.getClient()
            val playRes = aoneroomApi.getPlay(referer = aoneroomReferer, id = subjectId, subjectId = subjectId, se = se, ep = ep)
            val dlRes = aoneroomApi.getDownload(referer = aoneroomReferer, id = subjectId, subjectId = subjectId, se = se, ep = ep)

            val playData = if (playRes.isSuccessful && playRes.body()?.get("code")?.asInt == 0)
                playRes.body()?.getAsJsonObject("data") else null
            val dlData = if (dlRes.isSuccessful && dlRes.body()?.get("code")?.asInt == 0)
                dlRes.body()?.getAsJsonObject("data") else null

            return transformStream(playData, dlData)
        } catch (e: Exception) {
            e.printStackTrace()
            return StreamResult(success = false, error = e.message ?: "Stream fetch failed")
        }
    }

    /**
     * Mirrors transformStream() from lib/moviebox.js
     */
    private fun transformStream(playData: JsonObject?, dlData: JsonObject?): StreamResult {
        val downloads = mutableListOf<DownloadOption>()

        // From download endpoint
        if (dlData?.has("downloads") == true && dlData.get("downloads").isJsonArray) {
            for (dl in dlData.getAsJsonArray("downloads")) {
                val dlObj = dl.asJsonObject
                val url = dlObj.get("url")?.asString ?: continue
                val res = try { dlObj.get("resolution")?.asString?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 0 } catch (_: Exception) { 0 }
                downloads.add(DownloadOption(url = url, resolution = res, label = if (res > 0) "${res}p" else "Auto"))
            }
        }

        // From play endpoint streams
        if (playData?.has("streams") == true && playData.get("streams").isJsonArray) {
            for (st in playData.getAsJsonArray("streams")) {
                val stObj = st.asJsonObject
                val url = stObj.get("url")?.asString ?: continue
                val res = try {
                    (stObj.get("resolutions")?.asString ?: stObj.get("resolution")?.asString ?: "0")
                        .replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                } catch (_: Exception) { 0 }
                downloads.add(DownloadOption(url = url, resolution = res, label = if (res > 0) "${res}p" else "Auto"))
            }
        }

        // Fallback: dlData.list
        if (dlData?.has("list") == true && dlData.get("list").isJsonArray) {
            for (d in dlData.getAsJsonArray("list")) {
                val dObj = d.asJsonObject
                val url = dObj.get("url")?.asString ?: dObj.get("path")?.asString ?: continue
                val quality = dObj.get("quality")?.asString ?: ""
                val res = quality.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                downloads.add(DownloadOption(url = url, resolution = res, label = quality.ifEmpty { "Auto" }))
            }
        }

        // Dedup by resolution (keep first), sort desc
        val seen = mutableSetOf<Int>()
        val unique = downloads.filter { seen.add(it.resolution) }.sortedByDescending { it.resolution }

        var mainUrl = unique.firstOrNull()?.url ?: ""

        // Check HLS from play data
        if (playData?.has("hls") == true && playData.get("hls").isJsonArray) {
            val hlsArr = playData.getAsJsonArray("hls")
            if (hlsArr.size() > 0) {
                val hlsEl = hlsArr.get(0)
                val hlsUrl = if (hlsEl.isJsonObject) hlsEl.asJsonObject.get("url")?.asString ?: ""
                             else if (hlsEl.isJsonPrimitive) hlsEl.asString else ""
                if (hlsUrl.isNotEmpty()) mainUrl = hlsUrl
            }
        }

        // Pick best quality >= 480p for default
        if (mainUrl.isEmpty() && unique.isNotEmpty()) {
            val best = unique.lastOrNull { it.resolution >= 480 } ?: unique.first()
            mainUrl = best.url
        }

        val hasContent = mainUrl.isNotEmpty() || unique.isNotEmpty()
        return StreamResult(
            success = hasContent,
            videoUrl = mainUrl,
            downloads = unique,
            error = if (hasContent) "" else "No streams available"
        )
    }
}
