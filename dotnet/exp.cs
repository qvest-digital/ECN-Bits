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
		try {
			var i = API.Do();
			Console.WriteLine("ok:");
			Console.WriteLine(i);
		} catch (SocketException se) {
			Console.WriteLine((int)se.SocketErrorCode);
			Console.WriteLine(se.ErrorCode);
			Console.WriteLine(se.NativeErrorCode);
			Console.WriteLine((se is MonoSocketException) ?
			    ((se as MonoSocketException).NativeErrorCode) :
			    -1234567);
			Console.WriteLine(se);
		}
	}
}

}
