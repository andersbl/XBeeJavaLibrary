/**
 * Copyright (c) 2014 Digi International Inc.,
 * All rights not expressly granted are reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Digi International Inc. 11001 Bren Road East, Minnetonka, MN 55343
 * =======================================================================
 */
package com.digi.xbee.api;

import java.io.IOException;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digi.xbee.api.connection.IConnectionInterface;
import com.digi.xbee.api.connection.DataReader;
import com.digi.xbee.api.connection.serial.SerialPortParameters;
import com.digi.xbee.api.exceptions.ATCommandException;
import com.digi.xbee.api.exceptions.InterfaceNotOpenException;
import com.digi.xbee.api.exceptions.InvalidOperatingModeException;
import com.digi.xbee.api.exceptions.OperationNotSupportedException;
import com.digi.xbee.api.exceptions.TimeoutException;
import com.digi.xbee.api.exceptions.TransmitException;
import com.digi.xbee.api.exceptions.XBeeException;
import com.digi.xbee.api.io.IOLine;
import com.digi.xbee.api.io.IOMode;
import com.digi.xbee.api.io.IOSample;
import com.digi.xbee.api.io.IOValue;
import com.digi.xbee.api.listeners.IPacketReceiveListener;
import com.digi.xbee.api.listeners.ISerialDataReceiveListener;
import com.digi.xbee.api.models.ATCommand;
import com.digi.xbee.api.models.ATCommandResponse;
import com.digi.xbee.api.models.ATCommandStatus;
import com.digi.xbee.api.models.HardwareVersion;
import com.digi.xbee.api.models.HardwareVersionEnum;
import com.digi.xbee.api.models.XBee16BitAddress;
import com.digi.xbee.api.models.XBee64BitAddress;
import com.digi.xbee.api.models.OperatingMode;
import com.digi.xbee.api.models.XBeeProtocol;
import com.digi.xbee.api.models.XBeeTransmitOptions;
import com.digi.xbee.api.models.XBeeTransmitStatus;
import com.digi.xbee.api.packet.XBeeAPIPacket;
import com.digi.xbee.api.packet.APIFrameType;
import com.digi.xbee.api.packet.XBeePacket;
import com.digi.xbee.api.packet.common.ATCommandPacket;
import com.digi.xbee.api.packet.common.ATCommandResponsePacket;
import com.digi.xbee.api.packet.common.IODataSampleRxIndicatorPacket;
import com.digi.xbee.api.packet.common.RemoteATCommandPacket;
import com.digi.xbee.api.packet.common.RemoteATCommandResponsePacket;
import com.digi.xbee.api.packet.common.TransmitStatusPacket;
import com.digi.xbee.api.packet.raw.RX16IOPacket;
import com.digi.xbee.api.packet.raw.RX64IOPacket;
import com.digi.xbee.api.packet.raw.TXStatusPacket;
import com.digi.xbee.api.utils.ByteUtils;
import com.digi.xbee.api.utils.HexUtils;

public abstract class AbstractXBeeDevice {
	
	// Constants.
	protected static int DEFAULT_RECEIVE_TIMETOUT = 2000; // 2.0 seconds of timeout to receive packet and command responses.
	protected static int TIMEOUT_BEFORE_COMMAND_MODE = 1200;
	protected static int TIMEOUT_ENTER_COMMAND_MODE = 1500;
	
	// Variables.
	protected IConnectionInterface connectionInterface;
	
	protected DataReader dataReader = null;
	
	protected XBeeProtocol xbeeProtocol = XBeeProtocol.UNKNOWN;
	
	protected OperatingMode operatingMode = OperatingMode.UNKNOWN;
	
	protected XBee16BitAddress xbee16BitAddress;
	protected XBee64BitAddress xbee64BitAddress;
	
	protected int currentFrameID = 0xFF;
	protected int receiveTimeout = DEFAULT_RECEIVE_TIMETOUT;
	
	protected Logger logger;
	
	private String nodeID;
	private String firmwareVersion;
	
	private HardwareVersion hardwareVersion;
	
	protected AbstractXBeeDevice localXBeeDevice;
	
	private Object ioLock = new Object();
	
	private boolean ioPacketReceived = false;
	
	private byte[] ioPacketPayload;
	
	/**
	 * Class constructor. Instantiates a new {@code XBeeDevice} object in the 
	 * given port name and baud rate.
	 * 
	 * @param port Serial port name where XBee device is attached to.
	 * @param baudRate Serial port baud rate to communicate with the device. 
	 *                 Other connection parameters will be set as default (8 
	 *                 data bits, 1 stop bit, no parity, no flow control).
	 * 
	 * @throws NullPointerException if {@code port == null}.
	 * @throws IllegalArgumentException if {@code baudRate < 0}.
	 */
	public AbstractXBeeDevice(String port, int baudRate) {
		this(XBee.createConnectiontionInterface(port, baudRate));
	}
	
	/**
	 * Class constructor. Instantiates a new {@code XBeeDevice} object in the 
	 * given serial port name and settings.
	 * 
	 * @param port Serial port name where XBee device is attached to.
	 * @param baudRate Serial port baud rate to communicate with the device.
	 * @param dataBits Serial port data bits.
	 * @param stopBits Serial port data bits.
	 * @param parity Serial port data bits.
	 * @param flowControl Serial port data bits.
	 * 
	 * @throws NullPointerException if {@code port == null}.
	 * @throws IllegalArgumentException if {@code baudRate < 0} or
	 *                                  if {@code dataBits < 0} or
	 *                                  if {@code stopBits < 0} or
	 *                                  if {@code parity < 0} or
	 *                                  if {@code flowControl < 0}.
	 */
	public AbstractXBeeDevice(String port, int baudRate, int dataBits, int stopBits, int parity, int flowControl) {
		this(port, new SerialPortParameters(baudRate, dataBits, stopBits, parity, flowControl));
	}
	
	/**
	 * Class constructor. Instantiates a new {@code XBeeDevice} object in the 
	 * given serial port name and parameters.
	 * 
	 * @param port Serial port name where XBee device is attached to.
	 * @param serialPortParameters Object containing the serial port parameters.
	 * 
	 * @throws NullPointerException if {@code port == null} or
	 *                              if {@code serialPortParameters == null}.
	 * 
	 * @see SerialPortParameters
	 */
	public AbstractXBeeDevice(String port, SerialPortParameters serialPortParameters) {
		this(XBee.createConnectiontionInterface(port, serialPortParameters));
	}
	
	/**
	 * Class constructor. Instantiates a new {@code XBeeDevice} object with the 
	 * given connection interface.
	 * 
	 * @param connectionInterface The connection interface with the physical 
	 *                            XBee device.
	 * 
	 * @throws NullPointerException if {@code connectionInterface == null}.
	 * 
	 * @see IConnectionInterface
	 */
	public AbstractXBeeDevice(IConnectionInterface connectionInterface) {
		if (connectionInterface == null)
			throw new NullPointerException("ConnectionInterface cannot be null.");
		
		this.connectionInterface = connectionInterface;
		this.logger = LoggerFactory.getLogger(this.getClass());
		logger.debug(toString() + "Using the connection interface {}.", 
				connectionInterface.getClass().getSimpleName());
	}
	
