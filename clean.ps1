$ErrorActionPreference = "Stop"

$pathsToRemove = @(
    ".\out-client",
    ".\out-server",
    ".\dist",
    ".\src\sperta\data",
    ".\\src\\sperta\\server\\attestation.txt"
)

foreach ($path in $pathsToRemove) {
    if (Test-Path $path) {
        Remove-Item $path -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "Limpeza concluida."
