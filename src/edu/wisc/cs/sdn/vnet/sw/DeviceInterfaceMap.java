package edu.wisc.cs.sdn.vnet.sw;
import java.util.LinkedHashMap;
import java.util.Map;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.MACAddress;

public class DeviceInterfaceMap
{
	// Members:
	public Map<MACAddress, Iface> deviceInterface;
	
	// Constructor
	public DeviceInterfaceMap()
	{
		deviceInterface = new LinkedHashMap<MACAddress,Iface>()
		{
			private static final long serialVersionUID = 1L;
			static final int MAX_DEVICES = 1024; 
			//@Override
			protected boolean removeEldestEntry(Map.Entry<MACAddress, Iface> eldest)
			{
				return size() > MAX_DEVICES;
			}
		};
	}
	
	// Methods:
	
	//////////////////////////////////////////////////////////////////////
	/// param[in] inAddress The mac address of the incoming packet
	/// param[in] port The interface it entered using
	/// return retVal Mac address exists in table ? true : false
	//////////////////////////////////////////////////////////////////////
	public Boolean recordIncomingMac(MACAddress inAddress, Iface port)
	{
		Boolean retVal = false;
		if (deviceInterface.containsValue(inAddress))
		{
			// If the Mac is already present, return true and do nothing
			retVal = true;
		}
		else
		{
			// Else, we add it to the hashmap
			deviceInterface.put(inAddress, port);
		}
		return retVal;
	}
	
	//////////////////////////////////////////////////////////////////////
	/// param[in] outAddress The mac address of the outgoing packet
	/// return Interface to which address is mapped to, or null if none
	//////////////////////////////////////////////////////////////////////
	public Iface getMapInterface(MACAddress outAddress)
	{
		return deviceInterface.get(outAddress);
	}
	
	
	
	
}
