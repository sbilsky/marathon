package com.malinskiy.marathon.ios

import com.malinskiy.marathon.test.MetaProperty
import com.malinskiy.marathon.test.Test
import com.malinskiy.marathon.test.TestBatch

internal fun Test(pkg: String, clazz: String, method: String, targetName: String): Test =
    Test(pkg = pkg, clazz = clazz, method = method, metaProperties = listOf(testTargetMetaProperty(targetName)))
internal val Test.targetName: String
    get() = metaProperties.firstOrNull { it.name == IOS_TEST_TARGET_METAPROPERTY }
        ?.values?.get(IOS_TEST_TARGET_NAME_METAPROPERTY_KEY) as? String
        ?: throw IllegalStateException("Test target of ${this.pkg}.${this.clazz}.${this.method} is not configured")

internal fun Test.toXcodebuildArgument(): String = "-only-testing:\"${targetName}/${clazz}/${method}\""
internal fun TestBatch.toXcodebuildArguments(): String = tests.map { it.toXcodebuildArgument() }.joinToString(separator = " ")

private fun testTargetMetaProperty(targetName: String) = MetaProperty(IOS_TEST_TARGET_METAPROPERTY,
                                                                      mapOf(IOS_TEST_TARGET_NAME_METAPROPERTY_KEY to targetName))
private const val IOS_TEST_TARGET_METAPROPERTY = "IOS_TEST_TARGET_METAPROPERTY"
private const val IOS_TEST_TARGET_NAME_METAPROPERTY_KEY = "IOS_TEST_TARGET_NAME_METAPROPERTY_KEY"
