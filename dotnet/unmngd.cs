using System.Net.Sockets;
using System.Runtime.InteropServices;

namespace ECNBits.DotNet.Experiment {

public class API {
	#region wrappers
	public static int Do() {
		int rv;

		rv = Unmanaged.ecnbits_example_ndo();
		if (rv >= 2) {
			// this will retrieve the last error code
			throw MonoSocketException.NewSocketException();
		}
		return rv;
	}
	#endregion
}

internal class Unmanaged {
	#region windllpath
	[DllImport("kernel32", CharSet=CharSet.Unicode, SetLastError=true)]
	private static extern bool SetDllDirectory(string path);

	static Unmanaged() {
		if (!RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
			return;
		string arch;
		string mydir = System.IO.Path.GetDirectoryName(System.Reflection.Assembly.GetEntryAssembly().Location);
		switch (RuntimeInformation.ProcessArchitecture) {
		case Architecture.X86:
			arch = "i386";
			break;
		case Architecture.X64:
			arch = "amd64";
			break;
		case Architecture.Arm:
			arch = "armhf";
			break;
		case Architecture.Arm64:
			arch = "arm64";
			break;
		default:
			System.Console.Write("ECNBits: unknown process architecture: ");
			System.Console.WriteLine(RuntimeInformation.ProcessArchitecture);
			return;
		}
		if (!SetDllDirectory(System.IO.Path.Combine(mydir, arch)))
			throw new System.ComponentModel.Win32Exception();
	}
	#endregion

	#region native
	internal const string LIB = "cLib";

	[DllImport(LIB, CallingConvention=CallingConvention.Cdecl, SetLastError=true)]
	internal static extern int ecnbits_example_ndo();
	#endregion
}

}
