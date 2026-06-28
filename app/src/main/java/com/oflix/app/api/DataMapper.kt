package com.oflix.app.api

import com.google.gson.JsonObject
import com.oflix.app.data.MediaItem

object DataMapper {

    fun parseRanking(response: JsonObject?): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        if (response == null || !response.has("data")) return items

        try {
            val dataObj = response.get("data").asJsonObject
            
            // Try to extract from list array
            if (dataObj.has("list") && dataObj.get("list").isJsonArray) {
                val listArray = dataObj.get("list").asJsonArray
                for (i in 0 until listArray.size()) {
                    val itemObj = listArray.get(i).asJsonObject
                    
                    val title = if (itemObj.has("title")) itemObj.get("title").asString else ""
                    val id = if (itemObj.has("detailPath")) itemObj.get("detailPath").asString else if (itemObj.has("subjectId")) itemObj.get("subjectId").asString else ""
                    
                    var coverUrl = ""
                    if (itemObj.has("cover") && itemObj.get("cover").isJsonObject) {
                        val coverObj = itemObj.get("cover").asJsonObject
                        if (coverObj.has("url")) coverUrl = coverObj.get("url").asString
                    }
                    
                    val year = if (itemObj.has("releaseDate")) itemObj.get("releaseDate").asString.take(4) else ""
                    val rating = if (itemObj.has("imdbRatingValue")) itemObj.get("imdbRatingValue").asString else ""
                    
                    val isSeries = if (itemObj.has("subjectType")) itemObj.get("subjectType").asInt == 2 else false
                    val typeStr = if (isSeries) "Series" else "Film"
                    
                    if (id.isNotEmpty() && title.isNotEmpty()) {
                        items.add(
                            MediaItem(
                                id = id,
                                title = title,
                                posterUrl = optimizePoster(coverUrl),
                                type = typeStr,
                                year = year,
                                rating = rating
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return items
    }

    private fun optimizePoster(url: String): String {
        if (url.isEmpty()) return ""
        return "https://funny-kitten-ad51d6.netlify.app/img?url=${java.net.URLEncoder.encode(url, "UTF-8")}&w=400&q=70"
    }
}
