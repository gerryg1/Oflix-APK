package com.oflix.app.api

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class AuthInterceptor : Interceptor {
    private var cachedCookies: String = ""
    private var tokenTime: Long = 0
    private val TOKEN_TTL = 30 * 60 * 1000L // 30 minutes
    private val lock = ReentrantLock()
    
    // Separate OkHttpClient for auth fetching to prevent infinite loops
    private val authClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun intercept(chain: Interceptor.Chain): Response {
        val cookies = ensureCookies()
        
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept", "application/json")
            .header("X-Client-Info", "{\"timezone\":\"Asia/Bangkok\"}")
            .header("X-Source", "web")
            .header("Cookie", cookies)

        // Extract token for Authorization header
        val tokenMatch = Regex("token=([^;]+)").find(cookies)
        if (tokenMatch != null) {
            requestBuilder.header("Authorization", "Bearer ${tokenMatch.groupValues[1]}")
        }

        val request = requestBuilder.build()
        var response = chain.proceed(request)

        // Retry logic on 401, 429, or 400
        if (response.code == 401 || response.code == 429 || response.code == 400) {
            response.close()
            
            // Force refresh cookies
            val freshCookies = ensureCookies(forceRefresh = true)
            
            val retryBuilder = originalRequest.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .header("X-Client-Info", "{\"timezone\":\"Asia/Bangkok\"}")
                .header("X-Source", "web")
                .header("Cookie", freshCookies)
                
            val freshTokenMatch = Regex("token=([^;]+)").find(freshCookies)
            if (freshTokenMatch != null) {
                retryBuilder.header("Authorization", "Bearer ${freshTokenMatch.groupValues[1]}")
            }
                
            response = chain.proceed(retryBuilder.build())
        }
        
        return response
    }

    private fun ensureCookies(forceRefresh: Boolean = false): String {
        lock.withLock {
            if (!forceRefresh && cachedCookies.isNotEmpty() && (System.currentTimeMillis() - tokenTime < TOKEN_TTL)) {
                return cachedCookies
            }
            
            try {
                var currentCookies = ""
                
                // Step 1: Visit movieboxapp.in
                val req1 = Request.Builder()
                    .url(Secrets.getMovieBoxUrl())
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .build()
                
                val res1 = authClient.newCall(req1).execute()
                currentCookies = mergeCookies(currentCookies, extractCookies(res1))
                res1.close()

                // Step 2: Try h5-api if token not found
                if (!currentCookies.contains("token=")) {
                    val req2 = Request.Builder()
                        .url(Secrets.getApiBaseUrl() + "app/get-latest-app-pkgs?app_name=moviebox")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Cookie", currentCookies)
                        .build()
                    val res2 = authClient.newCall(req2).execute()
                    currentCookies = mergeCookies(currentCookies, extractCookies(res2))
                    res2.close()
                }

                // Append language if missing
                if (!currentCookies.contains("i18n_lang=")) {
                    currentCookies = if (currentCookies.isNotEmpty()) "$currentCookies; i18n_lang=id" else "i18n_lang=id"
                }

                if (currentCookies.contains("token=")) {
                    cachedCookies = currentCookies
                    tokenTime = System.currentTimeMillis()
                }
                
                return currentCookies.ifEmpty { cachedCookies.ifEmpty { "i18n_lang=id" } }
            } catch (e: Exception) {
                e.printStackTrace()
                return cachedCookies.ifEmpty { "i18n_lang=id" }
            }
        }
    }

    private fun extractCookies(response: Response): String {
        val headers = response.headers("set-cookie")
        return headers.joinToString("; ") { it.split(";")[0] }
    }

    private fun mergeCookies(c1: String, c2: String): String {
        val map = mutableMapOf<String, String>()
        listOf(c1, c2).forEach { s ->
            if (s.isNotEmpty()) {
                s.split(";").forEach { pair ->
                    val parts = pair.trim().split("=")
                    if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                        map[parts[0]] = parts.drop(1).joinToString("=")
                    }
                }
            }
        }
        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }
}
