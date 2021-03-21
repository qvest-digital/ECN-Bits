using System;
using System.Net.Sockets;

namespace ECNBits.DotNet.Experiment {

public class Exp {
	public static void Main(string[] args) {
		var se = API.Do();
		Console.WriteLine((int)se.SocketErrorCode);
		Console.WriteLine(se.ErrorCode);
		Console.WriteLine(se.NativeErrorCode);
		Console.WriteLine(se);
	}
}

}
