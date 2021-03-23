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
	#region native
	internal const string LIB = "cLib";

	[DllImport(LIB, CallingConvention=CallingConvention.Cdecl, SetLastError=true)]
	internal static extern int ecnbits_example_ndo();

	#endregion
}

}
