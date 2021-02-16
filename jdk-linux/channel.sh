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
# Run OpenJDK/JNI example DatagramChannel client (Swing GUI).

function config {
	# make sure this matches the POM
	mainclass=de.telekom.llcto.ecn_bits.jdk.jni.ChannelMain
}
function frobenv {
	# extra environment setup, only export commands allowed, for example:
	export LD_LIBRARY_PATH=$top/target/native${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}
}

# this must, indeed, use a here document
jarinfo11() { java --source 11 /dev/stdin "$@" <<\EOF
	class JEP330Extractor {
		public static void main(String[] args) throws Exception {
			System.out.println(new java.util.jar.JarFile(args[1])
			    .getManifest().getMainAttributes()
			    .getValue(args[0]));
			System.exit(0);
		}
	}
EOF
}

function makecmdline {
	local mainclass
	config
	typeset -ft frobenv
	# define local variables first
	local top exe cp x m2repo=~/.m2/repository
	set +U

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
	# determine Maven repository path
	[[ -n $M2_REPO && -d $M2_REPO/. ]] && m2repo=$M2_REPO

	# figure out whether Maven resource filtering has gifted us with info
	cp=<<'	end-of-cp'
${runtime.classpath}
	end-of-cp
	exe=<<'	end-of-exe'
${runtime.jarname}
	end-of-exe

	# determine executable, either from above or by finding marker file
	exe=${exe%%*($'\n'|$'\r')}
	[[ $exe = [!\$]* ]] || exe=
	[[ -z $exe ]] || if [[ -s $top/$exe ]]; then
		exe=$top/$exe
	else
		print -ru2 -- "[WARNING] $exe not found, looking around..."
		exe=
	fi
	[[ -n $exe ]] || for x in "$top"/target/*.cp; do
		if [[ -n $exe ]]; then
			print -ru2 -- '[ERROR] Found more than one JAR to run.'
			exit 255
		fi
		[[ -s $x ]] || break
		exe=${x%.cp}.jar
	done
	if [[ -z $exe ]]; then
		print -ru2 -- '[ERROR] Found no JAR to run.'
		exit 255
	fi
	if [[ ! -s $exe ]]; then
		print -ru2 -- "[ERROR] $exe not found."
		exit 255
	fi

	# determine JAR classpath, either from above or a .cp file or JAR manifest
	set -U
	if [[ $cp = '$'* && -s ${exe%.jar}.cp ]]; then
		cp=$(<"${exe%.jar}.cp")
		# use only if content is present
		[[ $cp = 'classpath='* ]] || cp='$'
	fi
	if [[ $cp = '$'* ]]; then
		if java --source 11 /dev/stdin \
		    <<<'class x { public static void main(String[] args) { System.exit(0); } }' \
		    >/dev/null 2>&1; then
			cp=$(jarinfo11 x-tartools-cp "$exe") || cp=
		elif ! whence jjs >/dev/null 2>&1; then
			print -ru2 -- '[ERROR] jjs (from JRE) not installed.'
			exit 255
		elif ! cp=$(jjs -scripting - -- \
		    <<<'echo(new java.util.jar.JarFile($ARG[1]).getManifest().getMainAttributes().getValue($ARG[0]));' \
		    x-tartools-cp "$exe" 2>/dev/null) || \
		    [[ $cp = *'jjs>'* ]]; then
			print -ru2 -- '[ERROR] Neither JEP 330 nor jjs work.'
			exit 255
		fi
		if [[ $cp != $'\u0086classpath='* ]]; then
			print -ru2 -- '[ERROR] Could not retrieve classpath' \
			    "from $exe manifest."
			exit 255
		fi
	fi
	cp=${cp#?($'\u0086')classpath=}
	cp=${cp%%*($'\n'|$'\r'|$'\u0087')}
	cp=${cp//$'\u0095'/"/"}
	cp=${cp//$'\u009C'/":"}
	cp=${cp//$'\u0096'M2REPO$'\u0097'/"$m2repo"}
	set +U
	# determine run CLASSPATH
	cp=$exe${cp:+:$cp}${CLASSPATH:+:$CLASSPATH}
	# put together command line
	set -A _ java -cp "$cp" "$mainclass" "$@"
	# additional environment setup
	frobenv
}
makecmdline "$0" "$@"
set -x
exec "${_[@]}"
