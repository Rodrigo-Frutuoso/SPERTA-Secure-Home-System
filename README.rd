Compilar (recomendado)
    .\build.ps1

Compilar manualmente
    javac -d out-client .\src\sperta\client\*.java
    javac -d out-server .\src\sperta\server\*.java
    jar --create --file .\dist\SpertaClient.jar --main-class SpertaClient -C .\out-client .
    jar --create --file .\dist\SpertaServer.jar --main-class SpertaServer -C .\out-server .
    # se o comando jar nao existir no PATH, usar o jar.exe ao lado do javac

Run Server- exemplo
    java -jar .\dist\SpertaServer.jar 12345

Run Client- exemplo
    java -jar .\dist\SpertaClient.jar localhost:12345 rodrigo frutas

####################################################################################
No mac:
# compile client/server separately
javac -d out-client src/sperta/client/*.java
javac -d out-server src/sperta/server/*.java

# package jars
jar --create --file dist/SpertaClient.jar --main-class SpertaClient -C out-client .
jar --create --file dist/SpertaServer.jar --main-class SpertaServer -C out-server .

# run server
java -jar dist/SpertaServer.jar 12345

# run client (in another terminal)
java -jar dist/SpertaClient.jar localhost:12345 rodrigo frutas
####################################################################################

valor para meter no attestation.txt -> wc -c dist/SpertaClient.jar

####################################################################################
Responsabilidade das classes

Cliente:
- SpertaClient: ponto de entrada do cliente. Faz parsing dos argumentos (host/porto, user, password) e inicia a sessao.
- ClientSession: estabelece a ligacao ao servidor, executa a atestacao e o fluxo de autenticacao.
- ClientCommandLoop: apresenta o menu, le comandos do utilizador e envia os comandos CREATE, ADD, RD, EC, RT e RH.

Servidor:
- SpertaServer: ponto de entrada do servidor. Abre a porta e aceita ligacoes de clientes.
- ClientSessionHandler: thread por cliente. Recebe pedidos, chama servicos e envia respostas.
- AuthService: trata da atestacao e da autenticacao/registo de utilizadores.
- CommandService: implementa a logica dos comandos CREATE, ADD, RD, EC, RT e RH.
- DataRepository: centraliza toda a persistencia em ficheiros (users, houses, states e logs).

####################################################################################
