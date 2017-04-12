/*
	This class is used to find out the given port is JMX port or Not.
*/
import javax.management.remote.JMXServiceURL;
import javax.management.remote.JMXConnector;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorFactory;
import java.util.concurrent.*;
import java.net.SocketTimeoutException;
import java.io.IOException;
import java.util.*;
import java.lang.*;
import java.io.*;

public class JmxDiscovery
{
	public boolean connectToJVM(String hostIp, int portNo)
	{
		boolean flag = false;
		String url = hostIp+":"+portNo;
		String jndiName = "jmxrmi";
		String jvmURL="service:jmx:rmi:///jndi/rmi://"+url+"/"+jndiName;
		Map environment = new HashMap();
		environment.put("jmx.remote.protocol.provider.pkgs","com.sun.jmx.remote.protocol");
		//environment.put("jmx.remote.x.request.waiting.timeout", Long.toString(500));
		try
		{
			JMXServiceURL jmxService = new JMXServiceURL(jvmURL);
			//JMXConnector jmxConnector = JMXConnectorFactory.connect(jmxService,environment);
			JMXConnector jmxConnector = connectWithTimeout(jmxService, 1, TimeUnit.SECONDS);
			MBeanServerConnection beanServerConn = jmxConnector.getMBeanServerConnection();
			if(beanServerConn!=null)
			{
				flag = true;
				if(jmxConnector != null) jmxConnector.close();
			}
		}
		catch(SecurityException se)
		{
			//System.out.println("Security Exception :"+se.getMessage());
			flag = true;
		}
		catch(Exception ee)
		{
			//System.out.println("Exception Occured....:"+e.getMessage());
			flag = false;
		}
		return flag;
	}

	public static JMXConnector connectWithTimeout(final JMXServiceURL url, long timeout, TimeUnit unit) throws IOException 
	{
		final BlockingQueue mailbox = new ArrayBlockingQueue(1);
		ExecutorService executor = Executors.newSingleThreadExecutor(daemonThreadFactory);
		executor.submit(new Runnable() {
			public void run() {
			try {
				JMXConnector connector = JMXConnectorFactory.connect(url);
				MBeanServerConnection connection = connector.getMBeanServerConnection();
				ObjectName delegateName = new ObjectName("Catalina:j2eeType=Servlet,*");
				Set localSet = connection.queryMBeans(delegateName, null);
				System.out.println(localSet);
				
				Iterator localIterator = localSet.iterator();
				while (localIterator.hasNext())
				{
					ObjectInstance io = (ObjectInstance)localIterator.next();
					ObjectName objName = io.getObjectName();
					String thisInfo = objName.toString();
					if(thisInfo.contains("WebModule="))
						thisInfo = objName.getKeyProperty("WebModule");
				}
				
				MBeanInfo beanInfo = connection.getMBeanInfo(delegateName);
				MBeanAttributeInfo[] beanAttribute = beanInfo.getAttributes();
				for(int k=0;k<beanAttribute.length;k++)
				{
					
					try
					{
						String attributeName = beanAttribute[k].getName();
						Object attributeValue = connection.getAttribute(delegateName , attributeName);
						if(attributeName.equals("null") || attributeValue.equals("null"))
							continue;
					}
					catch (Exception eg)
					{
						continue;
					}
					//System.out.println("EgTomcatBase attributeName: "+attributeName +" attributeValue: "+attributeValue);
					
				}
				if (!mailbox.offer(connector))
				connector.close();
			} catch (Throwable t) {
				mailbox.offer(t);
			}
			}
		});
		Object result=null;
		try {
			result = mailbox.poll(timeout, unit);
			if (result == null) {
			if (!mailbox.offer(""))
				result = mailbox.take();
			}
		} catch (InterruptedException e) {
			//throw initCause(new InterruptedIOException(e.getMessage()), e);
		} finally {
			executor.shutdown();
		}
		if (result == null)
			throw new SocketTimeoutException("Connect timed out: " + url);
		if (result instanceof JMXConnector)
			return (JMXConnector) result;
		try {
			throw (Throwable) result;
		} catch (IOException e) {
			throw e;
		} catch (RuntimeException e) {
			throw e;
		} catch (Error e) {
			throw e;
		} catch (Throwable e) {
			// In principle this can't happen but we wrap it anyway
			throw new IOException(e.toString());
		}
	}

	/*private static <T extends Throwable> T initCause(T wrapper, Throwable wrapped) {
		wrapper.initCause(wrapped);
		return wrapper;
	}*/

	private static class DaemonThreadFactory implements ThreadFactory 
	{
		public Thread newThread(Runnable r) {
			Thread t = Executors.defaultThreadFactory().newThread(r);
			t.setDaemon(true);
			return t;
		}
	}

	private static final ThreadFactory daemonThreadFactory = new DaemonThreadFactory();

	public static void main(String[] args) 
	{
		JmxDiscovery c = new JmxDiscovery();
		long now= System.currentTimeMillis();
		boolean check = c.connectToJVM("192.168.8.251", 12435);
		long aft = System.currentTimeMillis();
		long timetaken = aft - now;
		System.out.println("check.... :"+check + "    "+timetaken);
	}
}
