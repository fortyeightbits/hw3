package edu.wisc.cs.sdn.vnet.rt;

import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.*;
import net.floodlightcontroller.packet.ICMP.ICMP_TYPES;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	
	private ArpQueueHandler arpHandler;
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
		this.arpHandler = new ArpQueueHandler(arpCache, this);
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
	
	public void initRip()
	{		
		// Create the packets, populate, encapsulate them and send RIP request on all interfaces

		Ethernet etherPacket = new Ethernet();
		IPv4 ipPacket = new IPv4();
		UDP udpPacket = new UDP();
		RIPv2 ripPacket = new RIPv2();
		
		// Populating RIP packet:
		ripPacket.setCommand(RIPv2.COMMAND_REQUEST);
		
		// Populating UDP packet:
		udpPacket.setSourcePort(UDP.RIP_PORT);
		udpPacket.setDestinationPort(UDP.RIP_PORT);
		
		// Populating Ipv4 packet:
		int address = IPv4.toIPv4Address("224.0.0.9");
		ipPacket.setDestinationAddress(address);
		
		// Populating Ethernet packet:
		etherPacket.setDestinationMACAddress(Ethernet.BROADCAST_MAC);
		etherPacket.setEtherType(Ethernet.TYPE_IPv4);
		
		// Populate our routeTable, then send out request to all interfaces
		for (Iface iface : this.interfaces.values())
		{
			// Populating routeTable
			int maskIp = iface.getSubnetMask();
			int dstIp = iface.getIpAddress() & maskIp;
			this.routeTable.insert(dstIp, 0, maskIp, iface, 0);
			System.out.println("Initial Route Table Entry -- dstIp: " + dstIp + " gateway: " + 0 + " maskIp: " + maskIp);
			
			ipPacket.setSourceAddress(iface.getIpAddress());
			etherPacket.setSourceMACAddress(iface.getMacAddress().toBytes());
			udpPacket.setPayload(ripPacket);
			ipPacket.setPayload(udpPacket);
			etherPacket.setPayload(ipPacket);
			//Sending the request out
			
			this.sendPacket(etherPacket, iface);
		}
		
		// Set timer and task to continuously broadcast RIP responses
		Timer ripBroadcastTimer = new Timer();
		ripBroadcastTimer.scheduleAtFixedRate(new RipTimerTask(this) {
			
			@Override
			public void run() 
			{
				System.out.println("Sending RIP Response");
				// Send promiscuous RIP response broadcast
				Ethernet etherPacket = new Ethernet();
				IPv4 ipPacket = new IPv4();
				UDP udpPacket = new UDP();
				RIPv2 ripPacket = new RIPv2();
				
				// Populating RIP packet:
				ripPacket.setCommand(RIPv2.COMMAND_RESPONSE);
				
				// Populate RIP entries
				for (RouteEntry entry : this.localRouter.routeTable)
				{				
					RIPv2Entry ripEntry = new RIPv2Entry();
					ripEntry.setAddress(entry.getDestinationAddress());
					ripEntry.setAddressFamily(RIPv2Entry.ADDRESS_FAMILY_IPv4);
					ripEntry.setNextHopAddress(entry.getGatewayAddress());
					ripEntry.setSubnetMask(entry.getMaskAddress());
					
					ripPacket.addEntry(ripEntry);
				}
				
				// Populating UDP packet:
				udpPacket.setSourcePort(UDP.RIP_PORT);
				udpPacket.setDestinationPort(UDP.RIP_PORT);
				
				// Populating Ipv4 packet:
				int address = IPv4.toIPv4Address("224.0.0.9");
				ipPacket.setDestinationAddress(address);
				
				// Populating Ethernet packet:
				etherPacket.setDestinationMACAddress(Ethernet.BROADCAST_MAC);
				etherPacket.setEtherType(Ethernet.TYPE_IPv4);
				
				for (Iface iface : this.localRouter.interfaces.values())
				{
					ipPacket.setSourceAddress(iface.getIpAddress());
					etherPacket.setSourceMACAddress(iface.getMacAddress().toBytes());
					udpPacket.setPayload(ripPacket);
					ipPacket.setPayload(udpPacket);
					etherPacket.setPayload(ipPacket);
					//Sending the request out
					this.localRouter.sendPacket(etherPacket, iface);
				}
			}
		}, 10000, 10000);
	}
	
	private void handleRipRequest(Ethernet etherPacketIn, Iface inIface)
	{
		System.out.println("Handling RIP Request");
		// Send promiscuous RIP response broadcast
		Ethernet etherPacket = new Ethernet();
		IPv4 ipPacket = new IPv4();
		UDP udpPacket = new UDP();
		RIPv2 ripPacket = new RIPv2();
		
		// Populating RIP packet:
		ripPacket.setCommand(RIPv2.COMMAND_RESPONSE);
		
		// Populate RIP entries
		for (RouteEntry entry : this.routeTable)
		{				
			RIPv2Entry ripEntry = new RIPv2Entry();
			ripEntry.setAddress(entry.getDestinationAddress());
			ripEntry.setAddressFamily(RIPv2Entry.ADDRESS_FAMILY_IPv4);
			ripEntry.setNextHopAddress(entry.getGatewayAddress());
			ripEntry.setSubnetMask(entry.getMaskAddress());
			
			ripPacket.addEntry(ripEntry);
		}
		
		// Populating UDP packet:
		udpPacket.setSourcePort(UDP.RIP_PORT);
		udpPacket.setDestinationPort(UDP.RIP_PORT);
		
		// Populating Ipv4 packet:
		int address = IPv4.toIPv4Address("224.0.0.9");
		ipPacket.setDestinationAddress(address);
		
		// Populating Ethernet packet:
		etherPacket.setDestinationMACAddress(Ethernet.BROADCAST_MAC);
		etherPacket.setEtherType(Ethernet.TYPE_IPv4);
		
		ipPacket.setSourceAddress(inIface.getIpAddress());
		etherPacket.setSourceMACAddress(inIface.getMacAddress().toBytes());
		udpPacket.setPayload(ripPacket);
		ipPacket.setPayload(udpPacket);
		etherPacket.setPayload(ipPacket);
		//Sending the request out
		this.sendPacket(etherPacket, inIface);

	}
	
	private void handleRipResponse(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("Handling RIP response");
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		UDP udpPacket = (UDP)ipPacket.getPayload();
		RIPv2 ripPacket = (RIPv2)udpPacket.getPayload();
				
		for(RIPv2Entry ripEntry : ripPacket.getEntries())
		{
			RouteEntry routeEntry = this.routeTable.lookup(ripEntry.getAddress());
			if (routeEntry!= null)
			{
				if (routeEntry.getGatewayAddress() != 0 && ipPacket.getSourceAddress() == routeEntry.getGatewayAddress())
				{
					routeEntry.serviceTimer(RouteEntry.DEFAULT_TIMEOUT, this.routeTable);
				
				}
				// Check if it takes less hops:
				if(ripEntry.getMetric()+1 < routeEntry.getHops())
				{
					routeEntry.setHops(ripEntry.getMetric() + 1);
//					routeEntry.setGatewayAddress(ipPacket.getSourceAddress());
					routeEntry.setGatewayAddress(ripEntry.getNextHopAddress());
					routeEntry.setInterface(inIface);
				}
			}
			else
			{
				this.routeTable.insertTimedEntry(ripEntry.getAddress(), ipPacket.getSourceAddress(), ripEntry.getSubnetMask(), inIface, ripEntry.getMetric() + 1, RouteEntry.DEFAULT_TIMEOUT);
			}
		}
		
		System.out.println("Updated routing table:");
		System.out.println(" ================================");
		System.out.println(this.routeTable.toString());
		System.out.println(" ================================");
	}

	private void handleArpReplyPacket(Ethernet etherPacket, Iface inIface)
	{
		ARP arpPacket = (ARP)etherPacket.getPayload();
		arpHandler.appendToArpAndCheckPending(arpPacket, inIface);
	}
	
	private void handleArpRequestPacket(Ethernet etherPacket, Iface inIface)
	{	
		// Cast and convert ARP packet IP to int:
		ARP inArpPacket = (ARP)etherPacket.getPayload();
		
		int targetIp = ByteBuffer.wrap(inArpPacket.getTargetProtocolAddress()).getInt();
		System.out.println("ARP request packet is looking for this IP: " + IPv4.fromIPv4Address(targetIp));
		System.out.println("My IP is: " + IPv4.fromIPv4Address(inIface.getIpAddress()));
		if (inIface.getIpAddress() == targetIp)
		{
			byte [] interfaceMac = inIface.getMacAddress().toBytes();
			byte [] replyToMac = inArpPacket.getSenderHardwareAddress();
			// send reply ARP
			Ethernet replyEtherPacket = new Ethernet();
			ARP replyArpPacket = new ARP();
			
			// First, we populate the Ethernet packet:
			replyEtherPacket.setEtherType(Ethernet.TYPE_ARP);
			replyEtherPacket.setDestinationMACAddress(replyToMac);
			replyEtherPacket.setSourceMACAddress(interfaceMac);
			
			// Next, we populate the ARP packet:
			replyArpPacket.setHardwareType(ARP.HW_TYPE_ETHERNET);
			replyArpPacket.setProtocolType(ARP.PROTO_TYPE_IP);
			replyArpPacket.setHardwareAddressLength((byte)Ethernet.DATALAYER_ADDRESS_LENGTH);
			replyArpPacket.setProtocolAddressLength(IPv4.IP_PROTO_LENGTH);
			replyArpPacket.setOpCode(ARP.OP_REPLY);
			replyArpPacket.setSenderHardwareAddress(interfaceMac);
			replyArpPacket.setSenderProtocolAddress(inIface.getIpAddress());
			replyArpPacket.setTargetHardwareAddress(replyToMac);
			replyArpPacket.setTargetProtocolAddress(inArpPacket.getSenderProtocolAddress());
			
			// Store ARP in payload of Ethernet packet and send out
			replyEtherPacket.setPayload(replyArpPacket);
			System.out.println("Sending ARP reply packet: " + replyEtherPacket.toString());
			this.sendPacket(replyEtherPacket, inIface);
		}
		else if(this.routeTable.lookup(targetIp) != null)
		{
			byte [] interfaceMac = inIface.getMacAddress().toBytes();
			byte [] replyToMac = inArpPacket.getSenderHardwareAddress();
			// send reply ARP
			Ethernet replyEtherPacket = new Ethernet();
			ARP replyArpPacket = new ARP();
			
			// First, we populate the Ethernet packet:
			replyEtherPacket.setEtherType(Ethernet.TYPE_ARP);
			replyEtherPacket.setDestinationMACAddress(replyToMac);
			replyEtherPacket.setSourceMACAddress(interfaceMac);
			
			// Next, we populate the ARP packet:
			replyArpPacket.setHardwareType(ARP.HW_TYPE_ETHERNET);
			replyArpPacket.setProtocolType(ARP.PROTO_TYPE_IP);
			replyArpPacket.setHardwareAddressLength((byte)Ethernet.DATALAYER_ADDRESS_LENGTH);
			replyArpPacket.setProtocolAddressLength(IPv4.IP_PROTO_LENGTH);
			replyArpPacket.setOpCode(ARP.OP_REPLY);
			replyArpPacket.setSenderHardwareAddress(interfaceMac);
			replyArpPacket.setSenderProtocolAddress(inArpPacket.getTargetProtocolAddress());
			replyArpPacket.setTargetHardwareAddress(replyToMac);
			replyArpPacket.setTargetProtocolAddress(inArpPacket.getSenderProtocolAddress());
			
			// Store ARP in payload of Ethernet packet and send out
			replyEtherPacket.setPayload(replyArpPacket);
			System.out.println("Sending ARP reply packet: " + replyEtherPacket.toString());
			this.sendPacket(replyEtherPacket, inIface);
		}
	}
	
	public void sendArpRequest(int destinationIp)
	{		
		// Create new Ethernet and ARP packets
		Ethernet etherPacket = new Ethernet();
		ARP arpPacket = new ARP();
		
        // Find matching route table entry 
        RouteEntry bestMatch = this.routeTable.lookup(destinationIp);
        Iface bestMatchInterface = bestMatch.getInterface();
		
		// First, we populate the Ethernet packet:
		etherPacket.setEtherType(Ethernet.TYPE_ARP);
		etherPacket.setDestinationMACAddress(Ethernet.BROADCAST_MAC);
		etherPacket.setSourceMACAddress(bestMatchInterface.getMacAddress().toBytes());
		
		// Next, we populate the ARP packet:
		arpPacket.setHardwareType(ARP.HW_TYPE_ETHERNET);
		arpPacket.setProtocolType(ARP.PROTO_TYPE_IP);
		arpPacket.setHardwareAddressLength((byte)Ethernet.DATALAYER_ADDRESS_LENGTH);
		arpPacket.setProtocolAddressLength(IPv4.IP_PROTO_LENGTH);
		arpPacket.setOpCode(ARP.OP_REQUEST);
		arpPacket.setSenderHardwareAddress(bestMatchInterface.getMacAddress().toBytes());
		arpPacket.setSenderProtocolAddress(bestMatchInterface.getIpAddress());
		arpPacket.setTargetHardwareAddress(new byte[] {0, 0, 0, 0 ,0 ,0});

		arpPacket.setTargetProtocolAddress(destinationIp);
		
		// Store ARP in payload of Ethernet packet and send out
		etherPacket.setPayload(arpPacket);
		this.sendPacket(etherPacket, bestMatchInterface);
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
		case Ethernet.TYPE_ARP:
			if (((ARP)etherPacket.getPayload()).getOpCode() == ARP.OP_REQUEST)
			{
				System.out.println("Sending ARP reply");
				this.handleArpRequestPacket(etherPacket, inIface);			
			}
			else if (((ARP)etherPacket.getPayload()).getOpCode() == ARP.OP_REPLY)
			{
				System.out.println("Handling ARP reply");
				this.handleArpReplyPacket(etherPacket, inIface);
			}
			break;
		default:
			break;
		}
		
		/********************************************************************/
	}
	
	private void sendIcmpEcho(Ethernet etherPacket, Iface inIface)
	{
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		int srcIP = ipPacket.getSourceAddress();
		ICMP inICMP = (ICMP)ipPacket.getPayload();
		
		// Building Ethernet header on reply:
		Ethernet reply = new Ethernet();
		reply.setEtherType(Ethernet.TYPE_IPv4);
		RouteEntry entry = this.routeTable.lookup(srcIP);
		Iface outIface = entry.getInterface();
        reply.setSourceMACAddress(outIface.getMacAddress().toBytes()); //set Source MAC (this router's interface)
		int nextHop = entry.getGatewayAddress();
        if (nextHop == 0)
        { 
			nextHop = srcIP; 
		}
        ArpEntry arpEntry = this.arpCache.lookup(nextHop);
        reply.setDestinationMACAddress(arpEntry.getMac().toBytes()); //set Destination MAC (original sender)
        
		//IP
		IPv4 ip = new IPv4(); 
		ip.setTtl((byte)64); //set TTL
		ip.setProtocol(IPv4.PROTOCOL_ICMP); //setProtocol
		ip.setSourceAddress(inIface.getIpAddress()); //set Source IP
		ip.setDestinationAddress(srcIP); //set Destination IP
		
		//ICMP
		ICMP icmp = new ICMP();
		icmp.setIcmpType((byte)0);
		icmp.setIcmpCode((byte)0);
		icmp.setChecksum(inICMP.getChecksum());
		icmp.setPayload(inICMP.getPayload());
		
		reply.setPayload(ip);
		ip.setPayload(icmp);
		
		//send packet
		this.sendPacket(reply, outIface);
	}
	
	public void sendIcmpMsg(Ethernet etherPacket, Iface inIface, ICMP.ICMP_TYPES errorCode)
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
        if (arpEntry != null)
        {
        	ether.setDestinationMACAddress(arpEntry.getMac().toBytes()); //set Destination MAC (original sender)
        }
		
		//IP
		IPv4 ip = new IPv4(); 
		ip.setTtl((byte)64); //set TTL
		ip.setProtocol(IPv4.PROTOCOL_ICMP); //setProtocol
		ip.setSourceAddress(inIface.getIpAddress()); //set Source IP
		ip.setDestinationAddress(srcIP); //set Destination IP
		
		//ICMP
		ICMP icmp = new ICMP();
		int icmpType = 0;
		int icmpCode = 0;
		
		switch(errorCode)
		{
		case ICMP_CODE_TIMEOUT:
			icmpType = 11;
			icmpCode = 0;
			break;
		case ICMP_CODE_UNREACHABLE_NET:
			icmpType = 3;
			icmpCode = 0;
			break;
		case ICMP_CODE_UNREACHABLE_HOST:
			icmpType = 3;
			icmpCode = 1;
			break;
		case ICMP_CODE_UNREACHABLE_PORT:
			icmpType = 3;
			icmpCode = 3;
			break;
		default:
			throw new IllegalArgumentException();
		}
		
		icmp.setIcmpType((byte)icmpType);
		icmp.setIcmpCode((byte)icmpCode);
		
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
        
		// Check if it's a UDP RIP packet:
		if ((ipPacket.getDestinationAddress() == IPv4.toIPv4Address("224.0.0.9"))
			&& (ipPacket.getProtocol() == IPv4.PROTOCOL_UDP)
			&& (((UDP)ipPacket.getPayload()).getDestinationPort() == 520)
			)
		{
			UDP udpPacket = (UDP)ipPacket.getPayload();
			RIPv2 ripPacket = (RIPv2)udpPacket.getPayload();
			
			if (ripPacket.getCommand() == RIPv2.COMMAND_REQUEST)
			{
				handleRipRequest(etherPacket, inIface);
			}
			else if (ripPacket.getCommand() == RIPv2.COMMAND_RESPONSE)
			{
				handleRipResponse(etherPacket, inIface);
			}
			return;
		}

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
			sendIcmpMsg(etherPacket, inIface, ICMP_TYPES.ICMP_CODE_TIMEOUT);
			return; 
		}
        
        // Reset checksum now that TTL is decremented
        ipPacket.resetChecksum();
        
        // Check if packet is destined for one of router's interfaces
        for (Iface iface : this.interfaces.values())
        {
        	if (ipPacket.getDestinationAddress() == iface.getIpAddress())
        	{
        		byte protocolType = ipPacket.getProtocol();
        		if (protocolType == IPv4.PROTOCOL_TCP || protocolType == IPv4.PROTOCOL_UDP)
        		{
        			sendIcmpMsg(etherPacket, inIface, ICMP_TYPES.ICMP_CODE_UNREACHABLE_PORT);
            		return; 
        		}
        		else if(protocolType == IPv4.PROTOCOL_ICMP)
        		{
        			// echo function here
        			sendIcmpEcho(etherPacket, inIface);
        		}
        	}
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
			sendIcmpMsg(etherPacket, inIface, ICMP_TYPES.ICMP_CODE_UNREACHABLE_NET);
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
        System.out.println("Gateway Address: " + IPv4.fromIPv4Address(nextHop));
        if (0 == nextHop)
        { nextHop = dstAddr; }

        // Set destination MAC address in Ethernet header
        ArpEntry arpEntry = this.arpCache.lookup(nextHop);
        if (null == arpEntry)
        { 
        	sendArpRequest(((IPv4)etherPacket.getPayload()).getDestinationAddress());
        	arpHandler.enqueueAndBeginTimer(etherPacket);
        	return; 
        }
        etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());
        
        this.sendPacket(etherPacket, outIface);
    }
}
