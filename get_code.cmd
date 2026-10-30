@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "OUT=all_code_context.txt"
if not "%~1"=="" set "OUT=%~1"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$out='%OUT%'; $root=Get-Location; " ^
  "$files = Get-ChildItem -Recurse -File | Where-Object { " ^
  "  $_.FullName -notmatch '\\(target|\.git|\.idea|redis-cluster|node_modules|\.mvn)\\' -and " ^
  "  ($_.Name -match '\.(java|xml|yml|yaml)$' -or $_.Name -in @('Dockerfile','pom.xml','docker-compose.yml')) -and " ^
  "  $_.Name -notmatch 'application-(test|prod)\.yml' -and $_.Name -notlike '*.bak' -and " ^
  "  $_.Name -notin @('MessageConstants.java','CodeGenerator.java','GeoIpUtil.java','UserAgentUtil.java','IpUtil.java','AdminLinkQueryDTO.java','AdminUserQueryDTO.java') -and " ^
  "  $_.FullName -notmatch 'Test\.java$|\\src\\test\\' " ^
  "}; " ^
  "$sortedFiles = $files | Sort-Object { " ^
  "  if ($_.Name -eq 'pom.xml') { 10 } " ^
  "  elseif ($_.Name -eq 'docker-compose.yml') { 20 } " ^
  "  elseif ($_.Name -eq 'application.yml') { 30 } " ^
  "  elseif ($_.Name -eq 'application-dev.yml') { 31 } " ^
  "  elseif ($_.Name -match '(JwtAuthenticationFilter|SecurityConfig|CustomUserDetails)\.java$') { 40 } " ^
  "  elseif ($_.Name -match '(JwtUtil|JwtProperties)\.java$') { 41 } " ^
  "  elseif ($_.Name -match '(AuthService|AuthController|RefreshTokenDTO|TokenVO)') { 50 } " ^
  "  elseif ($_.Name -match '(ShortLinkService|ShortLinkController|RedirectController)') { 60 } " ^
  "  else { 70 } " ^
  "}, Name; " ^
  "$lines=@(\"LINK-HUB  $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')\", \"tests=0 fat=0 files=$($sortedFiles.Count)\", '', 'INDEX'); " ^
  "foreach($f in $sortedFiles){ $rel=($f.FullName.Substring($root.Path.Length+1) -replace '\\','/'); $n=(Get-Content $f.FullName | Measure-Object -Line).Lines; $lines += ('{0,5}  ./{1}' -f $n,$rel) }; " ^
  "$lines += ''; " ^
  "foreach($f in $sortedFiles){ $rel=($f.FullName.Substring($root.Path.Length+1) -replace '\\','/'); $lines += \"===== ./$rel =====\"; $lines += (Get-Content $f.FullName); $lines += '' }; " ^
  "Set-Content -Path $out -Value $lines -Encoding utf8; " ^
  "Write-Host \"done  $out\"; Write-Host (\"      {0} files, {1} lines\" -f $sortedFiles.Count, (Get-Content $out | Measure-Object -Line).Lines)"

endlocal