package com.macindex.macindex.userstate

import androidx.datastore.core.CorruptionException
import com.macindex.macindex.userstate.proto.UserLibrary
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateSerializerTest {
    @Test
    fun defaultsPreserveTrueProductDefaults() {
        val state = AppStateProtoMapper.toDomain(AppStateSerializer.defaultValue)
        assertTrue(state.preferences.playDeathSound)
        assertTrue(state.preferences.enableVolumeWarning)
        assertTrue(state.preferences.useNavigationButtons)
        assertTrue(state.preferences.highlightCompareDifferences)
        assertEquals(1L, state.library.nextFavouriteFolderId)
        assertEquals(0, state.registeredAppVersionCode)
    }

    @Test
    fun roundTrip() = runBlocking {
        val output = ByteArrayOutputStream()
        AppStateSerializer.writeTo(AppStateSerializer.defaultValue, output)
        val restored = AppStateSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))
        assertEquals(AppStateSerializer.defaultValue, restored)
    }

    @Test
    fun malformedProtoIsCorruptionNotAnEmptyState() {
        assertThrows(CorruptionException::class.java) {
            runBlocking {
                AppStateSerializer.readFrom(ByteArrayInputStream(byteArrayOf(0x80.toByte())))
            }
        }
    }

    @Test
    fun corruptionReplacementCarriesDurableResetNotice() {
        val state = AppStateProtoMapper.toDomain(AppStateProtoMapper.corruptionResetProto())
        assertTrue(state.pendingNotice!!.entireUserStateWasReset)
    }

    @Test
    fun parseableButSemanticallyInvalidProtoIsCorruption() {
        val invalid = AppStateSerializer.defaultValue.toBuilder()
            .setLibrary(UserLibrary.newBuilder().setNextFavouriteFolderId(0).build())
            .build()
        assertThrows(CorruptionException::class.java) {
            runBlocking {
                AppStateSerializer.readFrom(ByteArrayInputStream(invalid.toByteArray()))
            }
        }
    }
}
