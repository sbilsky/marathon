package com.malinskiy.marathon.cli.config.deserialize

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.treeToValue
import com.malinskiy.marathon.cli.args.FileAndroidConfiguration
import com.malinskiy.marathon.cli.args.FileIOSConfiguration
import com.malinskiy.marathon.cli.args.FileVendorConfiguration
import com.malinskiy.marathon.cli.config.ConfigurationException

const val TYPE_IOS = "iOS"
const val TYPE_ANDROID = "Android"

class FileVendorConfigurationDeserializer : StdDeserializer<FileVendorConfiguration>(FileVendorConfiguration::class.java) {
    override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): FileVendorConfiguration {
        val codec = p?.codec as ObjectMapper
        val node: JsonNode = codec.readTree(p) ?: throw ConfigurationException("Missing filter strategy")
        val type = node.get("type").asText()

        return when (type) {
            TYPE_IOS -> {
                (node as ObjectNode).remove("type")
                codec.treeToValue<FileIOSConfiguration>(node)
            }
            TYPE_ANDROID -> {
                (node as ObjectNode).remove("type")
                codec.treeToValue<FileAndroidConfiguration>(node)
            }
            else -> throw ConfigurationException("Unrecognized sorting strategy $type. " +
                    "Valid options are $TYPE_ANDROID, $TYPE_IOS")
        }
    }
}
