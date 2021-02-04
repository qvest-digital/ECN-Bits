#!/usr/bin/env mksh
# -*- mode: sh -*-
#-
# Copyright © 2021
#	mirabilos <t.glaser@tarent.de>
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
#-
# Run OpenJDK/JNI example socket client.

top=$(realpath "$0/..")
exe=
for x in "$top"/target/jni-*-cli.jar; do
	if [[ ! -s $x ]]; then
		print -ru2 '[ERROR] no executable JAR found'
		exit 255
	fi
	if [[ -n $exe ]]; then
		print -ru2 '[ERROR] more than one executable JAR found'
		exit 255
	fi
	exe=$x
done
set -x
LD_LIBRARY_PATH=$top/target/cmake${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH} \
    exec java -jar "$exe" "$@"
