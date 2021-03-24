/*-
 * Copyright (c) .NET Foundation and Contributors
 * Copyright © 2021
 *	mirabilos <t.glaser@tarent.de>
 * Licensor: Deutsche Telekom LLCTO
 *
 * All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

using System;
using System.Globalization;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text;

namespace ECNBits.DotNet.Experiment {

public class MonoSocketException : SocketException {
	#region factory
	internal static SocketException NewSocketException() {
		if (NoMonoSupportNeeded)
			return new SocketException();
		int errno = Marshal.GetLastWin32Error();
		int winerr = monosupp_errnomap(errno);
		return new MonoSocketException(errno, winerr);
	}
	#endregion

	#region implementation
	// use the factory method instead
	private MonoSocketException(int eno, int errorCode) : base(errorCode) {
		ErrnoValue = eno;
	}

	private int ErrnoValue;

	// this is unfortunately the only one overridable
	public override int ErrorCode => ErrnoValue;
	#endregion

	#region overridden_impl
	// from Mono’s external/corefx/src/Microsoft.Win32.Primitives/src/System/ComponentModel/Win32Exception.cs
	private const int E_FAIL = unchecked((int)0x80004005);

	public override string ToString() {
		string message = Message;
		string className = GetType().ToString();
		StringBuilder s = new StringBuilder(className);
		string nativeErrorString = ErrnoValue < 0 ?
		    string.Format(CultureInfo.InvariantCulture, "0x{0:X8}", ErrnoValue) :
		    ErrnoValue.ToString(CultureInfo.InvariantCulture);
		if (HResult == E_FAIL)
			s.AppendFormat(CultureInfo.InvariantCulture, " ({0})", nativeErrorString);
		else
			s.AppendFormat(CultureInfo.InvariantCulture, " ({0:X8}, {1})", HResult, nativeErrorString);
		if (!(String.IsNullOrEmpty(message))) {
			s.Append(": ");
			s.Append(message);
		}

		Exception innerException = InnerException;
		if (innerException != null) {
			s.Append(" ---> ");
			s.Append(innerException.ToString());
		}

		string stackTrace = StackTrace;
		if (stackTrace != null) {
			s.AppendLine();
			s.Append(stackTrace);
		}

		return s.ToString();
	}
	#endregion

	#region MonoSupportNeeded
	internal static readonly bool NoMonoSupportNeeded;

	static MonoSocketException() {
		monosupp_errtest();
		var se = new SocketException();
		NoMonoSupportNeeded = se.SocketErrorCode ==
		    SocketError.AddressFamilyNotSupported;
	}
	#endregion

	#region native
	[DllImport(Unmanaged.LIB, CallingConvention=CallingConvention.Cdecl, SetLastError=true)]
	internal static extern void monosupp_errtest();

	[DllImport(Unmanaged.LIB, CallingConvention=CallingConvention.Cdecl)]
	internal static extern int monosupp_errnomap(int errno);
	#endregion
}

}
