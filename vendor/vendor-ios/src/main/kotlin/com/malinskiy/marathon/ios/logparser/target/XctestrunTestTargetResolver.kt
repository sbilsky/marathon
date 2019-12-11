package com.malinskiy.marathon.ios.logparser.target

import com.malinskiy.marathon.ios.xctestrun.Xctestrun

/**
 * When applied, informs an xcode target name that corresponds to a module name found in xcodebuild log. Unlike its Android counterpart,
 * When an iOS test is executed, its module name is used to match execution results in the logs (modules are functionally similar to
 * packages). However, name of the build target a test belongs to is required in order to start its execution. Module and
 * target names of a test are the same in most projects, following default naming scheme provided by Xcode. However, that is not
 * expected and are sometimes configured differently. This class helps to determine target name when necessary, using data from
 * the provided xctestrun file.
 *
 * @param xctestrun a parsed xctestrun file
 */
class XctestrunTestTargetResolver(xctestrun: Xctestrun): TestTargetResolver {

    private val poductModuleNameMap = xctestrun.targetNames.mapNotNull { target ->
        xctestrun.productModuleName(target)?.let {
            it to target
        }
    }.toMap()

    override fun targetNameOf(productModule: String?): String? {
        if (productModule == null) { return null }

        val result = poductModuleNameMap.entries
                .firstOrNull { productModule.contains(it.key) }
                ?.let { productModule.replace(it.key, it.value) }
        return result
    }
}
