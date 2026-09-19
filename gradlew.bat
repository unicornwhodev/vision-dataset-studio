@echo off
rem Checksummed bootstrap. Requires Python 3.11+; no official wrapper JAR is bundled.
python "%~dp0tools\gradle_bootstrap.py" %*
