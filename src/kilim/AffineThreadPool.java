package kilim;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SequenceBarrier;

public class AffineThreadPool
{
	private static final int MAX_QUEUE_SIZE = 4096;
	private static final String colon_ = ":";

	protected static int getCurrentThreadId()
	{
		String name = Thread.currentThread().getName();
		int sIndex = name.indexOf(colon_);		
		return Integer.parseInt(name.substring(sIndex + 1, name.length()));
	}
		
	private int nThreads_;	
	private int queueSize_;
	private AtomicInteger currentIndex_ = new AtomicInteger(0);	
	private RingBuffer<KilimEvent> rBuffer_;
	private ExecutorService executorService_;
	
	public AffineThreadPool(int nThreads, String name, ExceptionHandler exHandler)
	{
		this(nThreads, MAX_QUEUE_SIZE, name, exHandler);
	}
	
	public AffineThreadPool(int nThreads, int queueSize, String name, ExceptionHandler exHandler)
	{	
		nThreads_ = nThreads;		
		queueSize_ = queueSize;
		rBuffer_ = RingBuffer.createMultiProducer(KilimEvent.factory_, queueSize);
		SequenceBarrier nBarrier = rBuffer_.newBarrier();
		executorService_ = Executors.newFixedThreadPool(nThreads, new ThreadFactoryImpl(name));
		EventProcessor[] evtProcessors = new EventProcessor[nThreads];
		for (int i = 0; i < nThreads; ++i)
		{
			evtProcessors[i] = new BatchEventProcessor<KilimEvent>(rBuffer_, nBarrier, new KilimEventHandler(i));
			executorService_.execute(evtProcessors[i]);
		}						
	}
	
	public long getTaskCount()
	{
		return queueSize_ - rBuffer_.remainingCapacity();
	}
	
	public int publish(Task task)
	{
		currentIndex_.compareAndSet(Integer.MAX_VALUE, 0);
		int index = currentIndex_.incrementAndGet() % nThreads_;		
		return publish(index, task);
	}
	
	public int publish(int index, Task task)
	{		
		long sequence = rBuffer_.next();
		KilimEvent kEvent = rBuffer_.get(sequence);
		kEvent.putTid(index);
		kEvent.putTask(task);
		rBuffer_.publish(sequence);		
		return index;
	}
	
	public void shutdown()
	{
		executorService_.shutdown();
	}
	
	public static void main(String[] args) throws Throwable
	{
		AffineThreadPool aPool = new AffineThreadPool(8, 65536, "Avinash", null);
		for (int i = 0; i < 1000000; ++i)
		{
			aPool.publish(null);			
		}
	}
}
