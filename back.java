/**
 * A simple example program to use DataMonitor to start and
 * stop executables based on a znode. The program watches the
 * specified znode and saves the data that corresponds to the
 * znode in the filesystem. It also starts the specified program
 * with the specified arguments when the znode exists and kills
 * the program if the znode goes away.
 */
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Scanner;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.CreateMode;

public class Executor implements Watcher, Runnable, DataMonitor.DataMonitorListener {
	String znode, mensagem, filename, exec[];
	static DataMonitor dm;
	Process child;
	static ZooKeeper zk;
	static Executor executor = null;

	public Executor(String hostPort, String znode, String filename, String exec[]) throws KeeperException, IOException, InterruptedException {
		this.filename = filename;
		this.exec = exec;
		zk = new ZooKeeper(hostPort, 3000, this);
	}

	public static void main(String[] args) throws KeeperException, IOException, InterruptedException {
		if (args.length < 4) {
			System.err.println("USAGE: Executor hostPort znode filename program [args ...]");
			System.exit(2);
		}

		String hostPort = args[0];
		String pathvariable = "/chat";
		String filename = args[2];
		String exec[] = new String[args.length - 3];
		String sentence = "";
		
		Executor executor = new Executor(hostPort, pathvariable, filename, exec);
		BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
	
		byte[] data = "data".getBytes();
		
		try {			
			executor.create(pathvariable, data);
		} catch (Exception e) {
			System.out.println("nada para ver por aqui");
		}
		
		String nome = inFromUser.readLine();
		executor.produce(nome);
		
		String pathName = executor.getLeader(pathvariable, executor);

		while (!sentence.equals("quit")) {
			if (zk.exists("/barrier", false) != null) {				
				System.out.println("Esperando alguém entrar para conversar com você...");
			}
			if (zk.exists("/barrier", false) == null) {
				System.out.print("chat> ");
				sentence = inFromUser.readLine();
				pathName = executor.getLeader(pathvariable, executor);
				if(sentence.equals("get")) {
					System.out.print("chat get> ");
					executor.getData(pathName);
				} else if (!sentence.equals("quit")) {
					data = sentence.getBytes();
					executor.update(pathName, data);
				}
			}
		}
	}
	
	boolean produce(String nome) throws KeeperException, InterruptedException {
		ByteBuffer b = ByteBuffer.allocate(4);
		byte[] value;
		value = nome.getBytes();
		zk.create("/element", value, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);
		return true;
	}
	
	
	public String getLeader(String pathvariable, Executor executor) throws KeeperException, InterruptedException, IOException {
		String[] args2 = {pathvariable};
		List<String> list = zk.getChildren(pathvariable, false);
		String mainNode = "";
		String pathName = pathvariable;
		
		if (list.size() == 0) {
			LeaderElector leaderElector = new LeaderElector(pathvariable, zk);
			pathName = leaderElector.leaderElection(args2);
			this.create("/barrier", "data".getBytes());

		} else {
			if (zk.exists("/barrier", false) != null) {				
				zk.delete("/barrier", -1);
			}
			mainNode = list.get(0);
			pathName = "/chat/"+mainNode;
		}
		
		return pathName;
	}

	public void process(WatchedEvent event) {
		//dm.process(event);
	}

	public void create(String path, byte[] data) throws KeeperException, InterruptedException {
		this.zk.create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
	}
	
	public void getData(String path) throws KeeperException, InterruptedException {
		byte[] bn = this.zk.getData(path, false, null);
		String data = new String(bn);
		System.out.println(data);
	}

	public void update(String path, byte[] data) throws KeeperException, InterruptedException {
		this.zk.setData(path, data, this.zk.exists(path, true).getVersion());
	}

	public void run() {
//		try {
//			synchronized (this) {
//				while (!dm.dead) {
//					wait();
//				}
//			}
//		} catch (InterruptedException e) {
//		}
	}

	public void closing(int rc) {
		synchronized (this) {
			notifyAll();
		}
	}

	static class StreamWriter extends Thread {
		OutputStream os;
		InputStream is;

		StreamWriter(InputStream is, OutputStream os) {
			this.is = is;
			this.os = os;
			start();
		}

		public void run() {
			byte b[] = new byte[80];
			int rc;
			try {
				while ((rc = is.read(b)) > 0) {
					os.write(b, 0, rc);
				}
			} catch (IOException e) {
			}

		}
	}

	public void exists(byte[] data) {
		if (data == null) {
			if (child != null) {
				
				child.destroy();
				try {
					child.waitFor();
				} catch (InterruptedException e) {
				}
			}
			child = null;
		} else {
			if (child != null) {
				
				child.destroy();
				try {
					child.waitFor();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			try {
				FileOutputStream fos = new FileOutputStream(filename);
				fos.write(data);
				fos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				
				child = Runtime.getRuntime().exec(exec);
				new StreamWriter(child.getInputStream(), System.out);
				new StreamWriter(child.getErrorStream(), System.err);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
