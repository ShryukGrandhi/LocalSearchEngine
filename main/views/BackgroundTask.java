package views;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.swing.SwingWorker;

/**
 * Runs work off the Swing event thread and reports back on it. Every disk read,
 * write and search in the app goes through here so the GUI never freezes.
 * A cancelled task never calls either callback.
 */
public final class BackgroundTask<T> extends SwingWorker<T, Void> {

    /** Work that may poll {@code isCancelled} to stop early. */
    @FunctionalInterface
    public interface Work<T> {
        T run(BooleanSupplier isCancelled) throws Exception;
    }

    private final Work<T> work;
    private final Consumer<? super T> onSuccess;
    private final Consumer<? super Throwable> onFailure;

    private BackgroundTask(Work<T> work, Consumer<? super T> onSuccess, Consumer<? super Throwable> onFailure) {
        this.work = work;
        this.onSuccess = onSuccess;
        this.onFailure = onFailure;
    }

    public static <T> BackgroundTask<T> start(
            Work<T> work, Consumer<? super T> onSuccess, Consumer<? super Throwable> onFailure) {
        BackgroundTask<T> task = new BackgroundTask<>(work, onSuccess, onFailure);
        task.execute();
        return task;
    }

    @Override
    protected T doInBackground() throws Exception {
        return work.run(this::isCancelled);
    }

    @Override
    protected void done() {
        if (isCancelled()) {
            return;
        }
        try {
            onSuccess.accept(get());
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof CancellationException)) {
                onFailure.accept(e.getCause());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (CancellationException e) {
            // cancelled between the isCancelled() check and get(): nothing to report
        }
    }
}
