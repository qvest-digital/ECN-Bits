/*-
 * Copyright © 2021
 *	mirabilos <t.glaser@tarent.de>
 * Licensor: Deutsche Telekom
 *
 * Provided that these terms and disclaimer and all copyright notices
 * are retained or reproduced in an accompanying document, permission
 * is granted to deal in this work without restriction, including un‐
 * limited rights to use, publicly perform, distribute, sell, modify,
 * merge, give away, or sublicence.
 *
 * This work is provided “AS IS” and WITHOUT WARRANTY of any kind, to
 * the utmost extent permitted by applicable law, neither express nor
 * implied; without malicious intent or gross negligence. In no event
 * may a licensor, author or contributor be held liable for indirect,
 * direct, other damage, loss, or other issues arising in any way out
 * of dealing in the work, even if advised of the possibility of such
 * damage or existence of a defect, except proven that it results out
 * of said person’s immediate fault when using the work as intended.
 */

using ECNBits;
using System;
using System.Net;
using System.Net.Sockets;
using System.Text;

public static class ECNBitsClient {

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
			Console.Write("Trying [{0}]:{1}...", addr, port);
			if (Do(addr, port))
				rv = 0;
		} catch (Exception e) {
			Console.WriteLine("error: " + e);
		}
	}
	Environment.Exit(rv);
}

private static bool Do(IPAddress addr, Int32 port) {
	bool ok = false;
	using (var client = new UdpClient(addr.AddressFamily)) {
		Nullable<Byte> tos;
		IPEndPoint ep = null;
		client.ECNBitsPrepare();
		var sock = client.Client;

		client.Connect(addr, port);
		Console.WriteLine(" connected");
		Byte[] sendbuf = utf8enc.GetBytes("hi!");
		client.Send(sendbuf, sendbuf.Length);
		while (sock.Poll(1000000, SelectMode.SelectRead)) {
			ok = true;
			Byte[] recvbuf = client.Receive(ref ep, out tos);
			var ts = DateTime.UtcNow.ToString("yyyy'-'MM'-'dd'T'HH':'mm':'ss'Z'",
			    System.Globalization.CultureInfo.InvariantCulture);
			var msg = utf8enc.GetString(recvbuf).TrimEnd('\r', '\n');

			StringBuilder sb = new StringBuilder(ts);
			sb.Append(' ');
			sb.Append(ECNUtil.Desc(tos));
			if (tos == null)
				sb.Append("{??}");
			else
				sb.AppendFormat("{0}{1:X2}{2}", '{', tos, '}');
			sb.AppendFormat(" <{0}>", msg);
			//Console.WriteLine("received message from " + ep);
			Console.WriteLine(sb);
		}
	}
	return ok;
}

}
