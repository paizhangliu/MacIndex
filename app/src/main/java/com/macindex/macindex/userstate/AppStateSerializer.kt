package com.macindex.macindex.userstate

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.macindex.macindex.userstate.proto.AppState
import java.io.InputStream
import java.io.OutputStream

object AppStateSerializer : Serializer<AppState> {
    override val defaultValue: AppState = AppStateProtoMapper.defaultProto()

    override suspend fun readFrom(input: InputStream): AppState = try {
        AppState.parseFrom(input).also(AppStateProtoMapper::requireValidPersistedProto)
    } catch (exception: InvalidProtocolBufferException) {
        throw CorruptionException("Unable to read app state", exception)
    } catch (exception: InvalidUserDataException) {
        throw CorruptionException("App state violates its protocol", exception)
    }

    override suspend fun writeTo(t: AppState, output: OutputStream) {
        t.writeTo(output)
    }
}
