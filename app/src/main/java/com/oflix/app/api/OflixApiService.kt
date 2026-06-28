package com.oflix.app.api

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Response
import com.google.gson.JsonObject

interface OflixApiService {

    @GET("web/home")
    suspend fun getHome(): Response<JsonObject>

    @GET("ranking-list/content")
    suspend fun getRanking(
        @Query("id") id: String,
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = 12
    ): Response<JsonObject>

    @GET("detail")
    suspend fun getDetail(
        @Query("detailPath") detailPath: String
    ): Response<JsonObject>

    @GET("subject/play")
    suspend fun getPlay(
        @Query("id") id: String,
        @Query("subjectId") subjectId: String,
        @Query("se") se: String = "0",
        @Query("ep") ep: String = "0",
        @Query("format") format: String = "MP4"
    ): Response<JsonObject>

    @GET("subject/download")
    suspend fun getDownload(
        @Query("id") id: String,
        @Query("subjectId") subjectId: String,
        @Query("se") se: String = "0",
        @Query("ep") ep: String = "0",
        @Query("format") format: String = "MP4"
    ): Response<JsonObject>
}
