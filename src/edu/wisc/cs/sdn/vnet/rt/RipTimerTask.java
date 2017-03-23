package edu.wisc.cs.sdn.vnet.rt;


import java.util.TimerTask;

public abstract class RipTimerTask extends TimerTask 
{
	protected Router localRouter;
	
	public RipTimerTask(Router routerArg)
	{
		localRouter = routerArg;
	}	
}
