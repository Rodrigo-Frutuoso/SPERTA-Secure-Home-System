$ErrorActionPreference = "Stop"

function Invoke-Checked {
	param(
		[Parameter(Mandatory = $true)]
		[string]$Command,
		[Parameter(Mandatory = $true)]
		[string[]]$Arguments,
		[Parameter(Mandatory = $true)]
		[string]$ErrorMessage
	)

	& $Command @Arguments
	if ($LASTEXITCODE -ne 0) {
		throw $ErrorMessage
	}
}

$clientOut = ".\out-client"
$serverOut = ".\out-server"
$distDir = ".\dist"
$clientJar = Join-Path $distDir "SpertaClient.jar"
$serverJar = Join-Path $distDir "SpertaServer.jar"

$javacCmd = (Get-Command javac -ErrorAction Stop).Source
$jarCmd = $null

$jarInPath = Get-Command jar.exe -ErrorAction SilentlyContinue
if ($jarInPath) {
	$jarCmd = $jarInPath.Source
}

if (-not $jarCmd -and $env:JAVA_HOME) {
	$candidate = Join-Path $env:JAVA_HOME "bin\jar.exe"
	if (Test-Path $candidate) {
		$jarCmd = $candidate
	}
}

if (-not $jarCmd) {
	$searchRoots = @(
		"$env:ProgramFiles\Java",
		"$env:ProgramFiles\Eclipse Adoptium",
		"$env:ProgramFiles\Microsoft",
		"$env:ProgramFiles(x86)\Java"
	)

	foreach ($root in $searchRoots) {
		if (-not (Test-Path $root)) {
			continue
		}

		$candidate = Get-ChildItem -Path $root -Recurse -Filter "jar.exe" -ErrorAction SilentlyContinue |
			Where-Object { $_.FullName -match "\\bin\\jar\.exe$" } |
			Select-Object -First 1

		if ($candidate) {
			$jarCmd = $candidate.FullName
			break
		}
	}
}

if (-not $jarCmd) {
	throw "jar.exe nao encontrado (PATH/JAVA_HOME/java.home)."
}

Remove-Item $clientOut, $serverOut -Recurse -Force -ErrorAction SilentlyContinue
if (-not (Test-Path $distDir)) {
	New-Item -ItemType Directory -Path $distDir | Out-Null
}
New-Item -ItemType Directory -Path $clientOut, $serverOut -Force | Out-Null

# Compilar cliente e servidor em diretorias separadas para gerar jars independentes
$clientSources = Get-ChildItem -Path ".\src\sperta\client\*.java" | ForEach-Object { $_.FullName }
$serverSources = Get-ChildItem -Path ".\src\sperta\server\*.java" | ForEach-Object { $_.FullName }

$clientCompileArgs = @("-d", $clientOut) + $clientSources
$serverCompileArgs = @("-d", $serverOut) + $serverSources

Invoke-Checked -Command $javacCmd -Arguments $clientCompileArgs -ErrorMessage "Falha a compilar o cliente."
Invoke-Checked -Command $javacCmd -Arguments $serverCompileArgs -ErrorMessage "Falha a compilar o servidor."

# Gerar jars executaveis
Invoke-Checked -Command $jarCmd -Arguments @("--create", "--file", $clientJar, "--main-class", "SpertaClient", "-C", $clientOut, ".") -ErrorMessage "Falha a gerar SpertaClient.jar (pode estar a ser usado por outro processo)."
Invoke-Checked -Command $jarCmd -Arguments @("--create", "--file", $serverJar, "--main-class", "SpertaServer", "-C", $serverOut, ".") -ErrorMessage "Falha a gerar SpertaServer.jar (pode estar a ser usado por outro processo)."

# Atualizar atestacao com o tamanho do jar do cliente
$size = (Get-Item $clientJar).Length
"SpertaClient.jar:$size" | Set-Content .\src\sperta\server\attestation.txt

Write-Host "Compilado. JARs gerados em ./dist e attestation.txt atualizado: SpertaClient.jar:$size"