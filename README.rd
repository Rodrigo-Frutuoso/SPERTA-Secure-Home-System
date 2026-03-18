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
