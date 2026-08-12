Run as administrator (some checks like gpresult and service queries need elevation). It generates two files next to itself:

IE_Diag_<computer>_<timestamp>.txt — the main log
gpresult_<computer>.html — full Group Policy report you can open in a browser

What it checks (matches the earlier troubleshooting steps):

#	Check	What to look for
1	IE/WinINET version	Confirms which IE build is installed
2–3	WMI service + repository	The F12 network agent depends on WMI — a broken repository is a common root cause
4–5	Group Policy on IE/Dev Tools	Look for any key that mentions "Developer Tools" or disables scripting/diagnostics
6	Core Windows services	Confirms WMI, event log, network stack services are running
7–8	Event Viewer errors	Often shows the real underlying error the generic message hides
9	Running IE helper processes	Reproduce the error in IE while this runs — if ieinstal.exe/iediagcmd.exe never appears, something's killing it instantly
10	Registered AV product	Tells you which security software to check/whitelist against
11	WinINET/proxy settings	Corrupted proxy config can block the agent
12	Temp folder permissions	The agent may fail silently if it can't write to temp/cache folders

Run it on both the broken machine and a working one, then diff the two logs — the WMI repository check (#3) and the AV/GPO sections (#4, #10) are usually where the difference shows up. Send me the log if you want help interpreting it.