	/**
	 * Class constructor. Instantiates a new {@code RemoteXBeeDevice} object 
	 * with the given local {@code XBeeDevice} which contains the connection 
	 * interface to be used.
	 * 
	 * @param localXBeeDevice The local XBee device that will behave as 
	 *                        connection interface to communicate with this 
	 *                        remote XBee device.
	 * @param xbee64BitAddress The 64-bit address to identify this remote XBee 
	 *                         device.
	 * @throws NullPointerException if {@code localXBeeDevice == null} or
	 *                              if {@code xbee64BitAddress == null}.
	 * 
	 * @see XBee64BitAddress
	 */
	public AbstractXBeeDevice(XBeeDevice localXBeeDevice, XBee64BitAddress xbee64BitAddress) {
		if (localXBeeDevice == null)
			throw new NullPointerException("Local XBee device cannot be null.");
		if (xbee64BitAddress == null)
			throw new NullPointerException("XBee 64 bit address of the remote device cannot be null.");
		if (localXBeeDevice.isRemote())
			throw new IllegalArgumentException("The given local XBee device is remote.");
		
		this.localXBeeDevice = localXBeeDevice;
		this.connectionInterface = localXBeeDevice.getConnectionInterface();
		this.xbee64BitAddress = xbee64BitAddress;
		this.logger = LoggerFactory.getLogger(this.getClass());
		logger.debug(toString() + "Using the connection interface {}.", 
				connectionInterface.getClass().getSimpleName());
	}
	
	/**
	 * Retrieves the connection interface associated to this XBee device.
	 * 
	 * @return XBee device's connection interface.
	 * 
	 * @see IConnectionInterface
	 */
	public IConnectionInterface getConnectionInterface() {
		return connectionInterface;
	}
	
	/**
	 * Retrieves whether or not the XBee device is a remote device.
	 * 
	 * @return {@code true} if the XBee device is a remote device, 
	 *         {@code false} otherwise.
	 */
	abstract public boolean isRemote();
	
	/**
	 * Initializes the XBee device. Reads some parameters from the device and 
	 * obtains its protocol.
	 * 
	 * @throws InvalidOperatingModeException if the operating mode of the device is not supported.
	 * @throws TimeoutException if there is a timeout reading the parameters.
	 * @throws OperationNotSupportedException if any of the operations performed in the method is not supported.
	 * @throws ATCommandException if there is any problem sending the AT commands.
	 * @throws XBeeException if there is any other XBee related exception.
	 * @throws InterfaceNotOpenException if the device is not open.
	 * 
	 * @see #xbee64BitAddress
	 * @see #nodeID
	 * @see #hardwareVersion
	 * @see #firmwareVersion
	 * @see #xbeeProtocol
	 * @see HardwareVersion
	 * @see HardwareVersionEnum
	 * @see XBeeProtocol
	 */
	protected void initializeDevice() 
			throws InvalidOperatingModeException, TimeoutException, OperationNotSupportedException, 
			ATCommandException, XBeeException {
		ATCommandResponse response = null;
		// Get the 64-bit address.
		if (xbee64BitAddress == null || xbee64BitAddress == XBee64BitAddress.UNKNOWN_ADDRESS) {
			String addressHigh;
			String addressLow;
			try {
				response = sendATCommand(new ATCommand("SH"));
			} catch (IOException e) {
				throw new XBeeException("Error writing in the communication interface.", e);
			}
			if (response == null || response.getResponse() == null)
				throw new OperationNotSupportedException("Couldn't get the SH value.");
			if (response.getResponseStatus() != ATCommandStatus.OK)
				throw new ATCommandException("Couldn't get the SH value.", response.getResponseStatus());
			addressHigh = HexUtils.byteArrayToHexString(response.getResponse());
			try {
				response = sendATCommand(new ATCommand("SL"));
			} catch (IOException e) {
				throw new XBeeException("Error writing in the communication interface.", e);
			}
			if (response == null || response.getResponse() == null)
				throw new OperationNotSupportedException("Couldn't get the SL value.");
			if (response.getResponseStatus() != ATCommandStatus.OK)
				throw new ATCommandException("Couldn't get the SL value.", response.getResponseStatus());
			addressLow = HexUtils.byteArrayToHexString(response.getResponse());
			while(addressLow.length() < 8)
				addressLow = "0" + addressLow;
			xbee64BitAddress = new XBee64BitAddress(addressHigh + addressLow);
		}
		// Get the Node ID.
		if (nodeID == null) {
			try {
				response = sendATCommand(new ATCommand("NI"));
			} catch (IOException e) {
				throw new XBeeException("Error writing in the communication interface.", e);
			}
			if (response == null || response.getResponse() == null)
				throw new OperationNotSupportedException("Couldn't get the NI value.");
			if (response.getResponseStatus() != ATCommandStatus.OK)
				throw new ATCommandException("Couldn't get the NI value.", response.getResponseStatus());
			nodeID = new String(response.getResponse());
		}
		// Get the hardware version.
		if (hardwareVersion == null) {
			try {
				response = sendATCommand(new ATCommand("HV"));
			} catch (IOException e) {
				throw new XBeeException("Error writing in the communication interface.", e);
			}
			if (response == null || response.getResponse() == null)
				throw new OperationNotSupportedException("Couldn't get the HV value.");
			if (response.getResponseStatus() != ATCommandStatus.OK)
				throw new ATCommandException("Couldn't get the HV value.", response.getResponseStatus());
			hardwareVersion = HardwareVersion.get(response.getResponse()[0]);
		}
		// Get the firmware version.
		if (firmwareVersion == null) {
			try {
				response = sendATCommand(new ATCommand("VR"));
			} catch (IOException e) {
				throw new XBeeException("Error writing in the communication interface.", e);
			}
			if (response == null || response.getResponse() == null)
				throw new OperationNotSupportedException("Couldn't get the VR value.");
			if (response.getResponseStatus() != ATCommandStatus.OK)
				throw new ATCommandException("Couldn't get the VR value.", response.getResponseStatus());
			firmwareVersion = HexUtils.byteArrayToHexString(response.getResponse());
		}
		// Obtain the device protocol.
		xbeeProtocol = XBeeProtocol.determineProtocol(hardwareVersion, firmwareVersion);
	}
	
	/**
	 * Retrieves the 16-bit address of the XBee device.
	 * 
	 * @return The 16-bit address of the XBee device.
	 * 
	 * @see XBee16BitAddress
	 */
	public XBee16BitAddress get16BitAddress() {
		return xbee16BitAddress;
	}
	
	/**
	 * Retrieves the 64-bit address of the XBee device.
	 * 
	 * @return The 64-bit address of the XBee device.
	 * 
	 * @see XBee64BitAddress
	 */
	public XBee64BitAddress get64BitAddress() {
		return xbee64BitAddress;
	}
	
	/**
	 * Retrieves the Operating mode (AT, API or API escaped) of the XBee device.
	 * 
	 * @return The operating mode of the XBee device.
	 * 
	 * @see OperatingMode
	 */
	protected OperatingMode getOperatingMode() {
		if (isRemote())
			return localXBeeDevice.getOperatingMode();
		return operatingMode;
	}
	
	/**
	 * Retrieves the XBee Protocol of the XBee device.
	 * 
	 * @return The XBee device protocol.
	 * 
	 * @see XBeeProtocol
	 * @see #setXBeeProtocol(XBeeProtocol)
	 */
	public XBeeProtocol getXBeeProtocol() {
		return xbeeProtocol;
	}
	
	/**
	 * Retrieves the node identifier of the XBee device.
	 * 
	 * @return The node identifier of the device.
	 * 
	 * @see #setNodeID(String)
	 * @see #getNodeID(boolean)
	 */
	public String getNodeID() {
		return nodeID;
	}
	
