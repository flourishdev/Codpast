package com.codpast.player.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Type-Safe Flow Combination Extensions.
 * Replaces unchecked array casting with compile-time type safety.
 */

fun <T1, T2, T3, T4, T5, T6, R> combine(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    transform: suspend (T1, T2, T3, T4, T5, T6) -> R
): Flow<R> = combine(
    combine(flow1, flow2, flow3) { a, b, c -> Triple(a, b, c) },
    combine(flow4, flow5, flow6) { d, e, f -> Triple(d, e, f) }
) { (a, b, c), (d, e, f) ->
    transform(a, b, c, d, e, f)
}

fun <T1, T2, T3, T4, T5, T6, T7, R> combine(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    transform: suspend (T1, T2, T3, T4, T5, T6, T7) -> R
): Flow<R> = combine(
    combine(flow1, flow2, flow3, flow4) { a, b, c, d -> Tuple4(a, b, c, d) },
    combine(flow5, flow6, flow7) { e, f, g -> Triple(e, f, g) }
) { (a, b, c, d), (e, f, g) ->
    transform(a, b, c, d, e, f, g)
}

private data class Tuple4<T1, T2, T3, T4>(
    val val1: T1,
    val val2: T2,
    val val3: T3,
    val val4: T4
)