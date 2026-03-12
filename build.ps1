javac -d out src/sperta/server/SpertaServer.java src/sperta/client/SpertaClient.java

$size = (Get-Item .\out\SpertaClient.class).Length
"SpertaClient:$size" | Set-Content .\out\attestation.txt

Write-Host "Compilado. attestation.txt atualizado: SpertaClient:$size"
#sempre que compilamos, o size muda