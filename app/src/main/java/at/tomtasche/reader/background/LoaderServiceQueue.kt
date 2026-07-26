package at.tomtasche.reader.background

import java.util.LinkedList

/**
 * Holds on to work that wants a [LoaderService] before the service connection is up, and replays it
 * once the service arrives.
 */
class LoaderServiceQueue {

    private val queue: MutableList<QueueEntry> = LinkedList()

    private var boundService: LoaderService? = null

    /**
     * The bound service, or null while the connection is still pending. Setting it replays
     * everything queued up so far; the queue is deliberately kept, matching the previous behaviour.
     */
    var service: LoaderService?
        @Synchronized get() = boundService
        @Synchronized
        set(value) {
            boundService = value

            if (value == null) {
                return
            }

            for (entry in queue) {
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

    interface QueueEntry {
        fun onService(service: LoaderService)
    }
}
