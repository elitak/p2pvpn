@echo off
set driverName=tapoas
cls

IF %PROCESSOR_ARCHITECTURE% == AMD64 (
	set engine=win64
	echo Set 64Bit
) ELSE (
	set engine=win32
	echo Set 32Bit

)

echo Start installer.
%~dp0%engine%\tapinstall.exe install %~dp0%engine%\OemWin2k.inf %driverName%

pause