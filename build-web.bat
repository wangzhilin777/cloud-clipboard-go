@echo off
powershell -ExecutionPolicy Bypass -File "%~dp0build-web.ps1" %*
