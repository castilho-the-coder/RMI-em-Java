# WhatsUT - Sistema de Chat com RMI

## Como Executar

### 1. Compilar os arquivos
```powershell
javac InterfaceCliente.java InterfaceServidor.java Servidor.java ClienteGUI.java
```

### 2. Iniciar o Servidor
Abra um terminal e execute:
```powershell
java Servidor
```

Você verá: `Servidor WhatsUT pronto (sem SSL) porta 1099`

### 3. Iniciar o(s) Cliente(s) com Interface Gráfica (GUI)
Abra outro(s) terminal(is) e execute:
```powershell
java ClienteGUI
```

- É possível abrir dois clientes GUI simultaneamente para conversar.
- O cliente GUI se conecta ao serviço RMI com o nome `WhatsUTService`.

## Recursos da Interface Gráfica

- Login e cadastro com campos simplificados.
- Lista de usuários online e grupos com atualização periódica.
- Abas separadas para Chat Privado e Chat de Grupo.
- Envio de mensagens privadas e em grupo.
- Solicitações: admins veem pedidos de entrada nos seus grupos e podem aceitar/recusar.
- Ações de grupo: criar, entrar, sair, banir (se admin).
- Notificações de sistema aparecem na aba ativa e possuem deduplicação para evitar mensagens repetidas.

## Exemplo de Uso

**Terminal 1 (Servidor):**
```
> java Servidor
Servidor WhatsUT pronto (sem SSL) porta 1099
```

**Terminal 2 (Cliente GUI Alice):**
```
> java ClienteGUI
- Faça cadastro e login
- Selecione "Grupos" > "Criar" para criar um grupo
- Use a aba "Chat de Grupo" para enviar mensagens
```

**Terminal 3 (Cliente GUI Bob):**
```
> java ClienteGUI
- Faça cadastro e login
- Entre em "Grupos" > "Entrar" para solicitar entrada
- Aguarde aprovação do admin em "Solicitações"
```

✅ Autenticação com senha criptografada (SHA-256)
✅ Senhas mascaradas durante digitação
✅ Chat privado entre usuários
✅ Chat em grupo com aprovação
✅ Envio de arquivos
✅ Sistema de administração de grupos
✅ Banimento de usuários
✅ Suporte opcional a SSL/TLS
✅ Interface gráfica Swing (ClienteGUI)
✅ Deduplicação de mensagens de sistema

## Opcional: Executar com SSL

Para habilitar SSL, use a flag `-Drmi.ssl=true` (requer keystores configurados):
```powershell
java -Drmi.ssl=true -Djavax.net.ssl.keyStore=server.keystore Servidor
java -Drmi.ssl=true -Djavax.net.ssl.trustStore=client.truststore ClienteGUI
```
