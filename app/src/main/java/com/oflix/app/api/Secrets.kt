package com.oflix.app.api

object Secrets {
    init {
        System.loadLibrary("oflix_secrets")
    }

    external fun getApiBaseUrl(): String
    external fun getFallbackBaseUrl(): String
    external fun getMovieBoxUrl(): String
}
