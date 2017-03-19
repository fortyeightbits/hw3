package edu.wisc.cs.sdn.vnet.rt;

import java.util.HashMap;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;

public class ArpQueueHandler 
{
	private final ArpCache arpCache;
	private final Router localRouter;
	private HashMap<Integer, TimedPacketQueue> packetQueueMap;
	
	// This class assists us in handling the packet queues and ARP replies
	public ArpQueueHandler(ArpCache cache, Router router)
	{
		packetQueueMap = new HashMap<Integer, TimedPacketQueue>();
		arpCache = cache;
		localRouter = router;
	}
	
	public void removeFromMap(Integer IP)
	{
		packetQueueMap.remove(IP);
	}
	
	public void appendToArpAndCheckPending(ARP arpPacket, Iface inIface)
	{
		MACAddress mac = MACAddress.valueOf(arpPacket.getSenderHardwareAddress());
		int ip = IPv4.toIPv4Address(arpPacket.getSenderProtocolAddress());
		
		// Add to cache:
		this.arpCache.insert(mac, ip);
		
		TimedPacketQueue packetQueue = packetQueueMap.get(ip);
		if (packetQueue != null)
		{
			System.out.println("Reply received, sending entire list");
			packetQueue.sendEntireList(inIface, mac.toBytes());
		}
	}
	
	public void enqueueAndBeginTimer(Ethernet inPacket)
	{
		Integer ip = ((IPv4)(inPacket.getPayload())).getDestinationAddress();
		System.out.println("IP: " + ip);
		if (packetQueueMap.containsKey(ip) == false)
		{
			System.out.println("ARP entry not found, now beginning timer and new list.");
			TimedPacketQueue timedQueue = new TimedPacketQueue(localRouter, this);
			packetQueueMap.put(ip, timedQueue);
		}
		else
		{
			System.out.println("ARP already listed as unfound, appending to list.");
		}
		
		packetQueueMap.get(ip).appendPacketToList(inPacket);
	}
}
