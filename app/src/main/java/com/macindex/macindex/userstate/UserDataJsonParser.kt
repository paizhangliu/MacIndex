package com.macindex.macindex.userstate

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Maps the external schema-1 JSON envelope onto validated user-library models. */
internal object UserDataJsonParser {
    private val ROOT_FIELDS = setOf("schema", "comments", "folders", "compare")
    private val COMMENT_FIELDS = setOf("machine", "text")
    private val FOLDER_FIELDS = setOf("name", "machines")
    private val COMPARE_FIELDS = setOf("machines", "left", "right")

    fun parse(raw: String, supportedSchema: Int): UserLibrary {
        try {
            val document = JSONObject(raw)
            requireFields(document, ROOT_FIELDS, "root")
            requireSchema(document.opt("schema"), supportedSchema)

            val comments = requiredArray(document, "comments").mapObjects("comment") { _, item ->
                requireFields(item, COMMENT_FIELDS, "comment")
                UserComment(
                    machineUid = requiredString(item, "machine"),
                    text = requiredString(item, "text"),
                )
            }
            val folders = requiredArray(document, "folders").mapObjects("folder") { index, item ->
                requireFields(item, FOLDER_FIELDS, "folder")
                FavouriteFolder(
                    id = index + 1L,
                    name = requiredString(item, "name"),
                    machineUids = requiredStringArray(item, "machines"),
                )
            }

            val compareObject = requiredObject(document, "compare")
            requireFields(compareObject, COMPARE_FIELDS, "compare")
            val library = UserLibrary(
                comments = comments,
                favouriteFolders = folders,
                compare = CompareSelection(
                    machineUids = requiredStringArray(compareObject, "machines"),
                    leftUid = requiredString(compareObject, "left"),
                    rightUid = requiredString(compareObject, "right"),
                ),
                nextFavouriteFolderId = folders.size + 1L,
            )
            UserStateValidator.validateLibrary(library)
            return library
        } catch (failure: InvalidUserDataException) {
            throw failure
        } catch (failure: JSONException) {
            throw InvalidUserDataException("Invalid user data JSON", failure)
        }
    }

    private fun requireSchema(value: Any?, supportedSchema: Int) {
        val schema = when (value) {
            is Int -> value.toLong()
            is Long -> value
            else -> throw InvalidUserDataException("Invalid user data schema")
        }
        if (schema != supportedSchema.toLong()) {
            throw InvalidUserDataException("Unsupported user data schema")
        }
    }

    private fun requireFields(value: JSONObject, expected: Set<String>, label: String) {
        val actual = mutableSetOf<String>()
        val fields = value.keys()
        while (fields.hasNext()) {
            actual += fields.next()
        }
        val unexpected = actual - expected
        if (unexpected.isNotEmpty()) {
            throw InvalidUserDataException(
                "Unexpected JSON field in $label: ${unexpected.first()}",
            )
        }
        val missing = expected - actual
        if (missing.isNotEmpty()) {
            throw InvalidUserDataException("Missing JSON field in $label: ${missing.first()}")
        }
    }

    private fun requiredObject(value: JSONObject, field: String): JSONObject =
        value.opt(field) as? JSONObject
            ?: throw InvalidUserDataException("JSON field $field must be an object")

    private fun requiredArray(value: JSONObject, field: String): JSONArray =
        value.opt(field) as? JSONArray
            ?: throw InvalidUserDataException("JSON field $field must be an array")

    private fun requiredString(value: JSONObject, field: String): String =
        (value.opt(field) as? String)
            ?.let(UserStateValidator::requireWellFormedUtf16)
            ?: throw InvalidUserDataException("JSON field $field must be a string")

    private fun requiredStringArray(value: JSONObject, field: String): List<String> {
        val array = requiredArray(value, field)
        return List(array.length()) { index ->
            (array.opt(index) as? String)
                ?.let(UserStateValidator::requireWellFormedUtf16)
                ?: throw InvalidUserDataException("JSON field $field must contain strings")
        }
    }

    private inline fun <T> JSONArray.mapObjects(
        label: String,
        transform: (Int, JSONObject) -> T,
    ): List<T> = List(length()) { index ->
        val item = opt(index) as? JSONObject
            ?: throw InvalidUserDataException("JSON $label must be an object")
        transform(index, item)
    }
}
