@ECHO OFF

SET WD=%CD%
SET HD=%~dp0
SET PARAMS=%*

CALL java -jar "%HD%target/nokia-3310-contact-converter.jar" %PARAMS%
