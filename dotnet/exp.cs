using System;
using System.Net.Sockets;
// for RuntimeInformation
using System.Runtime.InteropServices;

namespace ECNBits.DotNet.Experiment {

public class Exp {
	public static void Main(string[] args) {
		Console.WriteLine(Environment.Is64BitProcess);
		Console.WriteLine(RuntimeInformation.IsOSPlatform(OSPlatform.Windows) ?
		    "Windows" : RuntimeInformation.IsOSPlatform(OSPlatform.Linux) ?
		    "GNU/Linux" : "other");
		Console.WriteLine(RuntimeInformation.ProcessArchitecture);
		var se = API.Do();
		Console.WriteLine((int)se.SocketErrorCode);
		Console.WriteLine(se.ErrorCode);
		Console.WriteLine(se.NativeErrorCode);
		Console.WriteLine(se);
	}
}

}
