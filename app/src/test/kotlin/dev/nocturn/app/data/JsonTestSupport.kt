package dev.nocturn.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Parses a JSON fixture literally; tests never touch the network or Android. */
internal fun json(source: String): JsonObject = Json.parseToJsonElement(source) as JsonObject

internal fun jsonArray(source: String): JsonArray = Json.parseToJsonElement(source) as JsonArray
