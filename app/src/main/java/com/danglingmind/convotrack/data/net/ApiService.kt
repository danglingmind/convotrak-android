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

    /** Leader-only. Cascades to participants/waypoints server-side and broadcasts ride:deleted. */
    @DELETE("/rides/{rideId}")
    suspend fun deleteRide(@Path("rideId") rideId: String)

    @GET("/rides/join/{code}")
    suspend fun getRideByInviteCode(@Path("code") code: String): InviteCodeResponse

    @POST("/rides/{rideId}/join")
    suspend fun joinRide(@Path("rideId") rideId: String)

    @DELETE("/rides/{rideId}/participants/{userId}")
    suspend fun removeParticipant(@Path("rideId") rideId: String, @Path("userId") userId: String)

    /**
     * A rider removes themselves. Server-side this is the same operation as the leader's
     * [removeParticipant] — same guards, same `ride:participant_left` broadcast — so the other
     * riders' rosters, ready tally, leaderboard and map pins update through paths that already
     * exist. Rejects the leader (`LEADER_CANNOT_LEAVE`): they delete or end the ride instead.
     */
    @POST("/rides/{rideId}/leave")
    suspend fun leaveRide(@Path("rideId") rideId: String)

    @POST("/rides/{rideId}/start")
    suspend fun startRide(@Path("rideId") rideId: String)

    @POST("/rides/{rideId}/end")
    suspend fun endRide(@Path("rideId") rideId: String)

    @GET("/rides/{rideId}/summary")
    suspend fun getRideSummary(@Path("rideId") rideId: String): RideSummary

    /**
     * @param status comma-separated ride statuses to include; null means every status.
     *
     * Filtering server-side matters because this endpoint caps at 50 rows — unfiltered, a rider
     * with many finished rides could push their in-progress ones out of the response entirely.
     * See [RideStatusFilter].
     */
    @GET("/rides/me")
    suspend fun getMyRides(@Query("status") status: String? = null): MyRidesResponse

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

    /**
     * Erases the signed-in rider's account and everything attached to it.
     *
     * Play's account deletion policy does not accept deactivation, so the server contract here is
     * a real cascade: the user row, their participations, the rides they lead, their waypoints and
     * their history all go, and the Clerk user is revoked with them. Freezing or flagging the
     * account instead would satisfy the UI and fail the policy.
     *
     * The token used to authorise this call is dead by the time the response lands, which is why
     * the caller signs out locally off the back of it rather than making a second request.
     */
    @DELETE("/users/me")
    suspend fun deleteAccount()

    // Membership
    @POST("/memberships/activate")
    suspend fun activateMembership(@Body request: ActivateMembershipRequest): ActivateMembershipResponse

    @POST("/events")
    suspend fun sendEvents(@Body request: PaywallEventsRequest): EventsAccepted
}
