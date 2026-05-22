package com.example.data

import com.squareup.moshi.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// --- Models ---

data class GithubUser(
    val login: String,
    val id: Long,
    @Json(name = "avatar_url") val avatarUrl: String,
    val name: String?,
    val email: String?
)

data class GithubUserNested(
    val login: String,
    @Json(name = "avatar_url") val avatarUrl: String
)

data class GithubRepo(
    val id: Long,
    val name: String,
    @Json(name = "full_name") val fullName: String,
    val private: Boolean,
    val description: String?,
    @Json(name = "default_branch") val defaultBranch: String?,
    val owner: GithubUserNested
)

data class GithubContent(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long,
    val type: String, // "file" or "dir"
    @Json(name = "download_url") val downloadUrl: String?,
    val content: String?, // base64 encoded block (returned when getting a single file)
    val encoding: String?
)

data class GithubFileUpdateRequest(
    val message: String,
    val content: String, // Base64 encoded content
    val sha: String? = null,
    val branch: String? = null
)

data class GithubFileDeleteRequest(
    val message: String,
    val sha: String,
    val branch: String? = null
)

// Device Flow Auth Models
data class GithubDeviceCodeRequest(
    @Json(name = "client_id") val clientId: String,
    val scope: String = "repo,user"
)

data class GithubDeviceCodeResponse(
    @Json(name = "device_code") val deviceCode: String,
    @Json(name = "user_code") val userCode: String,
    @Json(name = "verification_uri") val verificationUri: String,
    @Json(name = "expires_in") val expiresIn: Int,
    val interval: Int
)

data class GithubPollTokenRequest(
    @Json(name = "client_id") val clientId: String,
    @Json(name = "device_code") val deviceCode: String,
    @Json(name = "grant_type") val grantType: String = "urn:ietf:params:oauth:grant-type:device_code"
)

data class GithubTokenResponse(
    @Json(name = "access_token") val accessToken: String?,
    @Json(name = "token_type") val tokenType: String?,
    val scope: String?,
    val error: String?,
    @Json(name = "error_description") val errorDescription: String?
)

// --- Retrofit Service Interfaces ---

interface GithubService {

    @GET("user")
    suspend fun getCurrentUser(
        @Header("Authorization") tokenHeader: String
    ): Response<GithubUser>

    @GET("user/repos")
    suspend fun getRepositories(
        @Header("Authorization") tokenHeader: String,
        @Query("per_page") perPage: Int = 100,
        @Query("sort") sort: String = "updated"
    ): Response<List<GithubRepo>>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getRepositoryContents(
        @Header("Authorization") tokenHeader: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("ref") ref: String? = null
    ): Response<List<GithubContent>>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getSingleFileContent(
        @Header("Authorization") tokenHeader: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("ref") ref: String? = null
    ): Response<GithubContent>

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun createOrUpdateFile(
        @Header("Authorization") tokenHeader: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body body: GithubFileUpdateRequest
    ): Response<Unit>

    @HTTP(method = "DELETE", path = "repos/{owner}/{repo}/contents/{path}", hasBody = true)
    suspend fun deleteFile(
        @Header("Authorization") tokenHeader: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body body: GithubFileDeleteRequest
    ): Response<Unit>
}

interface GithubAuthService {

    @Headers("Accept: application/json")
    @POST("login/device/code")
    suspend fun requestDeviceCode(
        @Body request: GithubDeviceCodeRequest
    ): Response<GithubDeviceCodeResponse>

    @Headers("Accept: application/json")
    @POST("login/oauth/access_token")
    suspend fun pollAccessToken(
        @Body request: GithubPollTokenRequest
    ): Response<GithubTokenResponse>
}

object RetrofitClient {
    private const val BASE_URL = "https://api.github.com/"
    private const val AUTH_URL = "https://github.com/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val githubService: GithubService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(GithubService::class.java)
    }

    val githubAuthService: GithubAuthService by lazy {
        Retrofit.Builder()
            .baseUrl(AUTH_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(GithubAuthService::class.java)
    }
}
