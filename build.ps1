javac .\src\sperta\server\SpertaServer.java .\src\sperta\client\SpertaClient.java

$size = (Get-Item .\src\sperta\client\SpertaClient.class).Length
"SpertaClient:$size" | Set-Content .\src\sperta\client\attestation.txt

Write-Host "Compilado. attestation.txt atualizado: SpertaClient:$size"
#sempre que compilamos, o size muda