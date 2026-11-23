package com.around.app.data.model

internal fun Any?.toAvatar(): Avatar {
    val map = this as? Map<*, *> ?: return Avatar.Default
    val type = AvatarType.fromWire(map["type"] as? String) ?: return Avatar.Default
    val value = map["value"] as? String ?: return Avatar.Default
    return Avatar(type, value)
}

internal fun Avatar.toFirestoreMap(): Map<String, String> = mapOf(
    "type" to type.wireValue,
    "value" to value,
)
