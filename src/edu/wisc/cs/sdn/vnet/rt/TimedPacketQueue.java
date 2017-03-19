package edu.wisc.cs.sdn.vnet.rt;

import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.ICMP.ICMP_TYPES;

public class TimedPacketQueue 
{
	// Static consts:
	public static final int TIMER_RESEND = 1000;
	public static final int MAX_RETRIES = 3;
	
	// instance variables:
	private Timer arpResendTimer;
	private LinkedList<Ethernet> packetQueue;
	private final Router localRouter;
	private final ArpQueueHandler localQueueHandler;
	
	// Constructor:
	public TimedPacketQueue(Router router, ArpQueueHandler queueHandler) 
	{
		localRouter = router;
		localQueueHandler = queueHandler;
		packetQueue = new LinkedList<Ethernet>();
		arpResendTimer = new Timer();
		arpResendTimer.scheduleAtFixedRate(new TimerTask() {
			private int count = 0;
			@Override
			public void run() {
				int destinationIp = 0;
				
				if (packetQueue.isEmpty())
				{
					// In case queue is empty, we don't do anything else
					return;
				}
				else
				{
					System.out.println("Timer tick once and resend ARP once");
					// Else, get the first entry's IP address, this should be the same for ALL entries in this list
					IPv4 ipPacket = (IPv4) packetQueue.peekFirst().getPayload();
					destinationIp = ipPacket.getDestinationAddress();
				}
				// Send ARP request to get MAC for this IP
				localRouter.sendArpRequest(destinationIp);
				
				if (++count >= MAX_RETRIES)
				{
					System.out.println("Number of Retries Exceeded");
					// Cancel the timer
					arpResendTimer.cancel();
					
					// Get the first packet
					Ethernet etherPacket = packetQueue.peekFirst();
					// Get IP header
					IPv4 ipPacket = (IPv4)etherPacket.getPayload();
			        int srcAddr = ipPacket.getSourceAddress();

			        // Find matching route table entry 
			        RouteEntry bestMatch = localRouter.getRouteTable().lookup(srcAddr);
			        
			        // Send the ICMP message out the source interface
					localRouter.sendIcmpMsg(etherPacket, bestMatch.getInterface(), ICMP_TYPES.ICMP_CODE_UNREACHABLE_HOST);
					localQueueHandler.removeFromMap(destinationIp);
				}
			}
		}, 0, TIMER_RESEND);
	}
	
	public void appendPacketToList(Ethernet packet)
	{
		System.out.println("Appending to the list");
		packetQueue.addLast(packet);
	}
	
	public void sendEntireList(Iface outIface, byte[] destinationMac)
	{
		int ip = ((IPv4)(packetQueue.peekFirst().getPayload())).getSourceAddress();
		for (Ethernet element : packetQueue)
		{
			System.out.println("Sending List element");
			element.setDestinationMACAddress(destinationMac);
			localRouter.sendPacket(element, outIface);
		}
		arpResendTimer.cancel();
		localQueueHandler.removeFromMap(ip);
	}
}
