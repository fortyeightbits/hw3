package edu.wisc.cs.sdn.vnet.sw;
import java.util.LinkedHashMap;
import java.util.Map;
import edu.wisc.cs.sdn.vnet.Iface;
import edu.wisc.cs.sdn.vnet.TimedIface;
import edu.wisc.cs.sdn.vnet.TimedIfaceCallback;
import net.floodlightcontroller.packet.MACAddress;

public class DeviceInterfaceMap implements TimedIfaceCallback
{
	// Members:
	public Map<MACAddress, TimedIface> deviceInterface;
	
	// Constructor
	public DeviceInterfaceMap()
	{
		deviceInterface = new LinkedHashMap<MACAddress,TimedIface>()
		{
			private static final long serialVersionUID = 1L;
			static final int MAX_DEVICES = 1024; 
			//@Override
			protected boolean removeEldestEntry(Map.Entry<MACAddress, TimedIface> eldest)
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
		if (deviceInterface.containsKey(inAddress))
		{
			//TODO: remove
			System.out.println("MAC found in map");
			// If the Mac is already present, return true and reset its timer
			retVal = true;
			deviceInterface.get(inAddress).resetTtlTimer();
		}
		else
		{
			//TODO: remove
			System.out.println("MAC NOT found in map");
			TimedIface timedPort = new TimedIface(port, inAddress, this) ;
			// Else, we add it to the hashmap
			deviceInterface.put(inAddress, timedPort);
		}
		return retVal;
	}
	
	//////////////////////////////////////////////////////////////////////
	/// param[in] outAddress The mac address of the outgoing packet
	/// return Interface to which address is mapped to, or null if none
	//////////////////////////////////////////////////////////////////////
	public Iface getMapInterface(MACAddress outAddress)
	{
		// TODO: Remove Debug:
		for (Map.Entry<MACAddress, TimedIface> entry : deviceInterface.entrySet())
		{
			System.out.println("MACs:" + entry.toString());
		}
		Iface retVal;
		
		TimedIface foundInterface = deviceInterface.get(outAddress);
		if (foundInterface != null)
		{
			retVal = foundInterface.getIface();
		}
		else
		{
			retVal = null;
		}
		return retVal;
	}
	
	//////////////////////////////////////////////////////////////////////
	/// param[in] outAddress The mac address of the outgoing packet
	/// return Interface to which address is mapped to, or null if none
	//////////////////////////////////////////////////////////////////////
	public void handleTimer(TimedIface invoker)
	{
		//TODO: Remove debug
		System.out.println("TtlTimer handled for " + invoker.getName());
		deviceInterface.remove(invoker.savedMac);
	}
	
	
}
