Complilar
    javac .\src\sperta\server\SpertaServer.java .\src\sperta\client\SpertaClient.java
    OU
    .\build.ps1

Run Server- exemplo
    java .\src\sperta\server\SpertaServer.java 12345

Run Client- exemplo
    java .\src\sperta\client\SpertaClient.java localhost:12345 rodrigo frutas

####################################################################################
No mac:
# compile into out/
javac -d out src/sperta/server/SpertaServer.java src/sperta/client/SpertaClient.java

# run server
java -cp out SpertaServer 12345

# run client (in another terminal)
java -cp out SpertaClient localhost:12345 rodrigo frutas
####################################################################################

valor para meter no attestation.txt -> wc -c out/SpertaClient.class
