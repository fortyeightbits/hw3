package edu.wisc.cs.sdn.vnet;
import java.util.Timer;
import java.util.TimerTask;

public class TimedIface
{
	// Members
	TimedIfaceCallback callback;
	Timer ttlTimer;
	TimerTask timerTask;
	public Iface savedInterface;
	TimedIface self;
	static final int TIMEOUT_DELAY = 1000;
	
	// Constructor
	public TimedIface(Iface incomingInterface, TimedIfaceCallback ifaceCallback)
	{
		savedInterface = incomingInterface;
		self = this;
		callback = ifaceCallback;
		ttlTimer = new Timer();
		timerTask = new TimerTask()
					{
						@Override
						public void run() {
							callback.handleTimer(self);			
						}
					};
		//TODO: Remove debug
		System.out.println("TtlTimer added for task " + savedInterface.getName());
		ttlTimer.schedule(timerTask, TIMEOUT_DELAY);
	}
	
	// Methods
	public void resetTtlTimer()
	{
		//TODO: Remove debug
		System.out.println("TtlTimer reset for task " + savedInterface.getName());
		timerTask.cancel();
		// Create a new timer task and then schedule it
		timerTask = new TimerTask() {
			
			@Override
			public void run() {
				callback.handleTimer(self);						
			}
		};
		ttlTimer.schedule(timerTask, TIMEOUT_DELAY);
	}
	
}