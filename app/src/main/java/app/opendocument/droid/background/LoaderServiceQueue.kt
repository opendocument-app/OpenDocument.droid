package app.opendocument.droid.background

import java.util.LinkedList

/**
 * Holds on to work that wants a [LoaderService] before the service connection is up, and replays it
 * once the service arrives.
 */
class LoaderServiceQueue {

    private val queue: MutableList<QueueEntry> = LinkedList()

    private var boundService: LoaderService? = null

    /**
     * The bound service, or null while the connection is pending or after it dropped. Setting it
     * replays what queued up in the meantime.
     */
    var service: LoaderService?
        @Synchronized get() = boundService
        @Synchronized
        set(value) {
            boundService = value

            if (value == null) {
                return
            }

            // drained, not kept: a reconnect would otherwise replay every load this activity has
            // ever queued, reopening documents the user has moved on from
            val pending = queue.toList()
            queue.clear()

            for (entry in pending) {
                entry.onService(value)
            }
        }

    @Synchronized
    fun addToQueue(entry: QueueEntry) {
        val service = boundService
        if (service != null) {
            entry.onService(service)
            return
        }

        queue.add(entry)
    }

    fun interface QueueEntry {
        fun onService(service: LoaderService)
    }
}
