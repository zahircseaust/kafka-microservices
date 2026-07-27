@echo off
REM Setup script for local PostgreSQL databases (Windows)
REM Prerequisites: PostgreSQL installed locally with psql in PATH
REM Usage: setup-local-db.bat [postgres_user] [postgres_password]

SET PGUSER=%1
IF "%PGUSER%"=="" SET PGUSER=postgres

SET PGPASSWORD=%2
IF "%PGPASSWORD%"=="" SET PGPASSWORD=postgres

echo Creating databases for Kafka Microservices Demo...
echo Using PostgreSQL user: %PGUSER%

psql -U %PGUSER% -c "SELECT 1 FROM pg_database WHERE datname = 'orderdb'" | findstr /C:"1" >nul
IF ERRORLEVEL 1 (
    echo Creating orderdb...
    psql -U %PGUSER% -c "CREATE DATABASE orderdb;"
) ELSE (
    echo orderdb already exists, skipping...
)

psql -U %PGUSER% -c "SELECT 1 FROM pg_database WHERE datname = 'inventorydb'" | findstr /C:"1" >nul
IF ERRORLEVEL 1 (
    echo Creating inventorydb...
    psql -U %PGUSER% -c "CREATE DATABASE inventorydb;"
) ELSE (
    echo inventorydb already exists, skipping...
)

psql -U %PGUSER% -c "SELECT 1 FROM pg_database WHERE datname = 'notificationdb'" | findstr /C:"1" >nul
IF ERRORLEVEL 1 (
    echo Creating notificationdb...
    psql -U %PGUSER% -c "CREATE DATABASE notificationdb;"
) ELSE (
    echo notificationdb already exists, skipping...
)

psql -U %PGUSER% -c "SELECT 1 FROM pg_database WHERE datname = 'authdb'" | findstr /C:"1" >nul
IF ERRORLEVEL 1 (
    echo Creating authdb...
    psql -U %PGUSER% -c "CREATE DATABASE authdb;"
) ELSE (
    echo authdb already exists, skipping...
)

echo.
echo Done! Databases created:
psql -U %PGUSER% -c "SELECT datname FROM pg_database WHERE datname IN ('orderdb', 'inventorydb', 'notificationdb', 'authdb');"
