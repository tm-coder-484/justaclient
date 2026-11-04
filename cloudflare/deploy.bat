@echo off
rem Deploys the Justaclient Worker (feedback and the messaging relay) to your Cloudflare account. Needs Node.js 20+ (https://nodejs.org).
cd /d "%~dp0"
where npx >nul 2>nul || (echo Node.js is not installed. Get it from https://nodejs.org and run this again. & pause & exit /b 1)
echo Signing in to Cloudflare (a browser window opens the first time)...
call npx --yes wrangler@3 login || (pause & exit /b 1)
findstr /c:"REPLACE_WITH_DATABASE_ID" wrangler.toml >nul && (
  echo Creating the D1 database...
  call npx --yes wrangler@3 d1 create justaclient > d1.txt
  type d1.txt
  for /f "tokens=2 delims== " %%i in ('findstr /c:"database_id" d1.txt') do powershell -Command "(Get-Content wrangler.toml) -replace 'REPLACE_WITH_DATABASE_ID', %%i | Set-Content wrangler.toml"
  del d1.txt
)
echo Creating the table...
call npx --yes wrangler@3 d1 execute justaclient --remote --file=schema.sql || (pause & exit /b 1)
echo Deploying...
call npx --yes wrangler@3 deploy || (pause & exit /b 1)
echo.
echo Done. Copy the https://justaclient.<you>.workers.dev address above into
echo Justaclient: Right Shift, Utility, Feedback, Server address.
pause
