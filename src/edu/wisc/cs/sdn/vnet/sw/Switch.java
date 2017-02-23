package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import java.util.Map;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device
{	
	protected DeviceInterfaceMap interfaceMap;
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Switch(String host, DumpFile logfile)
	{
		super(host,logfile);
		interfaceMap = new DeviceInterfaceMap();
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
		
		// Check if incoming MAC is already mapped to an interface, then map if unmapped.		
		interfaceMap.recordIncomingMac(etherPacket.getSourceMAC(), inIface);
		
		// Check if outgoing MAC is mapped in interface, then output from that interface, else output to all
		Iface outputInterface = interfaceMap.getMapInterface(etherPacket.getDestinationMAC());
		
		if (outputInterface != null)
		{
			sendPacket(etherPacket, outputInterface);
		}
		else
		{
			for (Map.Entry<String, Iface> entry : interfaces.entrySet())
			{
				sendPacket(etherPacket, entry.getValue());
			}
		}
	}
}
