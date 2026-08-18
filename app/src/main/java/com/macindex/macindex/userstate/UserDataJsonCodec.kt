package com.macindex.macindex.userstate

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/** The stable external schema-1 UID protocol. Internal Proto messages never cross this boundary. */
object UserDataJsonCodec {
    private const val SCHEMA_VERSION = 1

    fun export(state: UserState): String = export(state.library)

    fun export(library: UserLibrary): String {
        UserStateValidator.validateLibrary(library)
        return JSONObject()
            .put("schema", SCHEMA_VERSION)
            .put("comments", JSONArray().also { array ->
                library.comments.forEach { comment ->
                    array.put(JSONObject()
                        .put("machine", comment.machineUid)
                        .put("text", comment.text))
                }
            })
            .put("folders", JSONArray().also { array ->
                library.favouriteFolders.forEach { folder ->
                    array.put(JSONObject()
                        .put("name", folder.name)
                        .put("machines", JSONArray(folder.machineUids)))
                }
            })
            .put("compare", JSONObject()
                .put("machines", JSONArray(library.compare.machineUids))
                .put("left", library.compare.leftUid)
                .put("right", library.compare.rightUid))
            .toString(2)
    }

    fun prepareImport(raw: String, resolver: MachineNameResolver): PreparedUserDataImport {
        if (raw.toByteArray(StandardCharsets.UTF_8).size > UserStateLimits.MAX_IMPORT_BYTES) {
            throw InvalidUserDataException("User data file is too large")
        }
        val parsed = parse(raw)
        val reconciled = UserStateReconciler.reconcile(UserState(library = parsed), resolver)
        return PreparedUserDataImport(
            library = reconciled.state.library,
            removedCount = reconciled.removedContent.size,
        )
    }

    internal fun parse(raw: String): UserLibrary =
        UserDataJsonParser.parse(raw, SCHEMA_VERSION)
}
