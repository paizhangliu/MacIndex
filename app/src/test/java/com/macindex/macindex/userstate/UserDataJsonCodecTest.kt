package com.macindex.macindex.userstate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDataJsonCodecTest {
    private val library = UserLibrary(
        comments = listOf(UserComment("MI000001", "A note")),
        favouriteFolders = listOf(
            FavouriteFolder(42, "Saved", listOf("MI000001", "MI000002")),
        ),
        compare = CompareSelection(
            listOf("MI000001", "MI000002"),
            "MI000001",
            "MI000002",
        ),
        nextFavouriteFolderId = 43,
    )

    @Test
    fun schemaOneRoundTripUsesUidsAndDoesNotExportInternalFolderIds() {
        val json = UserDataJsonCodec.export(library)
        val parsed = UserDataJsonCodec.parse(json)

        assertTrue(json.contains("\"schema\": 1"))
        assertTrue(json.contains("MI000001"))
        assertFalse(json.contains("\"id\""))
        assertEquals(library.comments, parsed.comments)
        assertEquals(library.favouriteFolders.single().name, parsed.favouriteFolders.single().name)
        assertEquals(library.favouriteFolders.single().machineUids, parsed.favouriteFolders.single().machineUids)
        assertEquals(1L, parsed.favouriteFolders.single().id)
        assertEquals(library.compare, parsed.compare)
    }

    @Test
    fun prepareKeepsKnownUidsAndReportsMissingMachines() {
        val resolver = resolverOf("MI000001")
        val prepared = UserDataJsonCodec.prepareImport(UserDataJsonCodec.export(library), resolver)

        assertEquals(listOf("MI000001"), prepared.library.comments.map { it.machineUid })
        assertEquals(listOf("MI000001"), prepared.library.favouriteFolders.single().machineUids)
        assertEquals(listOf("MI000001"), prepared.library.compare.machineUids)
        assertTrue(prepared.library.compare.leftUid.isEmpty())
        assertEquals(3, prepared.removedCount)
    }

    @Test
    fun prepareMovesRetiredUidsToTheirCurrentReplacement() {
        val source = UserLibrary(
            comments = listOf(UserComment("MI000010", "moved")),
        )
        val prepared = UserDataJsonCodec.prepareImport(
            UserDataJsonCodec.export(source),
            MachineUidResolver { uid ->
                if (uid == "MI000010") {
                    MachineUidResolution("MI000020", "Old Machine")
                } else {
                    MachineUidResolution(null, uid)
                }
            },
        )

        assertEquals("MI000020", prepared.library.comments.single().machineUid)
        assertEquals(0, prepared.removedCount)
    }

    @Test
    fun structurallyInvalidImportIsRejectedAsAWhole() {
        val invalid = """
            {
              "schema": 1,
              "comments": [{"machine": "MI000001", "text": "note", "unexpected": true}],
              "folders": [],
              "compare": {"machines": [], "left": "", "right": ""}
            }
        """.trimIndent()

        assertThrows(InvalidUserDataException::class.java) {
            UserDataJsonCodec.prepareImport(invalid, resolverOf())
        }
    }

    @Test
    fun unsupportedSchemaIsRejected() {
        val json = UserDataJsonCodec.export(library).replaceFirst("\"schema\": 1", "\"schema\": 2")
        assertThrows(InvalidUserDataException::class.java) {
            UserDataJsonCodec.prepareImport(json, resolverOf())
        }
    }

    @Test
    fun schemaAndRecordTypesAreNotCoerced() {
        val exported = UserDataJsonCodec.export(library)
        for (invalid in listOf(
            exported.replaceFirst("\"schema\": 1", "\"schema\": \"1\""),
            exported.replaceFirst("\"schema\": 1", "\"schema\": 1.0"),
            """{"schema":1,"comments":{},"folders":[],"compare":{"machines":[],"left":"","right":""}}""",
            exported.replaceFirst("\"machine\": \"MI000001\"", "\"machine\": 1"),
        )) {
            assertThrows(InvalidUserDataException::class.java) {
                UserDataJsonCodec.parse(invalid)
            }
        }
    }

    @Test
    fun malformedJsonAndInvalidUidAreRejected() {
        assertThrows(InvalidUserDataException::class.java) {
            UserDataJsonCodec.parse("{")
        }
        assertThrows(InvalidUserDataException::class.java) {
            UserDataJsonCodec.parse(
                UserDataJsonCodec.export(library).replaceFirst("MI000001", "not-a-uid"),
            )
        }
    }

    @Test
    fun stringsRequireWellFormedUtf16WithoutRejectingAstralCharacters() {
        val exported = UserDataJsonCodec.export(library)
        val rawAstral = exported.replaceFirst("A note", "A 😀 note")
        val escapedAstral = exported.replaceFirst("A note", "A \\uD83D\\uDE00 note")

        assertEquals("A 😀 note", UserDataJsonCodec.parse(rawAstral).comments.single().text)
        assertEquals("A 😀 note", UserDataJsonCodec.parse(escapedAstral).comments.single().text)
        assertEquals("A 😀 note", UserStateValidator.normalizeComment("A 😀 note"))

        for (invalidText in listOf("\\uD800", "\\uDC00", "\uD800", "\uDC00")) {
            val invalid = exported.replaceFirst("A note", invalidText)
            assertThrows(InvalidUserDataException::class.java) {
                UserDataJsonCodec.parse(invalid)
            }
        }
        for (invalidText in listOf("\uD800", "\uDC00")) {
            assertThrows(InvalidUserDataException::class.java) {
                UserStateValidator.normalizeComment(invalidText)
            }
            assertThrows(InvalidUserDataException::class.java) {
                UserStateValidator.normalizeFolderName(invalidText)
            }
        }
    }

    @Test
    fun commentsUseOneCanonicalFiveHundredCodeUnitBoundary() {
        val exported = UserDataJsonCodec.export(library)
        val maximum = "A".repeat(500)
        val atLimit = exported.replaceFirst("A note", maximum)

        assertEquals(maximum, UserDataJsonCodec.parse(atLimit).comments.single().text)
        assertThrows(InvalidUserDataException::class.java) {
            UserDataJsonCodec.parse(exported.replaceFirst("A note", "A".repeat(501)))
        }
        assertThrows(InvalidUserDataException::class.java) {
            UserDataJsonCodec.parse(exported.replaceFirst("A note", " ".repeat(600) + "A"))
        }
        assertThrows(InvalidUserDataException::class.java) {
            UserStateValidator.validateLibrary(
                library.copy(comments = listOf(UserComment("MI000001", " A"))),
            )
        }
    }

    private fun resolverOf(vararg activeUids: String) = MachineUidResolver { uid ->
        if (uid in activeUids) {
            MachineUidResolution(uid, "Machine")
        } else {
            MachineUidResolution(null, uid)
        }
    }
}