	/**
	 * Retrieves the node identifier of the XBee device. This method allows for refreshing 
	 * the value reading it again from the device or retrieving the cached value.
	 * 
	 * @param refresh Indicates whether or not the value of the node ID should be refreshed 
	 *                (read again from the device)
	 * @return The node identifier of the device.
	 * 
	 * @throws TimeoutException if there is a timeout reading the node ID value.
	 * @throws XBeeException if there is any other XBee related exception.
	 * @throws InterfaceNotOpenException if the device is not open.
	 * 
	 * @see #setNodeID(String)
	 * @see #getNodeID()
	 */
	public String getNodeID(boolean refresh) throws TimeoutException, XBeeException {
		if (!refresh)
			return nodeID;
		ATCommandResponse response;
		try {
			response = sendATCommand(new ATCommand("NI"));
		} catch (IOException e) {
			throw new XBeeException("Error writing in the communication interface.", e);
		}
		
		if (response == null || response.getResponse() == null)
			throw new OperationNotSupportedException("Couldn't get the NI value.");
		if (response.getResponseStatus() != ATCommandStatus.OK)
			throw new ATCommandException("Couldn't get the NI value.", response.getResponseStatus());
		
		nodeID = new String(response.getResponse());
		return nodeID;
	}
	
	/**
	 * Sets the node identifier of the XBee device.
	 * 
	 * @param nodeID The new node id of the device.
	 * 
	 * @throws TimeoutException if there is a timeout setting the node ID value.
	 * @throws XBeeException if there is any other XBee related exception.
	 * @throws InterfaceNotOpenException if the device is not open.
	 * @throws NullPointerException if {@code nodeID == null}.
	 * @throws IllegalArgumentException if {@code nodeID.length > 20}.
	 * 
	 * @see #getNodeID()
	 * @see #getNodeID(boolean)
	 */
	public void setNodeID(String nodeID) throws TimeoutException, XBeeException {
		if (nodeID == null)
			throw new NullPointerException("Node ID cannot be null.");
		if (nodeID.length() > 20)
			throw new IllegalArgumentException("Node ID length must be less than 21.");
		if (!connectionInterface.isOpen())
			throw new InterfaceNotOpenException();
		
		ATCommandResponse response;
		try {
			response = sendATCommand(new ATCommand("NI", nodeID));
		} catch (IOException e) {
			throw new XBeeException("Error writing in the communication interface.", e);
		}
		
		if (response == null)
			throw new OperationNotSupportedException("Couldn't set the NI value.");
		if (response.getResponseStatus() != ATCommandStatus.OK)
			throw new ATCommandException("Couldn't set the NI value.", response.getResponseStatus());
		
		this.nodeID = nodeID;
	}
	
	/**
	 * Retrieves the firmware version (hexadecimal string value) of the XBee device.
	 * 
	 * @return The firmware version of the XBee device.
	 */
	public String getFirmwareVersion() {
		return firmwareVersion;
	}
	
	/**
	 * Retrieves the hardware version of the XBee device.
	 * 
	 * @return The hardware version of the XBee device.
	 * 
	 * @see HardwareVersion
	 * @see HardwareVersionEnum
	 */
	public HardwareVersion getHardwareVersion() {
		return hardwareVersion;
	}
	
	/**
	 * Starts listening for packets in the provided packets listener.
	 * 
	 * <p>The provided listener is added to the list of listeners to be notified
	 * when new packets are received. If the listener has been already 
	 * included, this method does nothing.</p>
	 * 
	 * @param listener Listener to be notified when new packets are received.
	 * 
	 * @see IPacketReceiveListener
	 * @see #stopListeningForPackets(IPacketReceiveListener)
	 */
	protected void startListeningForPackets(IPacketReceiveListener listener) {
		if (dataReader == null)
			return;
		dataReader.addPacketReceiveListener(listener);
	}
	
	/**
	 * Stops listening for packets in the provided packets listener. 
	 * 
	 * <p>The provided listener is removed from the list of packets listeners. 
	 * If the listener was not in the list this method does nothing.</p>
	 * 
	 * @param listener Listener to be removed from the list of listeners.
	 * 
	 * @see IPacketReceiveListener
	 * @see #startListeningForPackets(IPacketReceiveListener)
	 */
	protected void stopListeningForPackets(IPacketReceiveListener listener) {
		if (dataReader == null)
			return;
		dataReader.removePacketReceiveListener(listener);
	}
	
	/**
	 * Starts listening for serial data in the provided serial data listener.
	 *  
	 * <p>The provided listener is added to the list of listeners to be notified
	 * when new serial data is received. If the listener has been already 
	 * included this method does nothing.</p>
	 * 
	 * @param listener Listener to be notified when new serial data is received.
	 * 
	 * @see ISerialDataReceiveListener
	 * @see #stopListeningForSerialData(ISerialDataReceiveListener)
	 */
	protected void startListeningForSerialData(ISerialDataReceiveListener listener) {
		if (dataReader == null)
			return;
		dataReader.addSerialDatatReceiveListener(listener);
	}
	
	/**
	 * Stops listening for serial data in the provided serial data listener.
	 * 
	 * <p>The provided listener is removed from the list of serial data 
	 * listeners. If the listener was not in the list this method does nothing.</p>
	 * 
	 * @param listener Listener to be removed from the list of listeners.
	 * 
	 * @see ISerialDataReceiveListener
	 * @see #startListeningForSerialData(ISerialDataReceiveListener)
	 */
	protected void stopListeningForSerialData(ISerialDataReceiveListener listener) {
		if (dataReader == null)
			return;
		dataReader.removeSerialDataReceiveListener(listener);
	}
	
	/**
	 * Sends the given AT command and waits for answer or until the configured 
	 * receive timeout expires.
	 * 
	 * <p>The received timeout is configured using the {@code setReceiveTimeout}
	 * method and can be consulted with {@code getReceiveTimeout} method.</p>
	 * 
	 * @param command AT command to be sent.
	 * @return An {@code ATCommandResponse} object containing the response of 
	 *         the command or {@code null} if there is no response.
	 *         
	 * @throws InvalidOperatingModeException if the operating mode is different than {@link OperatingMode#API} and 
	 *                                       {@link OperatingMode#API_ESCAPE}.
	 * @throws TimeoutException if the configured time expires while waiting 
	 *                          for the command reply.
	 * @throws IOException if an I/O error occurs while sending the AT command.
	 * @throws InterfaceNotOpenException if the device is not open.
	 * @throws NullPointerException if {@code command == null}.
	 * 
	 * @see ATCommand
	 * @see ATCommandResponse
	 * @see #setReceiveTimeout(int)
	 * @see #getReceiveTimeout()
	 */
	protected ATCommandResponse sendATCommand(ATCommand command) 
			throws InvalidOperatingModeException, TimeoutException, IOException {
		// Check if command is null.
		if (command == null)
			throw new NullPointerException("AT command cannot be null.");
		// Check connection.
		if (!connectionInterface.isOpen())
			throw new InterfaceNotOpenException();
		
		ATCommandResponse response = null;
		OperatingMode operatingMode = getOperatingMode();
		switch (operatingMode) {
		case AT:
		case UNKNOWN:
		default:
			throw new InvalidOperatingModeException(operatingMode);
		case API:
		case API_ESCAPE:
			// Create the corresponding AT command packet depending on if the device is local or remote.
			XBeePacket packet;
			if (isRemote())
				packet = new RemoteATCommandPacket(getNextFrameID(), get64BitAddress(), XBee16BitAddress.UNKNOWN_ADDRESS, XBeeTransmitOptions.NONE, command.getCommand(), command.getParameter());
			else
				packet = new ATCommandPacket(getNextFrameID(), command.getCommand(), command.getParameter());
			if (command.getParameter() == null)
				logger.debug(toString() + "Sending AT command '{}'.", command.getCommand());
			else
				logger.debug(toString() + "Sending AT command '{} {}'.", command.getCommand(), HexUtils.prettyHexString(command.getParameter()));
			try {
				// Send the packet and build the corresponding response depending on if the device is local or remote.
				XBeePacket answerPacket;
				if (isRemote())
					answerPacket = localXBeeDevice.sendXBeePacket(packet);
				else
					answerPacket = sendXBeePacket(packet);
				if (answerPacket instanceof ATCommandResponsePacket)
					response = new ATCommandResponse(command, ((ATCommandResponsePacket)answerPacket).getCommandValue(), ((ATCommandResponsePacket)answerPacket).getStatus());
				else if (answerPacket instanceof RemoteATCommandResponsePacket)
					response = new ATCommandResponse(command, ((RemoteATCommandResponsePacket)answerPacket).getCommandValue(), ((RemoteATCommandResponsePacket)answerPacket).getStatus());
				
				if (response.getResponse() != null)
					logger.debug(toString() + "AT command response: {}.", HexUtils.prettyHexString(response.getResponse()));
				else
					logger.debug(toString() + "AT command response: null.");
			} catch (ClassCastException e) {
				logger.error("Received an invalid packet type after sending an AT command packet." + e);
			}
		}
		return response;
	}
	
