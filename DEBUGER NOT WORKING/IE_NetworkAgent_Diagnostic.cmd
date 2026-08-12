@echo off
:: ============================================================
:: IE F12 Network Collection Agent - Diagnostic Script
:: Run this as Administrator (right-click > Run as administrator)
:: Generates a timestamped log file in the same folder.
:: ============================================================

setlocal enabledelayedexpansion

set LOGFILE=%~dp0IE_Diag_%COMPUTERNAME%_%date:~-4,4%%date:~-10,2%%date:~-7,2%_%time:~0,2%%time:~3,2%.txt
set LOGFILE=%LOGFILE: =0%

echo ============================================================ > "%LOGFILE%"
echo IE Network Collection Agent Diagnostic >> "%LOGFILE%"
echo Machine: %COMPUTERNAME%   User: %USERNAME% >> "%LOGFILE%"
echo Date: %date% %time% >> "%LOGFILE%"
echo ============================================================ >> "%LOGFILE%"

:: Check for admin rights
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [WARNING] Not running as Administrator. Some checks may fail or be incomplete. >> "%LOGFILE%"
    echo [WARNING] Not running as Administrator. Some checks may fail or be incomplete.
)

echo. >> "%LOGFILE%"
echo ---- [1] Internet Explorer / WinINET Version Info ---- >> "%LOGFILE%"
reg query "HKLM\SOFTWARE\Microsoft\Internet Explorer" /v svcVersion >> "%LOGFILE%" 2>&1
reg query "HKLM\SOFTWARE\Microsoft\Internet Explorer" /v Version >> "%LOGFILE%" 2>&1

echo. >> "%LOGFILE%"
echo ---- [2] Windows Management Instrumentation (WMI) Service Status ---- >> "%LOGFILE%"
sc query winmgmt >> "%LOGFILE%" 2>&1

echo. >> "%LOGFILE%"
echo ---- [3] WMI Repository Consistency Check ---- >> "%LOGFILE%"
winmgmt /verifyrepository >> "%LOGFILE%" 2>&1

echo. >> "%LOGFILE%"
echo ---- [4] Group Policy Restrictions on IE Dev Tools / F12 ---- >> "%LOGFILE%"
reg query "HKLM\SOFTWARE\Policies\Microsoft\Internet Explorer" /s >> "%LOGFILE%" 2>&1
reg query "HKCU\SOFTWARE\Policies\Microsoft\Internet Explorer" /s >> "%LOGFILE%" 2>&1
echo (If both of the above return "ERROR: The system was unable to find the specified registry key or value.", no relevant GPO is applied - that is normal/expected on unmanaged machines) >> "%LOGFILE%"

echo. >> "%LOGFILE%"
echo ---- [5] Full Group Policy Result Report (HTML) ---- >> "%LOGFILE%"
gpresult /h "%~dp0gpresult_%COMPUTERNAME%.html" >> "%LOGFILE%" 2>&1
echo Full GPO report saved to gpresult_%COMPUTERNAME%.html >> "%LOGFILE%"

echo. >> "%LOGFILE%"
echo ---- [6] Relevant Windows Services Status ---- >> "%LOGFILE%"
for %%S in (winmgmt EventLog Dnscache LanmanWorkstation BFE mpssvc) do (
    echo -- Service: %%S -- >> "%LOGFILE%"
    sc query %%S >> "%LOGFILE%" 2>&1
)

echo. >> "%LOGFILE%"
echo ---- [7] Recent Application Event Log Errors (last 20, IE-related) ---- >> "%LOGFILE%"
wevtutil qe Application /q:"*[System[Provider[@Name='Internet Explorer'] and (Level=1 or Level=2 or Level=3)]]" /c:20 /rd:true /f:text >> "%LOGFILE%" 2>&1

echo. >> "%LOGFILE%"
echo ---- [8] Recent Application Event Log Errors (last 20, general) ---- >> "%LOGFILE%"
wevtutil qe Application /q:"*[System[(Level=1 or Level=2)]]" /c:20 /rd:true /f:text >> "%LOGFILE%" 2>&1

echo. >> "%LOGFILE%"
echo ---- [9] Currently Running IE-related Processes ---- >> "%LOGFILE%"
tasklist /v | findstr /I "iexplore ieinstal iediagcmd" >> "%LOGFILE%" 2>&1
echo (If this section is empty, no IE helper processes are currently running - try reproducing the error in another window while this runs) >> "%LOGFILE%"

echo. >> "%LOGFILE%"
echo ---- [10] Antivirus / Security Product Registered with Windows Security Center ---- >> "%LOGFILE%"
wmic /namespace:\\root\SecurityCenter2 path AntiVirusProduct get displayName,productState >> "%LOGFILE%" 2>&1

echo. >> "%LOGFILE%"
echo ---- [11] Proxy / WinINET Settings for Current User ---- >> "%LOGFILE%"
reg query "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" >> "%LOGFILE%" 2>&1

echo. >> "%LOGFILE%"
echo ---- [12] Temp Folder Permissions Check (agent may need write access) ---- >> "%LOGFILE%"
icacls "%TEMP%" >> "%LOGFILE%" 2>&1
icacls "%LOCALAPPDATA%\Microsoft\Windows\INetCache" >> "%LOGFILE%" 2>&1

echo. >> "%LOGFILE%"
echo ============================================================ >> "%LOGFILE%"
echo Diagnostic complete. >> "%LOGFILE%"
echo ============================================================ >> "%LOGFILE%"

echo.
echo Diagnostic complete. Log saved to:
echo %LOGFILE%
echo GPO report saved to: gpresult_%COMPUTERNAME%.html
echo.
pause
