using System;
using System.Net;
using System.Net.Sockets;
using System.Text;

public class ECNBitsClient {

private static Encoding utf8enc;

public static void Main(string[] args) {
	if (args.Length != 2) {
		Console.WriteLine("usage: client host port");
		Environment.Exit(2);
	}

	utf8enc = new UTF8Encoding(false, false);

	Int32 port = Int32.Parse(args[1]);
	int rv = 1;

	foreach (IPAddress addr in Dns.GetHostAddresses(args[0])) {
		try {
			Console.WriteLine("trying: " + addr);
			if (Do(addr, port))
				rv = 0;
		} catch (Exception e) {
			Console.WriteLine("error trying " + addr + ": " + e);
		}
	}
	Environment.Exit(rv);
}

private static bool Do(IPAddress addr, Int32 port) {
	bool ok = false;
	IPEndPoint ep = null;
	var client = new UdpClient(addr.AddressFamily);
	var sock = client.Client;

	client.Connect(addr, port);
	Byte[] sendbuf = utf8enc.GetBytes("hi!");
	client.Send(sendbuf, sendbuf.Length);
	while (sock.Poll(1000000, SelectMode.SelectRead)) {
		ok = true;
		Byte[] recvbuf = client.Receive(ref ep);
		Console.WriteLine("received message from " + ep);
		Console.WriteLine("content: " + utf8enc.GetString(recvbuf));
	}
	return ok;
}

}
