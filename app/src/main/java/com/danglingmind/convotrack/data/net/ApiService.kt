package com.danglingmind.convotrack.data.net

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// Retrofit mirror of the iOS APIClient endpoints. All calls are authenticated —
// the Bearer token is attached by AuthInterceptor, so nothing here handles auth.
interface ApiService {

    // Ride
    @POST("/rides")
    suspend fun createRide(@Body request: CreateRideRequest): CreateRideResponse

    @PATCH("/rides/{rideId}")
    suspend fun updateRide(@Path("rideId") rideId: String, @Body request: UpdateRideRequest)

    @GET("/rides/{rideId}")
    suspend fun getRide(@Path("rideId") rideId: String): Ride

    @GET("/rides/join/{code}")
    suspend fun getRideByInviteCode(@Path("code") code: String): InviteCodeResponse

    @POST("/rides/{rideId}/join")
    suspend fun joinRide(@Path("rideId") rideId: String)

    @DELETE("/rides/{rideId}/participants/{userId}")
    suspend fun removeParticipant(@Path("rideId") rideId: String, @Path("userId") userId: String)

    @POST("/rides/{rideId}/start")
    suspend fun startRide(@Path("rideId") rideId: String)

    @POST("/rides/{rideId}/end")
    suspend fun endRide(@Path("rideId") rideId: String)

    @GET("/rides/{rideId}/summary")
    suspend fun getRideSummary(@Path("rideId") rideId: String): RideSummary

    @GET("/rides/me")
    suspend fun getMyRides(): MyRidesResponse

    @GET("/rides/nearby")
    suspend fun getNearbyRides(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radiusKm") radiusKm: Int = 20,
    ): NearbyRidesResponse

    // Users
    @GET("/users/me")
    suspend fun fetchMe(): UserMeResponse

    @PATCH("/users/me")
    suspend fun syncProfile(@Body request: SyncProfileRequest)

    @PATCH("/users/me")
    suspend fun updateProfile(@Body request: UpdateProfileRequest)

    // Membership
    @POST("/memberships/activate")
    suspend fun activateMembership(@Body request: ActivateMembershipRequest): ActivateMembershipResponse
}
