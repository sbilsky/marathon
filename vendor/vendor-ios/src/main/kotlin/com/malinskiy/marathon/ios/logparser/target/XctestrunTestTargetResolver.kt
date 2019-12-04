package com.malinskiy.marathon.ios.logparser.target

import com.malinskiy.marathon.ios.xctestrun.Xctestrun

/**
 * When applied, replaces module name reported in xcodebuild log with testing target name in order
 * to unify test representation.
 *
 * @param targetNameMappings a map with target names as keys and product module names as corresponding values.
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
