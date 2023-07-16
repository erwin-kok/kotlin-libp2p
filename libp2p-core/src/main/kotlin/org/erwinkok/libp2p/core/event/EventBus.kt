// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.event

import io.ktor.utils.io.core.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

open class EventType

class EventBus : Closeable {
    val publisherMap = ConcurrentHashMap<KClass<out EventType>, Publisher<out EventType>>()
    val subscriberJobs = ConcurrentHashMap<Any, Set<Job>>()

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : EventType> tryPublish(event: T, replay: Int = 0): Boolean {
        val publisher = publisherMap.computeIfAbsent(T::class) { Publisher<T>(replay) } as Publisher<T>
        return publisher.tryPublish(event)
    }

    @Suppress("UNCHECKED_CAST")
    suspend inline fun <reified T : EventType> publish(event: T, replay: Int = 0) {
        val publisher = publisherMap.computeIfAbsent(T::class) { Publisher<T>(replay) } as Publisher<T>
        publisher.publish(event)
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : EventType> publisher(replay: Int = 0): Publisher<T> {
        return publisherMap.computeIfAbsent(T::class) { Publisher<T>(replay) } as Publisher<T>
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : EventType> subscribe(
        subscriber: Any,
        scope: CoroutineScope,
        dispatcher: CoroutineContext = Dispatchers.Default,
        noinline action: suspend (T) -> Unit,
    ) {
        val publisher = publisherMap.computeIfAbsent(T::class) { Publisher<T>(0) } as Publisher<T>
        val newJob =
            publisher
                .asSharedFlow()
                .onEach(action)
                .flowOn(dispatcher)
                .launchIn(scope)
        subscriberJobs[subscriber] = getJobs(subscriber) + newJob
    }

    fun subscribeWildcard(
        subscriber: Any,
        scope: CoroutineScope,
        dispatcher: CoroutineContext = Dispatchers.Default,
        action: suspend (EventType) -> Unit,
    ) {
        for (publisher in publisherMap.values) {
            val newJob =
                publisher
                    .asSharedFlow()
                    .onEach { action(it) }
                    .flowOn(dispatcher)
                    .launchIn(scope)
            subscriberJobs[subscriber] = getJobs(subscriber) + newJob
        }
    }

    fun unsubscribe(subscriber: Any) {
        getJobs(subscriber).forEach { job -> job.cancel() }
        subscriberJobs.remove(subscriber)
    }

    fun getAllEventTypes(): List<KClass<*>> {
        return publisherMap.keys.toList()
    }

    fun getJobs(subscriber: Any): Set<Job> {
        return subscriberJobs[subscriber] ?: emptySet()
    }

    override fun close() {
        subscriberJobs.flatMap { it.value }.forEach { it.cancel() }
        subscriberJobs.clear()
        publisherMap.clear()
    }
}
