# WhatsUT - Sistema de Chat com RMI

## Como Executar

### 1. Compilar os arquivos
```powershell
javac InterfaceCliente.java InterfaceServidor.java Servidor.java Cliente.java
```

### 2. Iniciar o Servidor
Abra um terminal e execute:
```powershell
java Servidor
```

Você verá: `Servidor WhatsUT pronto (sem SSL) porta 1099`

### 3. Iniciar o(s) Cliente(s)
Abra outro(s) terminal(is) e execute:
```powershell
java Cliente
```

## Comandos Disponíveis

### Menu Inicial
- `1` - Login
- `2` - Registrar novo usuário
- `0` - Sair

### Após Login
- `/users` - Listar usuários online
- `/groups` - Listar grupos disponíveis
- `/msg [usuario] [texto]` - Enviar mensagem privada
- `/file [usuario] [caminho]` - Enviar arquivo privado
- `/cgrupo [nome] [true/false]` - Criar grupo (true = apaga se admin sair)
- `/join [grupo]` - Solicitar entrada em grupo
- `/aprovar [grupo] [usuario]` - Aprovar entrada (apenas admin)
- `/gmsg [grupo] [texto]` - Enviar mensagem no grupo
- `/banir [grupo] [usuario]` - Banir usuário do grupo (apenas admin)
- `/sair [grupo]` - Sair do grupo
- `/logout` - Deslogar

## Exemplo de Uso

**Terminal 1 (Servidor):**
```
> java Servidor
Servidor WhatsUT pronto (sem SSL) porta 1099
```

**Terminal 2 (Cliente Alice):**
```
> java Cliente
--- Bem-vindo ao WhatsUT ---
1. Login | 2. Registrar | 0. Sair
2
Novo Usuario: alice
Nova Senha: ****** (senha oculta)
Registrado! Faça login.
1
Usuario: alice
Senha: ******
Login com sucesso!
> /cgrupo turma1 false
> /groups
Grupos: [turma1]
```

**Terminal 3 (Cliente Bob):**
```
> java Cliente
1. Login | 2. Registrar | 0. Sair
2
Novo Usuario: bob
Nova Senha: ******
1
Usuario: bob
Senha: ******
> /join turma1
Solicitacao enviada.
[SISTEMA] Voce foi aceito no grupo turma1
> /gmsg turma1 Oi pessoal!
```

## Recursos

✅ Autenticação com senha criptografada (SHA-256)
✅ Senhas mascaradas durante digitação
✅ Chat privado entre usuários
✅ Chat em grupo com aprovação
✅ Envio de arquivos
✅ Sistema de administração de grupos
✅ Banimento de usuários
✅ Suporte opcional a SSL/TLS

## Opcional: Executar com SSL

Para habilitar SSL, use a flag `-Drmi.ssl=true` (requer keystores configurados):
```powershell
java -Drmi.ssl=true -Djavax.net.ssl.keyStore=server.keystore Servidor
java -Drmi.ssl=true -Djavax.net.ssl.trustStore=client.truststore Cliente
```