	/**
	 * Sends the given XBee packet asynchronously.
	 * 
	 * <p>To be notified when the answer is received, use 
	 * {@link #sendXBeePacket(XBeePacket, IPacketReceiveListener)}.</p>
	 * 
	 * @param packet XBee packet to be sent asynchronously.
	 * 
	 * @throws InvalidOperatingModeException if the operating mode is different than {@link OperatingMode#API} and 
	 *                                       {@link OperatingMode#API_ESCAPE}.
	 * @throws IOException if an I/O error occurs while sending the XBee packet.
	 * @throws InterfaceNotOpenException if the device is not open.
	 * @throws NullPointerException if {@code packet == null}.
	 * 
	 * @see XBeePacket
	 * @see #sendXBeePacketAsync(XBeePacket)
	 * @see #sendXBeePacket(XBeePacket)
	 * @see #sendXBeePacket(XBeePacket, boolean)
	 * @see #sendXBeePacket(XBeePacket, IPacketReceiveListener)
	 * @see #sendXBeePacket(XBeePacket, IPacketReceiveListener, boolean)
	 */
	protected void sendXBeePacketAsync(XBeePacket packet) 
			throws InvalidOperatingModeException, IOException {
		sendXBeePacket(packet, null);
	}
	
	/**
	 * Sends the given XBee packet asynchronously and registers the given packet
	 * listener (if not {@code null}) to wait for an answer.
	 * 
	 * @param packet XBee packet to be sent.
	 * @param packetReceiveListener Listener for the operation, {@code null} 
	 *                              not to be notified when the answer arrives.
	 *                              
	 * @throws InvalidOperatingModeException if the operating mode is different than {@link OperatingMode#API} and 
	 *                                       {@link OperatingMode#API_ESCAPE}.
	 * @throws IOException if an I/O error occurs while sending the XBee packet.
	 * @throws InterfaceNotOpenException if the device is not open.
	 * @throws NullPointerException if {@code packet == null}.
	 * 
	 * @see XBeePacket
	 * @see IPacketReceiveListener
	 * @see #sendXBeePacket(XBeePacket)
	 * @see #sendXBeePacket(XBeePacket, boolean)
	 * @see #sendXBeePacket(XBeePacket, IPacketReceiveListener)
	 * @see #sendXBeePacketAsync(XBeePacket)
	 * @see #sendXBeePacketAsync(XBeePacket, boolean)
	 */
	protected void sendXBeePacket(XBeePacket packet, IPacketReceiveListener packetReceiveListener)
			throws InvalidOperatingModeException, IOException {
		// Check if the packet to send is null.
		if (packet == null)
			throw new NullPointerException("XBee packet cannot be null.");
		// Check connection.
		if (!connectionInterface.isOpen())
			throw new InterfaceNotOpenException();
		
		OperatingMode operatingMode = getOperatingMode();
		switch (operatingMode) {
		case AT:
		case UNKNOWN:
		default:
			throw new InvalidOperatingModeException(operatingMode);
		case API:
		case API_ESCAPE:
			// Add the required frame ID and subscribe listener if given.
			if (packet instanceof XBeeAPIPacket) {
				if (((XBeeAPIPacket)packet).needsAPIFrameID()) {
					if (((XBeeAPIPacket)packet).getFrameID() == XBeeAPIPacket.NO_FRAME_ID)
						((XBeeAPIPacket)packet).setFrameID(getNextFrameID());
					if (packetReceiveListener != null)
						dataReader.addPacketReceiveListener(packetReceiveListener, ((XBeeAPIPacket)packet).getFrameID());
				} else if (packetReceiveListener != null)
					dataReader.addPacketReceiveListener(packetReceiveListener);
			}
			
			// Write packet data.
			writePacket(packet);
			break;
		}
	}
	
	/**
	 * Sends the given XBee packet synchronously and blocks until response is 
	 * received or receive timeout is reached.
	 * 
	 * <p>The received timeout is configured using the {@code setReceiveTimeout}
	 * method and can be consulted with {@code getReceiveTimeout} method.</p>
	 * 
	 * <p>Use {@link #sendXBeePacketAsync(XBeePacket, boolean)} for non-blocking 
	 * operations.</p>
	 * 
	 * @param packet XBee packet to be sent.
	 * @return An {@code XBeePacket} containing the response of the sent packet 
	 *         or {@code null} if there is no response.
	 *         
	 * @throws InvalidOperatingModeException if the operating mode is different than {@link OperatingMode#API} and 
	 *                                       {@link OperatingMode#API_ESCAPE}.
	 * @throws TimeoutException if the configured time expires while waiting for the packet reply.
	 * @throws IOException if an I/O error occurs while sending the XBee packet.
	 * @throws InterfaceNotOpenException if the device is not open.
	 * @throws NullPointerException if {@code packet == null}.
	 * 
	 * @see XBeePacket
	 * @see #sendXBeePacket(XBeePacket)
	 * @see #sendXBeePacket(XBeePacket, IPacketReceiveListener)
	 * @see #sendXBeePacket(XBeePacket, IPacketReceiveListener, boolean)
	 * @see #sendXBeePacketAsync(XBeePacket)
	 * @see #sendXBeePacketAsync(XBeePacket, boolean)
	 * @see #setReceiveTimeout(int)
	 * @see #getReceiveTimeout()
	 */
	protected XBeePacket sendXBeePacket(final XBeePacket packet) 
			throws InvalidOperatingModeException, TimeoutException, IOException {
		// Check if the packet to send is null.
		if (packet == null)
			throw new NullPointerException("XBee packet cannot be null.");
		// Check connection.
		if (!connectionInterface.isOpen())
			throw new InterfaceNotOpenException();
		
		OperatingMode operatingMode = getOperatingMode();
		switch (operatingMode) {
		case AT:
		case UNKNOWN:
		default:
			throw new InvalidOperatingModeException(operatingMode);
		case API:
		case API_ESCAPE:
			// Build response container.
			ArrayList<XBeePacket> responseList = new ArrayList<XBeePacket>();
			
			// If the packet does not need frame ID, send it async. and return null.
			if (packet instanceof XBeeAPIPacket) {
				if (!((XBeeAPIPacket)packet).needsAPIFrameID()) {
					sendXBeePacketAsync(packet);
					return null;
				}
			} else {
				sendXBeePacketAsync(packet);
				return null;
			}
			
			// Add the required frame ID to the packet if necessary.
			insertFrameID(packet);
			
			// Generate a packet received listener for the packet to be sent.
			IPacketReceiveListener packetReceiveListener = createPacketReceivedListener(packet, responseList);
			
			// Add the packet listener to the data reader.
			startListeningForPackets(packetReceiveListener);
			
			// Write the packet data.
			writePacket(packet);
			try {
				// Wait for response or timeout.
				synchronized (responseList) {
					try {
						responseList.wait(receiveTimeout);
					} catch (InterruptedException e) {}
				}
				// After the wait check if we received any response, if not throw timeout exception.
				if (responseList.size() < 1)
					throw new TimeoutException();
				// Return the received packet.
				return responseList.get(0);
			} finally {
				// Always remove the packet listener from the list.
				stopListeningForPackets(packetReceiveListener);
			}
		}
	}
	
