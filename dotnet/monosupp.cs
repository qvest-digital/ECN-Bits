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
 *-
 * Workaround for https://github.com/mono/mono/issues/20958 (Mono on !Windows)
 */

using System;
using System.Globalization;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text;

namespace ECNBits {

/*
 * SocketException except, when run on Mono on not-Windows®,
 * we must use a subclass to pass the correct error code on…
 */
public class MonoSocketException : SocketException {
	#region factory
	/*
	 * Use this factory instead of a constructor
	 *
	 * Calling “MonoSocketException.NewSocketException()”
	 * replaces “new SocketException()” in all semantics.
	 */
	internal static SocketException NewSocketException() {
		if (NoMonoSupportNeeded)
			return new SocketException();
		int errno = Marshal.GetLastWin32Error();
		int winerr = ecnhll_mono_map(errno);
		return new MonoSocketException(errno, winerr);
	}
	#endregion

	#region implementation
	/*
	 * Internals, implementing the SocketException subclass
	 */

	// use the factory method instead
	private MonoSocketException(int eno, int errorCode) : base(errorCode) {
		ErrnoValue = eno;
	}

	private int ErrnoValue;

	// this is unfortunately the only one overridable
	public override int ErrorCode => ErrnoValue;

	// we w̲o̲u̲l̲d̲ like to set NativeErrorCode as well,
	// keeping the mapped-to-Windows code in only
	// SocketErrorCode, but .net is like Turbo Pascal ☹
	// cf. https://stackoverflow.com/a/27500564/2171120
	#endregion

	#region overridden ToString implementation
	// from Mono’s external/corefx/src/Microsoft.Win32.Primitives/src/System/ComponentModel/Win32Exception.cs
	private const int E_FAIL = unchecked((int)0x80004005);

	/*
	 * Slightly patched version of SocketException.ToString()
	 */
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

	/*
	 * Once-only check whether this is even needed
	 *
	 * Initialises NoMonoSupportNeeded, true if Windows® or not Mono
	 * or running under a Mono with bug#20958 fixed…
	 */
	static MonoSocketException() {
		ecnhll_mono_test();
		var se = new SocketException();
		NoMonoSupportNeeded = se.SocketErrorCode ==
		    SocketError.AddressFamilyNotSupported;
	}
	#endregion

	#region native
	/*
	 * Mapping native (unmanaged) code as used above;
	 * see unmngd.cs for the actual ECNBits prod code
	 */

	[DllImport(Unmanaged.LIB, ExactSpelling=true, CallingConvention=CallingConvention.Cdecl, SetLastError=true)]
	internal static extern void ecnhll_mono_test();

	[DllImport(Unmanaged.LIB, ExactSpelling=true, CallingConvention=CallingConvention.Cdecl)]
	internal static extern int ecnhll_mono_map(int errno);
	#endregion
}

}
