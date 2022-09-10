package at.tomtasche.reader.background;

import java.util.LinkedList;
import java.util.List;

public class LoaderServiceQueue {

    private List<QueueEntry> queue;

    private LoaderService service;

    public LoaderServiceQueue() {
        queue = new LinkedList<>();
    }

    public synchronized void addToQueue(QueueEntry entry) {
        if (service != null) {
            entry.onService(service);
            return;
        }

        queue.add(entry);
    }

    public synchronized LoaderService getService() {
        return service;
    }

    public synchronized void setService(LoaderService service) {
        this.service = service;

        for (QueueEntry entry : queue) {
            entry.onService(service);
        }
    }

    public interface QueueEntry {
        public void onService(LoaderService service);
    }
}
