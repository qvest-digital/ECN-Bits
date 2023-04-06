#!/usr/bin/env mksh
# -*- mode: sh -*-
#-
# Copyright © 2023
#	mirabilos <t.glaser@tarent.de>
# Licensor: Deutsche Telekom LLCTO
#
# Provided that these terms and disclaimer and all copyright notices
# are retained or reproduced in an accompanying document, permission
# is granted to deal in this work without restriction, including un‐
# limited rights to use, publicly perform, distribute, sell, modify,
# merge, give away, or sublicence.
#
# This work is provided “AS IS” and WITHOUT WARRANTY of any kind, to
# the utmost extent permitted by applicable law, neither express nor
# implied; without malicious intent or gross negligence. In no event
# may a licensor, author or contributor be held liable for indirect,
# direct, other damage, loss, or other issues arising in any way out
# of dealing in the work, even if advised of the possibility of such
# damage or existence of a defect, except proven that it results out
# of said person’s immediate fault when using the work as intended.

export LC_ALL=C DOTNET_CLI_TELEMETRY_OPTOUT=1
unset LANGUAGE
set -eo pipefail

cd "$(dirname "$0")"
basedir=
fwkver=
netcoredir=
s=$(dotnet --info | \
    awk -f mkdncsc.awk | \
    sort -nr | \
    sed -n -e '1{s/^/false /;s/^false 2147483647 2147483647 2147483647 /basedir=/;p;}' \
    -e '2{s/^[0-4] /false /;s/^/false /;s/^false \([0-9]*\) \([0-9]*\) \([0-9]*\) \(.*\)$/fwkver=\1.\2.\3 netcoredir=\4\/\1.\2.\3/p;}')
eval "$s"

cat >dnmkrtcf.ba_ <<EOF
#!/bin/sh
echo '{"runtimeOptions":{"framework":{"name":"Microsoft.NETCore.App","version":"$fwkver"}}}'
EOF

cat >dncsc.ba_ <<EOF
#!/usr/bin/env mksh
basedir=${basedir@Q}
netcoredir=${netcoredir@Q}
EOF
cat >>dncsc.ba_ <<\EOF
cd "$netcoredir"
set -A args
nargs=0
args[nargs++]=-lib:"$netcoredir"
args[nargs++]=-r:netstandard.dll
args[nargs++]=-r:Microsoft.CSharp.dll
args[nargs++]=-r:System.dll
for x in System.*.dll; do
	args[nargs++]=-r:$x
done
args[nargs++]=-r:Microsoft.Win32.Primitives.dll
cd "$OLDPWD"
exec dotnet "$basedir/Roslyn/bincore/csc.dll" "${args[@]}" "$@"
EOF

chmod +x dnmkrtcf.ba_ dncsc.ba_
exit 0
