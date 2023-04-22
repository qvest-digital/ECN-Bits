/^$/ {
	s = 0
}

/^Runtime Environment:$/ {
	s = 1
}

/^\.NET runtimes installed:$/ {
	s = 2
}

/^ Base Path:  *\/.*\/$/ {
	if (s == 1) {
		p = $0;
		gsub("^ Base Path:  *", "", p);
		gsub("'", "'\\''", p);
		gsub("^|$", "'", p);
		print 2147483647, 2147483647, 2147483647, p;
	}
}

/^  Microsoft\.NETCore\.App [0-9.]* \[\/.*]$/ {
	if (s == 2) {
		p = $0;
		gsub("^[^[]*\\[|]$", "", p);
		gsub("'", "'\\''", p);
		gsub("^|$", "'", p);
		gsub("\\.", " ", $2);
		print $2, p;
	}
}
