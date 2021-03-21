using System;
using System.Net.Sockets;

public class Exp {
	public static void Main(string[] args) {
		var se = new SocketException((int) SocketError.ConnectionRefused);
		Console.WriteLine((int)se.SocketErrorCode);
		Console.WriteLine(se.ErrorCode);
		Console.WriteLine(se.NativeErrorCode);
	}
}
