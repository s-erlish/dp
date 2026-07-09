package com.v2ray.ang.auth

import com.v2ray.ang.auth.dto.AuthCodeRequest
import com.v2ray.ang.auth.dto.AuthPollRequest
import com.v2ray.ang.auth.dto.AuthPollResponse
import com.v2ray.ang.auth.dto.AuthStartRequest
import com.v2ray.ang.auth.dto.AuthStartResponse
import com.v2ray.ang.auth.dto.RefreshRequest
import com.v2ray.ang.auth.dto.SubscriptionInfoDto
import com.v2ray.ang.auth.dto.UserProfileDto

/**
 * Pluggable client for the Departament backend. All methods run on Dispatchers.IO in the
 * implementation and throw [ApiError] on failure.
 */
interface DepartamentApiClient {
    suspend fun startTelegramAuth(req: AuthStartRequest): AuthStartResponse
    suspend fun pollTelegramAuth(req: AuthPollRequest): AuthPollResponse
    suspend fun submitAuthCode(req: AuthCodeRequest): AuthPollResponse
    suspend fun getProfile(token: String): UserProfileDto
    suspend fun getSubscription(token: String): SubscriptionInfoDto
    suspend fun refresh(req: RefreshRequest): AuthPollResponse
    suspend fun logout(token: String)
}
