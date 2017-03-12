package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.*;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}
	
	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));
		
		/********************************************************************/
		/* TODO: Handle packets                                             */
		
		switch(etherPacket.getEtherType())
		{
		case Ethernet.TYPE_IPv4:
			this.handleIpPacket(etherPacket, inIface);
			break;
		// Ignore all other packet types, for now
		}
		
		/********************************************************************/
	}
	
	private void sendIcmpMsg(Ethernet etherPacket, Iface inIface, int errorCode)
	{
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
        int srcIP = ipPacket.getSourceAddress();
		
		//Ethernet 
		Ethernet ether = new Ethernet();
		ether.setEtherType(Ethernet.TYPE_IPv4); //set EtherType
		RouteEntry entry = this.routeTable.lookup(srcIP);
		Iface outIface = entry.getInterface();
        ether.setSourceMACAddress(outIface.getMacAddress().toBytes()); //set Source MAC (this router's interface)
		int nextHop = entry.getGatewayAddress();
        if (nextHop == 0)
        { 
			nextHop = srcIP; 
		}
        ArpEntry arpEntry = this.arpCache.lookup(nextHop);
        ether.setDestinationMACAddress(arpEntry.getMac().toBytes()); //set Destination MAC (original sender)
		
		//IP
		IPv4 ip = new IPv4(); 
		ip.setTtl((byte)64); //set TTL
		ip.setProtocol(IPv4.PROTOCOL_ICMP); //setProtocol
		ip.setSourceAddress(inIface.getIpAddress()); //set Source IP
		ip.setDestinationAddress(srcIP); //set Destination IP
		
		//ICMP
		ICMP icmp = new ICMP();
		int type = 0;
		if (errorCode == 0) //TTL errorCode
		{
			type = 11;
		}
		else if (errorCode == 1) //unreachable errorCode
		{
			type = 3;
		}
		icmp.setIcmpType((byte)type);
		icmp.setIcmpCode((byte)0);
		
		//Payload
		int headerLengthPlus12 = (ipPacket.getHeaderLength()*4) + 8 + 4;
		byte[] serializedIpPacket = ipPacket.serialize();
		byte[] dataArray = new byte[headerLengthPlus12];
		int ipPacketCnt = 0;
		for (int i = 4; i < headerLengthPlus12; ++i)
		{
			dataArray[i] = serializedIpPacket[ipPacketCnt];
			ipPacketCnt++;
		}
		Data data = new Data(dataArray);
		
		ether.setPayload(ip);
		ip.setPayload(icmp);
		icmp.setPayload(data);
		
		//send packet
		this.sendPacket(ether, outIface);
		System.out.println("Sending ICMP -- IP " + IPv4.fromIPv4Address(ip.getDestinationAddress()));
	}
	
	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
	{
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
		
		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
        System.out.println("Handle IP packet");

        // Verify checksum
        short origCksum = ipPacket.getChecksum();
        ipPacket.resetChecksum();
        byte[] serialized = ipPacket.serialize();
        ipPacket.deserialize(serialized, 0, serialized.length);
        short calcCksum = ipPacket.getChecksum();
        if (origCksum != calcCksum)
        { return; }
        
        // Check TTL
        ipPacket.setTtl((byte)(ipPacket.getTtl()-1));
        if (0 == ipPacket.getTtl())
        { 
			sendIcmpMsg(etherPacket, inIface, 0);
			return; 
		}
        
        // Reset checksum now that TTL is decremented
        ipPacket.resetChecksum();
        
        // Check if packet is destined for one of router's interfaces
        for (Iface iface : this.interfaces.values())
        {
        	if (ipPacket.getDestinationAddress() == iface.getIpAddress())
        	{ return; }
        }
		
        // Do route lookup and forward
        this.forwardIpPacket(etherPacket, inIface);
	}

    private void forwardIpPacket(Ethernet etherPacket, Iface inIface)
    {
        // Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
        System.out.println("Forward IP packet");
		
		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
        int dstAddr = ipPacket.getDestinationAddress();

        // Find matching route table entry 
        RouteEntry bestMatch = this.routeTable.lookup(dstAddr);

        // If no entry matched, do nothing
        if (null == bestMatch)
        { 
			sendIcmpMsg(etherPacket, inIface, 1);
			return; 
		}

        // Make sure we don't sent a packet back out the interface it came in
        Iface outIface = bestMatch.getInterface();
        if (outIface == inIface)
        { return; }

        // Set source MAC address in Ethernet header
        etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());

        // If no gateway, then nextHop is IP destination
        int nextHop = bestMatch.getGatewayAddress();
        if (0 == nextHop)
        { nextHop = dstAddr; }

        // Set destination MAC address in Ethernet header
        ArpEntry arpEntry = this.arpCache.lookup(nextHop);
        if (null == arpEntry)
        { return; }
        etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());
        
        this.sendPacket(etherPacket, outIface);
    }
}
