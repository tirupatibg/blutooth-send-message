package com.citrix.sso.handshake;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import javax.bluetooth.*;
import javax.microedition.io.Connector;
import javax.obex.ClientSession;
import javax.obex.HeaderSet;
import javax.obex.Operation;
import javax.obex.ResponseCodes;

public class MyDiscoveryListener implements DiscoveryListener {

    private static Object lock=new Object();
    private ArrayList<RemoteDevice> devices;
    LocalDevice localDevice ;
    static DiscoveryAgent agent ;
    private MyDiscoveryListener() {
        devices = new ArrayList<RemoteDevice>();
        try {
            localDevice = LocalDevice.getLocalDevice();
        } catch (BluetoothStateException e) {
            e.printStackTrace();
        }
        agent = localDevice.getDiscoveryAgent();
    }

    public static void main(String[] args) {

        MyDiscoveryListener listener =  new MyDiscoveryListener();

        try{

            agent.startInquiry(DiscoveryAgent.GIAC, listener);

            try {
                synchronized(lock){
                    lock.wait();
                }
            }
            catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }


            System.out.println("Device Inquiry Completed. ");


            UUID[] uuidSet = new UUID[1];
            uuidSet[0]=new UUID(0x1105); //OBEX Object Push service

            int[] attrIDs =  new int[] {
                    0x0100 // Service name
            };

            for (RemoteDevice device : listener.devices) {
                agent.searchServices(
                        attrIDs,uuidSet,device,listener);


                try {
                    synchronized(lock){
                        lock.wait();
                    }
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }


                System.out.println("Service search finished.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    @Override
    public void deviceDiscovered(RemoteDevice btDevice, DeviceClass arg1) {
        String name;
        try {
            name = btDevice.getFriendlyName(false);
        } catch (Exception e) {
            name = btDevice.getBluetoothAddress();
        }

        if (name.equalsIgnoreCase("Redmi")) {
            System.out.println("this is my red me device");
            devices.add(btDevice);
            System.out.println("device found: " + name);
            agent.cancelInquiry(this);
        }


    }

    @Override
    public void inquiryCompleted(int arg0) {
        synchronized(lock){
            System.out.println("giving up lock");
            lock.notify();
        }
    }

    @Override
    public void serviceSearchCompleted(int arg0, int arg1) {
        synchronized (lock) {
            lock.notify();
        }
    }

    @Override
    public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {
        for (int i = 0; i < servRecord.length; i++) {
            String url = servRecord[i].getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
            if (url == null) {
                continue;
            }
            DataElement serviceName = servRecord[i].getAttributeValue(0x0100);
            if (serviceName != null) {
                System.out.println("service " + serviceName.getValue() + " found " + url);

                if(serviceName.getValue().toString().contains("OBEX Object Push")){
                    sendMessageToDevice(url);
                }
            } else {
                System.out.println("service found " + url);
            }


        }
    }

    private static void sendMessageToDevice(String serverURL){
        try{
            System.out.println("Connecting to " + serverURL);

            ClientSession clientSession = (ClientSession) Connector.open(serverURL);
            HeaderSet hsConnectReply = clientSession.connect(null);
            if (hsConnectReply.getResponseCode() != ResponseCodes.OBEX_HTTP_OK) {
                System.out.println("Failed to connect");
                return;
            }


            HeaderSet hsOperation = clientSession.createHeaderSet();
            hsOperation.setHeader(HeaderSet.NAME, "Hello.txt");
            hsOperation.setHeader(HeaderSet.TYPE, "text");

            //Create PUT Operation
            Operation putOperation = clientSession.put(hsOperation);

            // Send some text to server
            byte[] data = "Hello World !!!".getBytes(StandardCharsets.UTF_8);
            OutputStream os = putOperation.openOutputStream();
            os.write(data);
            os.close();

            putOperation.close();

            clientSession.disconnect(null);

            clientSession.close();

            System.out.println("Sent the message");
        }
        catch (Exception e) {
            System.err.println("failed");
            e.printStackTrace();
        }
    }

}