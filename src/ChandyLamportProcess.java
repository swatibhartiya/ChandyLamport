import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

class Marker implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	int ID;
	boolean seen;
	static int counter = 0;

	public Marker() {
		ID = counter;
		counter++;
	}
}

public class ChandyLamportProcess extends UnicastRemoteObject implements
		CommonInterface, Runnable, Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	int portNo = 6544;
	int ID = 0;
	static int balance = 1000;

	String selfName;
	String selfIP;

	int noOfReceivedMarkers = 0;

	String state = "";

	int incomingChannel;

	boolean recordChannelState = false;
	int channelState = 0;
	HashMap<String, String> processes = new HashMap<String, String>();
	HashMap<Integer, String> processesID = new HashMap<Integer, String>();

	static Marker m;
	static Marker receiveM;

	static int noOfObjects = 0;

	static int channel_12;
	static int channel_13;

	boolean sentToComet = false;

	HashMap<Marker, Integer> seenMarkerObject = new HashMap<Marker, Integer>();

	public ChandyLamportProcess() throws RemoteException, UnknownHostException {
		super();

		processesID.put(0, "comet");
		processesID.put(1, "rhea");
		processesID.put(2, "glados");

		if (InetAddress.getLocalHost().getHostName().equals("glados")) {
			ID = 2;
		} else if (InetAddress.getLocalHost().getHostName().equals("comet")) {
			ID = 0;
		} else if (InetAddress.getLocalHost().getHostName().equals("rhea")) {
			ID = 1;
		}

		processes.put("comet", "129.21.34.80");
		processes.put("rhea", "129.21.37.49");
		processes.put("glados", "129.21.22.196");

		selfName = InetAddress.getLocalHost().getHostName();
		selfIP = InetAddress.getLocalHost().getHostAddress();

		if (noOfObjects == 0) {
			++noOfObjects;

			Registry reg = LocateRegistry.createRegistry(portNo);
			reg.rebind("chandyprocess", this);

			System.out.println("Object bound to the registry");

			/*
			 * for (Map.Entry<Integer, String> entry : processesID.entrySet()) {
			 * if (entry.getValue().equals(selfName)) { ID = entry.getKey();
			 * 
			 * if (ID == 0) { // process 1 needs to initiate the snapshot
			 * System.out.println(selfName +
			 * " is going to create a snapshot thread");
			 * this.createSnapshotThread(); } } }
			 */

			if (ID == 0) {
				System.out.println(selfName
						+ " is going to create a snapshot thread");
				this.createSnapshotThread();
			}
		}
	}

	private void createSnapshotThread() throws RemoteException,
			UnknownHostException {
		ChandyLamportProcess snapshotThread = new ChandyLamportProcess();
		Thread t = new Thread(snapshotThread, "snapshotThread");
		t.start();
	}

	public void send() throws RemoteException, NotBoundException,
			InterruptedException {
		while (true) {
			Thread.sleep(8000);
			Random r = new Random();
			int option = r.nextInt(3 - 0);
			while (option == ID) {
				option = r.nextInt(3 - 0);
				if (option != ID) {
					break;
				}
			}

			int amount = r.nextInt(100 - 0);
			if (balance - amount >= 0) {

				String conn = processesID.get(option);
				String IP = processes.get(conn);
				Registry reg = LocateRegistry.getRegistry(IP, portNo);
				CommonInterface chprocesssObj = (CommonInterface) reg
						.lookup("chandyprocess");

				chprocesssObj.connected(selfName);
				
				balance = balance - amount;
				
				chprocesssObj.transmitMoney(amount);
				if (option == 0) {
					if (ID == 1) {
						channel_12 = amount;
					} else if (ID == 2) {
						channel_13 = amount;
					}
					System.out.println(this.ID + " sending to " + option);
				}
				System.out.println("\n\n" + selfName
						+ " transferred an amount of: " + amount + " to : "
						+ conn);
				System.out.println("\n\n" + selfName + "'s balance = "
						+ balance);

				

			} else {
				System.out.println("\n\n" + "Insufficient balance");
			}
		}
	}

	public static void main(String[] args) throws RemoteException,
			UnknownHostException, NotBoundException, InterruptedException {
		ChandyLamportProcess clp = new ChandyLamportProcess();
		clp.send();
	}

	@Override
	public void connected(String name) throws RemoteException {
		System.out.println("\n\n" + selfName + " got connected to: " + name);
	}

	@Override
	public void receiveMoney(int amount) throws RemoteException {
		if (recordChannelState) {
			channelState = channelState + amount;
		}
		balance = balance + amount;
		System.out.println("\n\n" + selfName + "'s updated balance = "
				+ balance);
	}

	@Override
	public void run() {
		while (true) {
			try {
				Thread.sleep(8000);
				takeSnapshot();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (RemoteException e) {
				e.printStackTrace();
			} catch (NotBoundException e) {
				e.printStackTrace();
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
		}
	}

	private void takeSnapshot() throws RemoteException, NotBoundException, UnknownHostException {
		this.m = new Marker();

		state = "";
		
		state += selfName + " balance: " + balance;
		int ID;
		String hostName = "";
		String connectToIP = "";
		int channelValue;
		String channelState = "";
		for (Map.Entry<Integer, String> entry : processesID.entrySet()) {
			ID = entry.getKey();
			if (ID != this.ID) {
				hostName = processesID.get(ID);
				connectToIP = processes.get(hostName);
				Registry r = LocateRegistry.getRegistry(connectToIP, portNo);
				CommonInterface processObj = (CommonInterface) r
						.lookup("chandyprocess");
				processObj.assignMarkerObject(this.m);
				state += processObj.sendState(); // sends the current balance
				channelValue = processObj.collectChannelState();
				processObj.sendM(selfIP);
				channelState = "\nChannel state between " + this.selfName
						+ " and " + hostName + " is: " + channelValue;
				state += channelState;
			}
		}
		System.out.println("---------------STATE------------");
		System.out.println(state);
		System.out.println("---------------------------------");
	}

	@Override
	public void sendM(String IP) throws RemoteException, NotBoundException {
		Registry r = LocateRegistry.getRegistry(IP, portNo);
		CommonInterface pObj = (CommonInterface) r.lookup("chandyprocess");

	}

	@Override
	public void assignMarkerObject(Marker mark) throws RemoteException, UnknownHostException {
		if (InetAddress.getLocalHost().getHostName().equals("glados")) {
			receiveM = mark;
		} else {
			m = mark;
		}
	}

	@Override
	public String sendState() throws RemoteException {
		return ("\n" + selfName + " balance = " + balance);
	}

	@Override
	public void getM(Marker mark) throws RemoteException {
		receiveM = mark;
		System.out.println(selfName + " has receiveM :" + receiveM
				+ " with ID = " + receiveM.ID);
	}

	@Override
	public int collectChannelState() throws RemoteException {

		int retValue = 0;
		if (this.ID == 1) {
			retValue = channel_12;
			channel_12 = 0;
		} else if (this.ID == 2) {
			retValue = channel_13;
			channel_13 = 0;
		} else {
			return -1;
		}

		return retValue;
	}

	@Override
	public void transmitMoney(int amount) throws RemoteException,
			InterruptedException {
		incomingChannel = amount;
		Thread.sleep(4000);
		this.receiveMoney(amount);
	}

	@Override
	public int incomingChannelValue() {
		return incomingChannel;
	}
}