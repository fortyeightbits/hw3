package edu.wisc.cs.sdn.vnet.rt;

import java.util.TimerTask;

public class RouteEntryTimerTask extends TimerTask
{
	protected RouteTable table;
	protected int destinationAddress;
	protected int maskAddress;
	
	public RouteEntryTimerTask(RouteTable tableArg, int destinationAddressArg, int maskAddressArg)
	{
		table = tableArg;
		destinationAddress = destinationAddressArg;
		maskAddress = maskAddressArg;
	}

	@Override
	public void run() {
		// remove
		System.out.println("Route Entry Timed Out: " + destinationAddress);
		if(table.remove(destinationAddress, maskAddress) == true)
		{
			System.out.println("Successfully removed route entry from table");
		}
	}
	

}
