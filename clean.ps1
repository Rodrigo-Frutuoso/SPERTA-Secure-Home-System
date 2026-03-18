$ErrorActionPreference = "Stop"

$pathsToRemove = @(
    ".\out-client",
    ".\out-server",
    ".\\dist",
    ".\\src\\sperta\\data"
    ".\\src\\sperta\\server\\attestation.txt"
)

Write-Host "Limpeza concluida."
