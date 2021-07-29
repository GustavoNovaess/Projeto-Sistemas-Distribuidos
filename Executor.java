
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
import java.util.Scanner;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.CreateMode;

public class Executor implements Watcher, Runnable, DataMonitor.DataMonitorListener {
	String znode;

	String mensagem;

	DataMonitor dm;

	ZooKeeper zk;

	String filename;

	String exec[];

	Process child;

	public Executor(String hostPort, String znode, String filename, String exec[]) throws KeeperException, IOException {

		this.filename = filename;
		this.exec = exec;
		// this.mensagem = entrada.nextLine();
		zk = new ZooKeeper(hostPort, 3000, this);
		dm = new DataMonitor(zk, znode, null, this);
	}

	/**
	 * @param args
	 * @throws IOException
	 * @throws KeeperException
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws KeeperException, IOException, InterruptedException {
		Scanner entrada = new Scanner(System.in);
		if (args.length < 4) {
			System.err.println("USAGE: Executor hostPort znode filename program [args ...]");
			System.exit(2);
		}
		String hostPort = args[0];
		String znode = args[1];
		String filename = args[2];
		String exec[] = new String[args.length - 3];
		String path, pathvariable;
		System.arraycopy(args, 3, exec, 0, exec.length);
		
		Executor executor = new Executor(hostPort, znode, filename, exec);

		BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
		String sentence = "";

		sentence = inFromUser.readLine();
		pathvariable = "/" + sentence;
		byte[] data = "data".getBytes();

		try {
			executor.create(pathvariable, data);
		} catch (Exception e) {
			System.out.println("\nEntrando no nó\n");
		}

		System.out.println("Chat:\n");

		while (!sentence.equals("quit")) {
			sentence = inFromUser.readLine();
			if(sentence.equals("get")) {
				//executor.getData(pathvariable);
				
			} else {
				data = sentence.getBytes();
				executor.update(pathvariable, data);
			}
			
			
		}
		executor.run();
	}

	/***************************************************************************
	 * We do process any events ourselves, we just need to forward them on.
	 *
	 * @see org.apache.zookeeper.Watcher#process(org.apache.zookeeper.proto.WatcherEvent)
	 */
	public void process(WatchedEvent event) {
		dm.process(event);
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
		System.out.println();
		try {
			synchronized (this) {
				while (!dm.dead) {
					wait();
				}
			}
		} catch (InterruptedException e) {
		}
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