#!/usr/bin/env mksh
# -*- mode: sh -*-
#-
# Copyright © 2021
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
#-
# Run OpenJDK/JNI example socket client.

function makecmdline {
	# configure this to match the POM
	local mainclass=de.telekom.llcto.ecn_bits.jdk.jni.ClientMain

	# define local variables first
	set +U
	local top exe cp m2repo=~/.m2/repository x

	# check mainclass begins with package or class
	if [[ -z $1 || $mainclass != [a-zA-Z]* ]]; then
		print -ru2 -- "[ERROR] This script, ${1@Q}, is misconfigured."
		exit 255
	fi

	# find directory this script is located in
	if ! top=$(realpath "$1/.."); then
		print -ru2 -- '[ERROR] Could not determine top-level directory.'
		exit 255
	fi
	shift
	# determine executable by finding classpath metadata
	exe=
	for x in "$top"/target/*-classpath.jar; do
		if [[ -n $exe ]]; then
			print -ru2 -- '[ERROR] Found more than one JAR to run.'
			exit 255
		fi
		[[ -f $x ]] || break
		exe=$x
	done
	if [[ -z $exe ]]; then
		print -ru2 -- '[ERROR] Found no JAR to run.'
		exit 255
	fi
	# determine Maven repository path
	[[ -n $M2_REPO && -d $M2_REPO/. ]] && m2repo=$M2_REPO
	# determine JAR classpath
	if ! cp=$(<"$exe"); then
		print -ru2 -- '[ERROR] Could not read classpath metadata.'
		exit 255
	fi
	cp=${cp//'${M2_REPO}'/$m2repo}
	# determine JAR to run
	exe=${exe%-classpath.jar}.jar
	if [[ ! -s $exe ]]; then
		print -ru2 -- "[ERROR] $exe not found."
		exit 255
	fi
	# determine run CLASSPATH
	cp=$exe${cp:+:$cp}${CLASSPATH:+:$CLASSPATH}
	# put together command line
	set -x -A _ java -cp "$cp" "$mainclass" "$@"
	# additional environment setup
	export LD_LIBRARY_PATH=$top/target/native${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}
}
makecmdline "$0" "$@"
set -x
exec "${_[@]}"
