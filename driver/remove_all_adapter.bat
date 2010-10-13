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

echo Start to remove all Virtual Ethernet Adapter.
%~dp0%engine%\tapinstall.exe remove %driverName%

pause