	/**
	 * Insert (if possible) the next frame ID stored in the device to the 
	 * provided packet.
	 * 
	 * @param xbeePacket The packet to add the frame ID.
	 * 
	 * @see XBeePacket
	 */
	private void insertFrameID(XBeePacket xbeePacket) {
		if (xbeePacket instanceof XBeeAPIPacket)
			return;
		
		if (((XBeeAPIPacket)xbeePacket).needsAPIFrameID() && ((XBeeAPIPacket)xbeePacket).getFrameID() == XBeeAPIPacket.NO_FRAME_ID)
			((XBeeAPIPacket)xbeePacket).setFrameID(getNextFrameID());
	}
	
	/**
	 * Retrieves the packet listener corresponding to the provided sent packet. 
	 * 
	 * <p>The listener will filter those packets  matching with the Frame ID of 
	 * the sent packet storing them in the provided responseList array.</p>
	 * 
	 * @param sentPacket The packet sent.
	 * @param responseList List of packets received that correspond to the 
	 *                     frame ID of the packet sent.
	 * 
	 * @return A packet receive listener that will filter the packets received 
	 *         corresponding to the sent one.
	 *         
	 * @see IPacketReceiveListener
	 * @see XBeePacket
	 */
	private IPacketReceiveListener createPacketReceivedListener(final XBeePacket sentPacket, final ArrayList<XBeePacket> responseList) {
		IPacketReceiveListener packetReceiveListener = new IPacketReceiveListener() {
			/*
			 * (non-Javadoc)
			 * @see com.digi.xbee.api.listeners.IPacketReceiveListener#packetReceived(com.digi.xbee.api.packet.XBeePacket)
			 */
			@Override
			public void packetReceived(XBeePacket receivedPacket) {
				// Check if it is the packet we are waiting for.
				if (((XBeeAPIPacket)receivedPacket).checkFrameID((((XBeeAPIPacket)sentPacket).getFrameID()))) {
					// Security check to avoid class cast exceptions. It has been observed that parallel processes 
					// using the same connection but with different frame index may collide and cause this exception at some point.
					if (sentPacket instanceof XBeeAPIPacket
							&& receivedPacket instanceof XBeeAPIPacket) {
						XBeeAPIPacket sentAPIPacket = (XBeeAPIPacket)sentPacket;
						XBeeAPIPacket receivedAPIPacket = (XBeeAPIPacket)receivedPacket;
						
						// If the packet sent is an AT command, verify that the received one is an AT command response and 
						// the command matches in both packets.
						if (sentAPIPacket.getFrameType() == APIFrameType.AT_COMMAND) {
							if (receivedAPIPacket.getFrameType() != APIFrameType.AT_COMMAND_RESPONSE)
								return;
							if (!((ATCommandPacket)sentAPIPacket).getCommand().equalsIgnoreCase(((ATCommandResponsePacket)receivedPacket).getCommand()))
								return;
						}
						// If the packet sent is a remote AT command, verify that the received one is a remote AT command response and 
						// the command matches in both packets.
						if (sentAPIPacket.getFrameType() == APIFrameType.REMOTE_AT_COMMAND_REQUEST) {
							if (receivedAPIPacket.getFrameType() != APIFrameType.REMOTE_AT_COMMAND_RESPONSE)
								return;
							if (!((RemoteATCommandPacket)sentAPIPacket).getCommand().equalsIgnoreCase(((RemoteATCommandResponsePacket)receivedPacket).getCommand()))
								return;
						}
					}
					
					// Verify that the sent packet is not the received one! This can happen when the echo mode is enabled in the 
					// serial port.
					if (!isSamePacket(sentPacket, receivedPacket)) {
						responseList.add(receivedPacket);
						synchronized (responseList) {
							responseList.notify();
						}
					}
				}
			}
		};
		
		return packetReceiveListener;
	}
	
	/**
	 * Retrieves whether or not the sent packet is the same than the received one.
	 * 
	 * @param sentPacket The packet sent.
	 * @param receivedPacket The packet received.
	 * 
	 * @return {@code true} if the sent packet is the same than the received 
	 *         one, {@code false} otherwise.
	 *         
	 * @see XBeePacket
	 */
	private boolean isSamePacket(XBeePacket sentPacket, XBeePacket receivedPacket) {
		// TODO Should not we implement the {@code equals} method in the XBeePacket??
		if (HexUtils.byteArrayToHexString(sentPacket.generateByteArray()).equals(HexUtils.byteArrayToHexString(receivedPacket.generateByteArray())))
			return true;
		return false;
	}
	
	/**
	 * Writes the given XBee packet in the connection interface.
	 * 
	 * @param packet XBee packet to be written.
	 * 
	 * @throws IOException if an I/O error occurs while writing the XBee packet 
	 *                     in the connection interface.
	 */
	private void writePacket(XBeePacket packet) throws IOException {
		logger.debug(toString() + "Sending XBee packet: \n{}", packet.toPrettyString());
		// Write bytes with the required escaping mode.
		switch (operatingMode) {
		case API:
		default:
			connectionInterface.writeData(packet.generateByteArray());
			break;
		case API_ESCAPE:
			connectionInterface.writeData(packet.generateByteArrayEscaped());
			break;
		}
	}
	
	/**
	 * Retrieves the next Frame ID of the XBee protocol.
	 * 
	 * @return The next Frame ID.
	 */
	protected int getNextFrameID() {
		if (isRemote())
			return localXBeeDevice.getNextFrameID();
		if (currentFrameID == 0xff) {
			// Reset counter.
			currentFrameID = 1;
		} else
			currentFrameID ++;
		return currentFrameID;
	}
	
	/**
	 * Sends the provided {@code XBeePacket} and determines if the transmission 
	 * status is success for synchronous transmissions. If the status is not 
	 * success, an {@code TransmitException} is thrown.
	 * 
	 * @param packet The {@code XBeePacket} to be sent.
	 * @param asyncTransmission Determines whether or not the transmission 
	 *                          should be made asynchronously.
	 * 
	 * @throws TransmitException if {@code packet} is not an instance of {@code TransmitStatusPacket} or 
	 *                           if {@code packet} is not an instance of {@code TXStatusPacket} or 
	 *                           if its transmit status is different than {@code XBeeTransmitStatus.SUCCESS}.
	 * @throws XBeeException if there is any other XBee related error.
	 * 
	 * @see XBeePacket
	 */
	protected void sendAndCheckXBeePacket(XBeePacket packet, boolean asyncTransmission) throws TransmitException, XBeeException {
		XBeePacket receivedPacket = null;
		
		// Send the XBee packet.
		try {
			if (asyncTransmission)
				sendXBeePacketAsync(packet);
			else
				receivedPacket = sendXBeePacket(packet);
		} catch (IOException e) {
			throw new XBeeException("Error writing in the communication interface.", e);
		}
		
		// If the transmission is async. we are done.
		if (asyncTransmission)
			return;
		
		// Check if the packet received is a valid transmit status packet.
		if (receivedPacket == null)
			throw new TransmitException(null);
		if (receivedPacket instanceof TransmitStatusPacket) {
			if (((TransmitStatusPacket)receivedPacket).getTransmitStatus() == null)
				throw new TransmitException(null);
			else if (((TransmitStatusPacket)receivedPacket).getTransmitStatus() != XBeeTransmitStatus.SUCCESS)
				throw new TransmitException(((TransmitStatusPacket)receivedPacket).getTransmitStatus());
		} else if (receivedPacket instanceof TXStatusPacket) {
			if (((TXStatusPacket)receivedPacket).getTransmitStatus() == null)
				throw new TransmitException(null);
			else if (((TXStatusPacket)receivedPacket).getTransmitStatus() != XBeeTransmitStatus.SUCCESS)
				throw new TransmitException(((TXStatusPacket)receivedPacket).getTransmitStatus());
		} else
			throw new TransmitException(null);
	}
	
