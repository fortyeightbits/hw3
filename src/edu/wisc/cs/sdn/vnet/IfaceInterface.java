package edu.wisc.cs.sdn.vnet;

import net.floodlightcontroller.packet.MACAddress;

//Yes, it sounds ridiculous...

public interface IfaceInterface
{
	public String getName();
	
	public void setMacAddress(MACAddress mac);
	
	public MACAddress getMacAddress();

	public void setIpAddress(int ip);
	
	public int getIpAddress();
	
    public void setSubnetMask(int subnetMask);
	
	public int getSubnetMask();

	public String toString();
}