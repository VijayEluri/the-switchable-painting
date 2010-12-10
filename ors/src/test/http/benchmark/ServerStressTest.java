package http.benchmark;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class ServerStressTest {

	public static void main(String[] args) 
	{
		ServerStressTest test = new ServerStressTest();
		test.concurrentUsers(100);
	}
	
	private void concurrentUsers(int users) 
	{
		for (int i = 0; i < users; i++) {
			new Thread(new User()).start();
		}
	}
	
	class User implements Runnable
	{
		@Override
		public void run() {
			URL url = null;
			try {
				url = new URL("http://localhost:4444");
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
			long start   = System.currentTimeMillis();
			long finish  = start;
			int requests = 0;
			while(finish - start < 1000) {
				try {
					((HttpURLConnection) url.openConnection()).getContent();
				} catch (IOException e) {
					e.printStackTrace();
				}
				finish = System.currentTimeMillis();
				requests++;
			}
			start = finish;
			System.out.println("User done. Requests per second: " + requests);
		}
	}
}