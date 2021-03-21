using System.Net.Sockets;

namespace ECNBits.DotNet.Experiment {

public class API {
	public static SocketException Do() {
		try {
			Unmanaged.ThrowUp();
			return null;
		} catch (SocketException se) {
			return se;
		}
	}
}

internal class Unmanaged {
	internal static int ThrowUp() {
		throw new SocketException((int) SocketError.ConnectionRefused);
	}
}

}