	/**
	 * Sets the configuration of the given IO line.
	 * 
	 * @param ioLine The IO line to configure.
	 * @param mode The IO mode to set to the IO line.
	 * 
	 * @throws TimeoutException if there is a timeout sending the set 
	 *                          configuration command.
	 * @throws XBeeException if there is any other XBee related exception.
	 * @throws InterfaceNotOpenException if the device is not open.
	 * @throws NullPointerException if {@code ioLine == null} or
	 *                              if {@code ioMode == null}.
	 * 
	 * @see IOLine
	 * @see IOMode
	 * @see #getIOConfiguration(IOLine)
	 */
	public void setIOConfiguration(IOLine ioLine, IOMode ioMode) throws TimeoutException, XBeeException {
		// Check IO line.
		if (ioLine == null)
			throw new NullPointerException("IO line cannot be null.");
		if (ioMode == null)
			throw new NullPointerException("IO mode cannot be null.");
		// Check connection.
		if (!connectionInterface.isOpen())
			throw new InterfaceNotOpenException();
		
		// Create and send the AT Command.
		String atCommand = ioLine.getConfigurationATCommand();
		ATCommandResponse response = null;
		try {
			response = sendATCommand(new ATCommand(atCommand, new byte[]{(byte)ioMode.getID()}));
		} catch (IOException e) {
			throw new XBeeException("Error writing in the communication interface.", e);
		}
		
		// Check if AT Command response is valid.
		checkATCommandResponseIsValid(response);
	}
	
	/**
	 * Retrieves the configuration mode of the provided IO line.
	 * 
	 * @param ioLine The IO line to get its configuration.
	 * 
	 * @return The IO mode (configuration) of the provided IO line.
	 * 
	 * @throws TimeoutException if there is a timeout sending the get 
	 *                          configuration command.
	 * @throws XBeeException if there is any other XBee related exception.
	 * @throws InterfaceNotOpenException if the device is not open.
	 * @throws NullPointerException if {@code ioLine == null}.
	 * 
	 * @see IOLine
	 * @see IOMode
	 * @see #setIOConfiguration(IOLine, IOMode)
	 */
	public IOMode getIOConfiguration(IOLine ioLine) throws TimeoutException, XBeeException {
		// Check IO line.
		if (ioLine == null)
			throw new NullPointerException("DIO pin cannot be null.");
		// Check connection.
		if (!connectionInterface.isOpen())
			throw new InterfaceNotOpenException();
		
		// Create and send the AT Command.
		ATCommandResponse response = null;
		try {
			response = sendATCommand(new ATCommand(ioLine.getConfigurationATCommand()));
		} catch (IOException e) {
			throw new XBeeException("Error writing in the communication interface.", e);
		}
		
		// Check if AT Command response is valid.
		checkATCommandResponseIsValid(response);
		
		// Check if the response contains the configuration value.
		if (response.getResponse() == null || response.getResponse().length == 0)
			throw new OperationNotSupportedException("Answer does not contain the configuration value.");
		
		// Check if the received configuration mode is valid.
		int ioModeValue = response.getResponse()[0];
		IOMode dioMode = IOMode.getIOMode(ioModeValue, ioLine);
		if (dioMode == null)
			throw new OperationNotSupportedException("Received configuration mode '" + HexUtils.integerToHexString(ioModeValue, 1) + "' is not valid.");
		
		// Return the configuration mode.
		return dioMode;
	}
	
	/**
	 * Sets the digital value (high or low) to the provided IO line.
	 * 
	 * @param ioLine The IO line to set its value.
	 * @param value The IOValue to set to the IO line ({@code HIGH} or 
	 *              {@code LOW}).
	 * 
	 * @throws TimeoutException if there is a timeout sending the set DIO 
	 *                          command.
	 * @throws XBeeException if there is any other XBee related exception.
	 * @throws InterfaceNotOpenException if the device is not open.
	 * @throws NullPointerException if {@code ioLine == null} or 
	 *                              if {@code ioValue == null}.
	 * 
	 * @see IOLine
	 * @see IOValue
	 * @see IOMode#DIGITAL_OUT_HIGH
	 * @see IOMode#DIGITAL_OUT_LOW
	 * @see #getIOConfiguration(IOLine)
	 * @see #setIOConfiguration(IOLine, IOMode)
	 */
	public void setDIOValue(IOLine ioLine, IOValue ioValue) throws TimeoutException, XBeeException {
		// Check IO line.
		if (ioLine == null)
			throw new NullPointerException("IO line cannot be null.");
		// Check IO value.
		if (ioValue == null)
			throw new NullPointerException("IO value cannot be null.");
		// Check connection.
		if (!connectionInterface.isOpen())
			throw new InterfaceNotOpenException();
		
		// Create and send the AT Command.
		String atCommand = ioLine.getConfigurationATCommand();
		byte[] valueByte = new byte[]{(byte)ioValue.getID()};
		ATCommandResponse response = null;
		try {
			response = sendATCommand(new ATCommand(atCommand, valueByte));
		} catch (IOException e) {
			throw new XBeeException("Error writing in the communication interface.", e);
		}
		
		// Check if AT Command response is valid.
		checkATCommandResponseIsValid(response);
	}
	
	/**
	 * Retrieves the digital value of the provided IO line (must be configured 
	 * as digital I/O).
	 * 
	 * @param ioLine The IO line to get its digital value.
	 * 
	 * @return The digital value corresponding to the provided IO line.
	 * 
	 * @throws TimeoutException if there is a timeout sending the get IO values 
	 *                          command.
	 * @throws XBeeException if there is any other XBee related exception.
	 * @throws InterfaceNotOpenException if the device is not open.
	 * @throws NullPointerException if {@code ioLine == null}.
	 * 
	 * @see IOLine
	 * @see IOMode#DIGITAL_IN
	 * @see IOMode#DIGITAL_OUT_HIGH
	 * @see IOMode#DIGITAL_OUT_LOW
	 * @see #getIOConfiguration(IOLine)
	 * @see #setIOConfiguration(IOLine, IOMode)
	 */
	public IOValue getDIOValue(IOLine ioLine) throws TimeoutException, XBeeException {
		// Obtain an IO Sample from the XBee device.
		IOSample ioSample = getIOSample(ioLine);
		
		// Check if the IO sample contains the expected IO line and value.
		if (!ioSample.hasDigitalValues() || !ioSample.getDigitalValues().containsKey(ioLine))
			throw new OperationNotSupportedException("Answer does not conain digital data for " + ioLine.getName() + ".");
		
		// Return the digital value. 
		return ioSample.getDigitalValues().get(ioLine);
	}
	
