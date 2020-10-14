package de.datlag.openfe.commons

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@ExperimentalContracts
fun CharSequence?.isNotCleared(): Boolean {
    contract {
        returns(true) implies (this@isNotCleared != null)
    }
    val data = this?.trim()

    return !data.isNullOrEmpty() && !data.isNullOrBlank()
}
