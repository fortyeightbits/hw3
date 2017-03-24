package edu.wisc.cs.sdn.vnet.rt;

import net.floodlightcontroller.packet.IPv4;

import java.util.Timer;
import java.util.TimerTask;

import edu.wisc.cs.sdn.vnet.Iface;

/**
 * An entry in a route table.
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class RouteEntry 
{
	public static final int DEFAULT_TIMEOUT = 30000;
	
	/** Destination IP address */
	private int destinationAddress;
	
	/** Gateway IP address */
	private int gatewayAddress;
	
	/** Subnet mask */
	private int maskAddress;
	
	/** Hops */
	private int hops;
	
	/** RouteEntry removal timer */
	private Timer routeEntryRemovalTimer;
	
	private RouteEntryTimerTask routeTimerTask;
	
	private RouteTable localTable;
	
	/** Router interface out which packets should be sent to reach
	 * the destination or gateway */
	private Iface iface;
	
	/**
	 * Create a new route table entry.
	 * @param destinationAddress destination IP address
	 * @param gatewayAddress gateway IP address
	 * @param maskAddress subnet mask
	 * @param iface the router interface out which packets should 
	 *        be sent to reach the destination or gateway
	 * @param metric Metric
	 */
	public RouteEntry(int destinationAddress, int gatewayAddress, 
			int maskAddress, Iface iface, int metric)
	{
		this.destinationAddress = destinationAddress;
		this.gatewayAddress = gatewayAddress;
		this.maskAddress = maskAddress;
		this.iface = iface;
		this.hops = metric;
	}
	
	// Overload constructor:
	public RouteEntry(final int destinationAddress, int gatewayAddress, final int maskAddress,
			Iface iface, int metric, int timeout, RouteTable table)
	{
		this.destinationAddress = destinationAddress;
		this.gatewayAddress = gatewayAddress;
		this.maskAddress = maskAddress;
		this.iface = iface;
		this.hops = metric;
		
		if (timeout != 0)
		{
			this.routeEntryRemovalTimer = new Timer();
			this.routeTimerTask = new RouteEntryTimerTask(table, destinationAddress, maskAddress);
			routeEntryRemovalTimer.schedule(this.routeTimerTask, timeout);
		}
	}
	
	public void serviceTimer(int timeout)
	{
		System.out.println("Servicing timer!");
		this.routeEntryRemovalTimer.cancel();
		this.routeEntryRemovalTimer = new Timer();
		this.routeTimerTask = new RouteEntryTimerTask(this.localTable, this.destinationAddress, this.maskAddress);
		routeEntryRemovalTimer.schedule(this.routeTimerTask, timeout);
	}
	
	/**
	 * @return destination IP address
	 */
	public int getDestinationAddress()
	{ return this.destinationAddress; }
	
	/**
	 * @return gateway IP address
	 */
	public int getGatewayAddress()
	{ return this.gatewayAddress; }

    public void setGatewayAddress(int gatewayAddress)
    { this.gatewayAddress = gatewayAddress; }
	
	/**
	 * @return subnet mask 
	 */
	public int getMaskAddress()
	{ return this.maskAddress; }
	
	/**
	 * @return the router interface out which packets should be sent to 
	 *         reach the destination or gateway
	 */
	public Iface getInterface()
	{ return this.iface; }

    public void setInterface(Iface iface)
    { this.iface = iface; }
    
	/**
	 * @return the number of hops to get to a given route entry
	 */
	public int getHops()
	{ return this.hops; }

    public void setHops(int hopArg)
    { this.hops = hopArg; }
	
	public String toString()
	{
		return String.format("%s \t%s \t%s \t%s",
				IPv4.fromIPv4Address(this.destinationAddress),
				IPv4.fromIPv4Address(this.gatewayAddress),
				IPv4.fromIPv4Address(this.maskAddress),
				this.iface.getName());
	}
}
