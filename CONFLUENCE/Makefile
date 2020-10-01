all:
	@echo run either make pack or make unpack
	@echo pack converts .doc exported from Confluence to repo format
	@echo unpack converts from repo to .doc for Confluence import

# nmake: \
!IF 1 # \
RM=del # \
!ELSE
# make (BSD, POSIX, GNU):
RM=rm -f
# nmake again: \
!ENDIF

pack:
	$(RM) ECN-Library.doc.gz ECN-Library.doc.gz.uu
	gzip -n9 <ECN-Library.doc | \
	    uuencode ECN-Library.doc.gz >ECN-Library.doc.gz.uu
	$(RM) ECN-Library.doc

unpack:
	$(RM) ECN-Library.doc ECN-Library.doc.gz
	uudecode ECN-Library.doc.gz.uu
	gzip -d ECN-Library.doc.gz
	$(RM) ECN-Library.doc.gz.uu