	/**
	 * Sets the duty cycle (in %) of the provided IO line. 
	 * 
	 * <p>IO line must be PWM capable({@code hasPWMCapability()}) and 
	 * it must be configured as PWM Output ({@code IOMode.PWM}).</p>
	 * 
	 * @param ioLine The IO line to set its duty cycle value.
	 * @param value The duty cycle of the PWM.
	 * 
	 * @throws TimeoutException if there is a timeout sending the set PWM duty 
	 *                          cycle command.
	 * @throws XBeeException if there is any other XBee related exception.
	 * @throws InterfaceNotOpenException if the device is not open.
	 * @throws IllegalArgumentException if {@code ioLine.hasPWMCapability() == false} or 
	 *                                  if {@code value < 0} or
	 *                                  if {@code value > 1023}.
	 * @throws NullPointerException if {@code ioLine == null}.
	 * 
	 * @see IOLine
	 * @see IOMode#PWM
	 * @see #getPWMDutyCycle(IOLine)
	 * @see #getIOConfiguration(IOLine)
	 * @see #setIOConfiguration(IOLine, IOMode)
	 */
	public void setPWMDutyCycle(IOLine ioLine, double dutyCycle) throws TimeoutException, XBeeException {
		// Check IO line.
		if (ioLine == null)
			throw new NullPointerException("IO line cannot be null.");
		// Check if the IO line has PWM capability.
		if (!ioLine.hasPWMCapability())
			throw new IllegalArgumentException("Provided IO line does not have PWM capability.");
		// Check duty cycle limits.
		if (dutyCycle < 0 || dutyCycle > 100)
			throw new IllegalArgumentException("Duty Cycle must be between 0% and 100%.");
		// Check connection.
		if (!connectionInterface.isOpen())
			throw new InterfaceNotOpenException();
		
		// Convert the value.
		int finaldutyCycle = (int)(dutyCycle * 1023.0/100.0);
		
		// Create and send the AT Command.
		String atCommand = ioLine.getPWMDutyCycleATCommand();
		ATCommandResponse response = null;
		try {
			response = sendATCommand(new ATCommand(atCommand, ByteUtils.intToByteArray(finaldutyCycle)));
		} catch (IOException e) {
			throw new XBeeException("Error writing in the communication interface.", e);
		}
		
		// Check if AT Command response is valid.
		checkATCommandResponseIsValid(response);
	}
	
	/**
	 * Gets the PWM duty cycle (in %) corresponding to the provided IO line.
	 * 
	 * <p>IO line must be PWM capable ({@code hasPWMCapability()}) and 
	 * it must be configured as PWM Output ({@code IOMode.PWM}).</p>
	 * 
	 * @param ioLine The IO line to get its PWM duty cycle.
	 * 
	 * @return The PWM duty cycle value corresponding to the provided IO line 
	 *         (0% - 100%).
	 * 
	 * @throws TimeoutException if there is a timeout sending the get PWM duty 
	 *                          cycle command.
	 * @throws XBeeException if there is any other XBee related exception.
	 * @throws InterfaceNotOpenException if the device is not open.
	 * @throws IllegalArgumentException if {@code ioLine.hasPWMCapability() == false}.
	 * @throws NullPointerException if {@code ioLine == null}.
	 * 
	 * @see IOLine
	 * @see IOMode#PWM
	 * @see #setPWMDutyCycle(IOLine, double)
	 * @see #getIOConfiguration(IOLine)
	 * @see #setIOConfiguration(IOLine, IOMode)
	 */
	public double getPWMDutyCycle(IOLine ioLine) throws TimeoutException, XBeeException {
		// Check IO line.
		if (ioLine == null)
			throw new NullPointerException("IO line cannot be null.");
		// Check if the IO line has PWM capability.
		if (!ioLine.hasPWMCapability())
			throw new IllegalArgumentException("Provided IO line does not have PWM capability.");
		// Check connection.
		if (!connectionInterface.isOpen())
			throw new InterfaceNotOpenException();
		
		// Create and send the AT Command.
		ATCommandResponse response = null;
		try {
			response = sendATCommand(new ATCommand(ioLine.getPWMDutyCycleATCommand()));
		} catch (IOException e) {
			throw new XBeeException("Error writing in the communication interface.", e);
		}
		
		// Check if AT Command response is valid.
		checkATCommandResponseIsValid(response);
		
		// Check if the response contains the PWM value.
		if (response.getResponse() == null || response.getResponse().length == 0)
			throw new OperationNotSupportedException("Answer does not conain PWM duty cycle value.");
		
		// Return the PWM duty cycle value.
		int readValue = ByteUtils.byteArrayToInt(response.getResponse());
		return Math.round((readValue * 100.0/1023.0) * 100.0) / 100.0;
	}
	
	/**
	 * Retrieves the analog value of the provided IO line (must be configured 
	 * as ADC).
	 * 
	 * @param ioLine The IO line to get its analog value.
	 * 
	 * @return The analog value corresponding to the provided IO line.
	 * 
	 * @throws TimeoutException if there is a timeout sending the get IO values
	 *                          command.
	 * @throws XBeeException if there is any other XBee related exception.
	 * @throws InterfaceNotOpenException if the device is not open.
	 * @throws NullPointerException if {@code ioLine == null}.
	 * 
	 * @see IOLine
	 * @see IOMode#ADC
	 * @see #getIOConfiguration(IOLine)
	 * @see #setIOConfiguration(IOLine, IOMode)
	 */
	public int getADCValue(IOLine ioLine) throws TimeoutException, XBeeException {
		// Check IO line.
		if (ioLine == null)
			throw new NullPointerException("IO line cannot be null.");
		
		// Obtain an IO Sample from the XBee device.
		IOSample ioSample = getIOSample(ioLine);
		
		// Check if the IO sample contains the expected IO line and value.
		if (!ioSample.hasAnalogValues() || !ioSample.getAnalogValues().containsKey(ioLine))
			throw new OperationNotSupportedException("Answer does not conain analog data for " + ioLine.getName() + ".");
		
		// Return the analog value.
		return ioSample.getAnalogValues().get(ioLine);
	}
	
	/**
	 * Checks if the provided {@code ATCommandResponse} is valid throwing an 
	 * {@code ATCommandException} in case it is not.
	 * 
	 * @param response The {@code ATCommandResponse} to check.
	 * 
	 * @throws ATCommandException if {@code response == null} or 
	 *                            if {@code response.getResponseStatus() != ATCommandStatus.OK}.
	 */
	protected void checkATCommandResponseIsValid(ATCommandResponse response) throws ATCommandException {
		if (response == null || response.getResponseStatus() == null)
			throw new ATCommandException(null);
		else if (response.getResponseStatus() != ATCommandStatus.OK)
			throw new ATCommandException(response.getResponseStatus());
	}
	
