using System;
using System.Net.Sockets;
// for RuntimeInformation
using System.Runtime.InteropServices;

namespace ECNBits.DotNet.Experiment {

public class Exp {
	public static void Main(string[] args) {
		Console.Write("Is64Bit? ");
		Console.WriteLine(Environment.Is64BitProcess);
		Console.Write("Platform ");
		Console.WriteLine(RuntimeInformation.IsOSPlatform(OSPlatform.Windows) ?
		    "Windows" : RuntimeInformation.IsOSPlatform(OSPlatform.Linux) ?
		    "GNU/Linux" : "other");
		Console.Write("Arch:    ");
		Console.WriteLine(RuntimeInformation.ProcessArchitecture);
		try {
			var i = API.Do();
			Console.Write("Success: ");
			Console.WriteLine(i);
		} catch (SocketException se) {
			Console.Write("SocketEC ");
			Console.WriteLine((int)se.SocketErrorCode);
			Console.Write("      EC ");
			Console.WriteLine(se.ErrorCode);
			Console.Write("NativeEC ");
			Console.WriteLine(se.NativeErrorCode);
			Console.Write("HResult: ");
			Console.WriteLine(se.HResult);
			Console.Write("Exception: ");
			Console.WriteLine(se);
		}
	}
}

}
