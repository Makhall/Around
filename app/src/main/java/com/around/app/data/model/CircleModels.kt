package com.around.app.data.model

enum class AvatarType(val wireValue: String) {
    EMOJI("emoji"),
    COLOR("color"),
    ;

    companion object {
        fun fromWire(value: String?): AvatarType? =
            entries.firstOrNull { it.wireValue == value }
    }
}

data class Avatar(
    val type: AvatarType,
    val value: String,
) {
    companion object {
        val Default = Avatar(AvatarType.COLOR, "#006A6A")
    }
}

data class UserSettings(
    val shareBattery: Boolean = true,
)

data class UserProfile(
    val uid: String,
    val displayName: String,
    val avatar: Avatar = Avatar.Default,
    val locale: String = "en",
    val createdAt: Long = 0L,
    val settings: UserSettings = UserSettings(),
)

enum class MemberRole(val wireValue: String) {
    OWNER("owner"),
    MEMBER("member"),
    ;

    companion object {
        fun fromWire(value: String?): MemberRole =
            entries.firstOrNull { it.wireValue == value } ?: MEMBER
    }
}

data class Circle(
    val id: String,
    val name: String,
    val ownerUid: String,
    val memberCount: Int,
    val memberUids: List<String>,
    val createdAt: Long,
) {
    fun isOwnedBy(uid: String): Boolean = ownerUid == uid
}

data class CircleMember(
    val uid: String,
    val displayName: String,
    val avatar: Avatar,
    val role: MemberRole,
    val joinedAt: Long,
    val sharing: Boolean,
    val allowRing: Boolean,
    val historyEnabled: Boolean = true,
)

data class CreatedCircle(
    val circleId: String,
    val circleName: String,
    val inviteCode: String,
    val expiresAt: Long,
)

data class JoinedCircle(
    val circleId: String,
    val circleName: String,
)

data class InviteCode(
    val code: String,
    val expiresAt: Long,
)

enum class CircleError {
    INVALID_NAME,
    NAME_REQUIRED,
    CODE_REQUIRED,
    INVALID_REQUEST,
    TOO_MANY_CIRCLES,
    PROFILE_MISSING,
    CODE_NOT_FOUND,
    CODE_EXPIRED,
    CODE_REVOKED,
    CODE_EXHAUSTED,
    CIRCLE_FULL,
    ALREADY_MEMBER,
    RATE_LIMITED,
    OWNER_CANNOT_LEAVE,
    NOT_OWNER,
    MEMBER_NOT_FOUND,
    CIRCLE_NOT_FOUND,
    UNAUTHENTICATED,
    NETWORK,
    UNKNOWN,
}

class CircleOperationException(
    val error: CircleError,
) : Exception(error.name)

fun Throwable.toCircleError(): CircleError =
    (this as? CircleOperationException)?.error ?: CircleError.UNKNOWN

fun circleErrorFromApiCode(code: String?): CircleError = when (code) {
    "invalid_name" -> CircleError.INVALID_NAME
    "invalid_request" -> CircleError.INVALID_REQUEST
    "profile_missing" -> CircleError.PROFILE_MISSING
    "too_many_circles" -> CircleError.TOO_MANY_CIRCLES
    "code_not_found" -> CircleError.CODE_NOT_FOUND
    "code_expired" -> CircleError.CODE_EXPIRED
    "code_revoked" -> CircleError.CODE_REVOKED
    "code_exhausted" -> CircleError.CODE_EXHAUSTED
    "circle_full" -> CircleError.CIRCLE_FULL
    "already_member" -> CircleError.ALREADY_MEMBER
    "rate_limited" -> CircleError.RATE_LIMITED
    "owner_cannot_leave" -> CircleError.OWNER_CANNOT_LEAVE
    "not_owner" -> CircleError.NOT_OWNER
    "member_not_found" -> CircleError.MEMBER_NOT_FOUND
    "circle_not_found" -> CircleError.CIRCLE_NOT_FOUND
    "unauthenticated" -> CircleError.UNAUTHENTICATED
    else -> CircleError.UNKNOWN
}
