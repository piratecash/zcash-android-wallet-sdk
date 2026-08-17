package cash.z.ecc.android.sdk.fixture

import java.lang.reflect.Proxy

/**
 * Creates a proxy implementation of interface [T] whose every member throws unless the caller
 * overrides it. Useful for building small test fakes for large interfaces without needing to
 * implement every member.
 */
object UnusedProxyFixture {
    @Suppress("UNCHECKED_CAST")
    internal inline fun <reified T> new(): T =
        Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java)
        ) { _, method, _ ->
            error("Unexpected ${T::class.simpleName}.${method.name} call")
        } as T
}
