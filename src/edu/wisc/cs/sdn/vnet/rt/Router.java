package edu.wisc.cs.sdn.vnet.rt;

import java.nio.ByteBuffer;
import java.util.Map;

import javax.swing.plaf.synth.SynthSpinnerUI;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;
import net.floodlightcontroller.packet.IPacket;

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
		
		//check if IPv4 packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{
			return; 
		}
		IPv4 header = (IPv4)etherPacket.getPayload();
		
		// Check if checksum is correct
		short calculatedChecksum = Router.calculateIPv4Checksum(header);
		System.out.println("Calculated Checksum: " + calculatedChecksum);
		System.out.println("Packet Checksum: " + header.getChecksum());
		
		if (header.getChecksum() != calculatedChecksum)
		{
			System.out.println("checksum error");
			//return; //TODO
		}

		header.setTtl((byte)(header.getTtl() - 1)); 
		
		if (header.getTtl() == 0)
		{
			System.out.println("packet dropped: TTL");
			return;
		}

		for (Map.Entry<String, Iface> entry : this.interfaces.entrySet())
		{
			if (entry.getValue().getIpAddress() == header.getDestinationAddress())
			{
				System.out.println("packet dropped: interface");
				return;
			}
		}
		System.out.println("forwarding now");
		//FORWARDING PACKETS
		RouteEntry rEntry = routeTable.lookup(header.getDestinationAddress());
		if (rEntry == null)
		{
			System.out.println("RouteEntry null");
			return;
		}
		ArpEntry aEntry = arpCache.lookup(header.getDestinationAddress());
		MACAddress MAC = aEntry.getMac();
		System.out.println("arp MAC: " + MAC.toString());
		etherPacket.setDestinationMACAddress(MAC.toBytes());
		
		MACAddress interfaceMAC = rEntry.getInterface().getMacAddress();
		System.out.println("interface MAC: " + interfaceMAC.toString());
		etherPacket.setSourceMACAddress(interfaceMAC.toBytes());
		
		boolean flag = sendPacket(etherPacket, rEntry.getInterface());
		System.out.println("flag: " + flag);
		/********************************************************************/
	}
	
	/**
	 * Static method to calculate the checksum of a given Ipv4 header
	 * @param header The IPv4 header on which to calculate the checksum.
	 */	
	public static short calculateIPv4Checksum(IPv4 header)
	{
		short retVal = 0;
		short headerLength = header.getHeaderLength();
		
		// We save off the currently stored checksum then synchronize:
		short savedChecksum = header.getChecksum();
		synchronized (header) 
		{
			// Now clear the checksum field so we can calculate it over the header:
			header.resetChecksum();
			ByteBuffer headerAsBytes = ByteBuffer.wrap(header.serialize());
			
            headerAsBytes.rewind();
            int accumulation = 0;
            // headerLength is stored as number of 32bit words, so we multiply 2 to get number of 16bit(shorts)
            for (int i = 0; i < headerLength * 2; ++i) 
            {
                accumulation += 0xffff & headerAsBytes.getShort();
            }
            // Adding carry forward if any:
            accumulation = ((accumulation >> 16) & 0xffff)
                    + (accumulation & 0xffff);
            
            // Inverting the final value and casting to short for final checksum, this will be returned.
            retVal = (short) (~accumulation & 0xffff);
			
			// Restore the previously stored checksum
			header.setChecksum(savedChecksum);
		}	
		return retVal;
	}
}
