package com.secops.agent;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;

/** Bounded workers, zero backlog. Timed-out tasks cannot persist any state. */
@Component
public class DeadlineRunner {
    private final ExecutorService pool=new ThreadPoolExecutor(0,8,30,TimeUnit.SECONDS,new SynchronousQueue<>(),
        r -> { Thread t=new Thread(r,"triage-io"); t.setDaemon(true); return t; },new ThreadPoolExecutor.AbortPolicy());
    public <T> T call(Callable<T> action,long timeoutMs) throws Exception {
        if(timeoutMs<=0) throw new TimeoutException();
        Future<T> future=pool.submit(action);
        try { return future.get(timeoutMs,TimeUnit.MILLISECONDS); }
        catch(ExecutionException e) { if(e.getCause() instanceof Exception x) throw x; throw e; }
        finally { if(!future.isDone()) future.cancel(true); }
    }
    @PreDestroy public void close() { pool.shutdownNow(); }
}