	/**
	 * Retrieves an IO sample from the XBee device containing the value of the 
	 * provided IO line.
	 * 
	 * @param ioLine The IO line to obtain its associated IO sample.
	 * @return An IO sample containing the value of the provided IO line.
	 * 
	 * @throws InterfaceNotOpenException if the device is not open.
	 * @throws NullPointerException if {@code ioLine == null}.
	 * @throws TimeoutException if there is a timeout getting the IO sample.
	 * @throws XBeeException if there is any other XBee related exception.
	 * 
	 * @see IOSample
	 * @see IOLine
	 */
	private IOSample getIOSample(IOLine ioLine) throws TimeoutException, XBeeException {
		if (ioLine == null)
			throw new NullPointerException("IO line cannot be null.");
		// Check connection.
		if (!connectionInterface.isOpen())
			throw new InterfaceNotOpenException();
		
		// Create and send the AT Command.
		ATCommandResponse response = null;
		try {
			response = sendATCommand(new ATCommand(ioLine.getReadIOATCommand()));
		} catch (IOException e) {
			throw new XBeeException("Error writing in the communication interface.", e);
		}
		
		// Check if AT Command response is valid.
		checkATCommandResponseIsValid(response);
		
		// Try to build an IO Sample from the sample payload.
		byte[] samplePayload;
		IOSample ioSample;
		switch (getXBeeProtocol()) {
		case RAW_802_15_4:
			samplePayload = receiveRaw802IOPacket();
			if (samplePayload == null)
				throw new TimeoutException("Timeout waiting for the IO response packet.");
			break;
		default:
			samplePayload = response.getResponse();
		}
		
		try {
			ioSample = new IOSample(samplePayload);
		} catch (IllegalArgumentException e) {
			throw new XBeeException("Couldn't create the IO sample.", e);
		} catch (NullPointerException e) {
			throw new XBeeException("Couldn't create the IO sample.", e);
		}
		return ioSample;
	}
	
	/**
	 * Retrieves the latest 802.15.4 IO packet and returns its value.
	 * 
	 * @return The value of the latest received 802.15.4 IO packet. 
	 */
	private byte[] receiveRaw802IOPacket() {
		ioPacketReceived = false;
		ioPacketPayload = null;
		startListeningForPackets(IOPacketReceiveListener);
		synchronized (ioLock) {
			try {
				ioLock.wait(receiveTimeout);
			} catch (InterruptedException e) { }
		}
		stopListeningForPackets(IOPacketReceiveListener);
		if (ioPacketReceived)
			return ioPacketPayload;
		return null;
	}
	
	/**
	 * Custom listener for 802.15.4 IO packets. It will try to receive an 802.15.4 IO 
	 * sample packet.
	 * 
	 * <p>When an IO sample packet is received, it saves its payload and notifies 
	 * the object that was waiting for the reception.</p>
	 */
	private IPacketReceiveListener IOPacketReceiveListener = new IPacketReceiveListener() {
		/*
		 * (non-Javadoc)
		 * @see com.digi.xbee.api.listeners.IPacketReceiveListener#packetReceived(com.digi.xbee.api.packet.XBeePacket)
		 */
		@Override
		public void packetReceived(XBeePacket receivedPacket) {
			// Discard non API packets.
			if (!(receivedPacket instanceof XBeeAPIPacket))
				return;
			// If we already have received an IO packet, ignore this packet.
			if (ioPacketReceived)
				return;
			
			// Save the packet value (IO sample payload)
			switch (((XBeeAPIPacket)receivedPacket).getFrameType()) {
			case IO_DATA_SAMPLE_RX_INDICATOR:
				ioPacketPayload = ((IODataSampleRxIndicatorPacket)receivedPacket).getRFData();
				break;
			case RX_IO_16:
				ioPacketPayload = ((RX16IOPacket)receivedPacket).getRFData();
				break;
			case RX_IO_64:
				ioPacketPayload = ((RX64IOPacket)receivedPacket).getRFData();
				break;
			default:
				return;
			}
			// Set the IO packet received flag.
			ioPacketReceived = true;
			
			// Continue execution by notifying the lock object.
			synchronized (ioLock) {
				ioLock.notify();
			}
		}
	};
	
	/**
	 * Performs a software reset on the module and blocks until the process
	 * is completed.
	 * 
	 * @throws TimeoutException if the configured time expires while waiting 
	 *                          for the command reply.
	 * @throws XBeeException if there is any other XBee related exception.
	 */
	abstract public void reset() throws TimeoutException, XBeeException;
	
	/**
	 * Sets the given parameter with the provided value in the XBee device.
	 * 
	 * @param parameter The AT command corresponding to the parameter to be set.
	 * @param parameterValue The value of the parameter to set.
	 * 
	 * @throws IllegalArgumentException if {@code parameter.length() != 2}.
	 * @throws NullPointerException if {@code parameter == null} or 
	 *                              if {@code parameterValue == null}.
	 * @throws TimeoutException if there is a timeout setting the parameter.
	 * @throws XBeeException if there is any other XBee related exception.
	 * 
	 * @see #getParameter(String)
	 * @see #executeParameter(String)
	 */
	public void setParameter(String parameter, byte[] parameterValue) throws TimeoutException, XBeeException {
		if (parameterValue == null)
			throw new NullPointerException("Value of the parameter cannot be null.");
		
		sendParameter(parameter, parameterValue);
	}
	
	/**
	 * Gets the value of the given parameter from the XBee device.
	 * 
	 * @param parameter The AT command corresponding to the parameter to be get.
	 * @return A byte array containing the value of the parameter.
	 * 
	 * @throws IllegalArgumentException if {@code parameter.length() != 2}.
	 * @throws NullPointerException if {@code parameter == null}.
	 * @throws TimeoutException if there is a timeout getting the parameter value.
	 * @throws XBeeException if there is any other XBee related exception.
	 * 
	 * @see #setParameter(String)
	 * @see #executeParameter(String)
	 */
	public byte[] getParameter(String parameter) throws TimeoutException, XBeeException {
		byte[] parameterValue = sendParameter(parameter, null);
		
		// Check if the response is null, if so throw an exception (maybe it was a write-only parameter).
		if (parameterValue == null)
			throw new OperationNotSupportedException("Couldn't get the '" + parameter + "' value.");
		return parameterValue;
	}
	
	/**
	 * Executes the given parameter in the XBee device. This method is intended to be used for 
	 * those parameters that cannot be read or written, they just execute some action in the 
	 * XBee module.
	 * 
	 * @param parameter The AT command corresponding to the parameter to be executed.
	 * 
	 * @throws IllegalArgumentException if {@code parameter.length() != 2}.
	 * @throws NullPointerException if {@code parameter == null}.
	 * @throws TimeoutException if there is a timeout executing the parameter.
	 * @throws XBeeException if there is any other XBee related exception.
	 * 
	 * @see #setParameter(String)
	 * @see #getParameter(String)
	 */
	public void executeParameter(String parameter) throws TimeoutException, XBeeException {
		sendParameter(parameter, null);
	}
	
	/**
	 * Sends the given AT parameter to the XBee device with an optional argument or value 
	 * and returns the response (likely the value) of that parameter in a byte array format.
	 * 
	 * @param parameter The AT command corresponding to the parameter to be executed.
	 * @param parameterValue The value of the parameter to set (if any).
	 * 
	 * @throws IllegalArgumentException if {@code parameter.length() != 2}.
	 * @throws NullPointerException if {@code parameter == null}.
	 * @throws TimeoutException if there is a timeout executing the parameter.
	 * @throws XBeeException if there is any other XBee related exception.
	 * 
	 * @see #setParameter(String)
	 * @see #getParameter(String)
	 * @see #executeParameter(String)
	 */
	private byte[] sendParameter(String parameter, byte[] parameterValue) throws TimeoutException, XBeeException {
		if (parameter == null)
			throw new NullPointerException("Parameter cannot be null.");
		if (parameter.length() != 2)
			throw new IllegalArgumentException("Parameter must contain exactly 2 characters.");
		
		ATCommand atCommand = new ATCommand(parameter, parameterValue);
		
		// Create and send the AT Command.
		ATCommandResponse response = null;
		try {
			response = sendATCommand(atCommand);
		} catch (IOException e) {
			throw new XBeeException("Error writing in the communication interface.", e);
		}
		
		// Check if AT Command response is valid.
		checkATCommandResponseIsValid(response);
		
		// Return the response value.
		return response.getResponse();
	}
	
	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return connectionInterface.toString();
	}
}