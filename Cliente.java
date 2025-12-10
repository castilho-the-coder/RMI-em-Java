import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import java.rmi.RemoteException;
import java.util.Scanner;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

public class Cliente extends UnicastRemoteObject implements InterfaceCliente {
    private static volatile boolean logado = false;
    
    protected Cliente() throws RemoteException { super(); }

    @Override
    public void receberMensagem(String remetente, String mensagem, boolean isPrivado) throws RemoteException {
        String prefixo = isPrivado ? "[PRIVADO] " : "[GRUPO] ";
        if (logado) {
            System.out.println("\n" + prefixo + remetente + ": " + mensagem);
            System.out.print("> "); // Reescreve o prompt
        }
    }

    @Override
    public void receberArquivo(String remetente, String nomeArquivo, byte[] dados) throws RemoteException {
        try {
            File f = new File("recebido_" + nomeArquivo);
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(dados);
            fos.close();
            System.out.println("\n[ARQUIVO] Recebido " + nomeArquivo + " de " + remetente);
            System.out.print("> ");
        } catch (Exception e) {
            System.out.println("Erro ao salvar arquivo.");
        }
    }

    @Override
    public void notificar(String mensagem) throws RemoteException {
        System.out.println("\n[SISTEMA] " + mensagem);
        System.out.print("> ");
    }

    private static String lerSenha(String prompt, Scanner scanner) {
        java.io.Console console = System.console();
        if (console != null) {
            // Usa Console.readPassword que esconde a senha
            char[] pwd = console.readPassword(prompt);
            return pwd == null ? "" : new String(pwd);
        } else {
            // Fallback: tenta ler sem eco usando thread e mascaramento
            System.out.print(prompt);
            return lerSenhaComMascara();
        }
    }

    private static String lerSenhaComMascara() {
        StringBuilder senha = new StringBuilder();
        try {
            while (true) {
                int c = System.in.read();
                if (c == '\r' || c == '\n') {
                    System.out.println();
                    break;
                } else if (c == 8 || c == 127) { // backspace
                    if (senha.length() > 0) {
                        senha.deleteCharAt(senha.length() - 1);
                        System.out.print("\b \b"); // apaga o último asterisco
                    }
                } else if (c >= 32 && c <= 126) { // caracteres imprimíveis
                    senha.append((char) c);
                    System.out.print("*");
                }
            }
        } catch (Exception e) {
            // Em caso de erro, retorna o que foi digitado até então
        }
        return senha.toString();
    }

    public static void main(String[] args) {
        try {
            boolean useSsl = Boolean.getBoolean("rmi.ssl");
            Registry registry = useSsl
                    ? LocateRegistry.getRegistry("localhost", 1099, new SslRMIClientSocketFactory())
                    : LocateRegistry.getRegistry("localhost", 1099);
            InterfaceServidor server = (InterfaceServidor) registry.lookup("WhatsUTService");
            Cliente clientCallback = new Cliente();

            Scanner scanner = new Scanner(System.in);
            String meuUsuario = null;

            System.out.println("--- Bem-vindo ao WhatsUT ---");            while (true) {
                if (meuUsuario == null) {
                    System.out.println("1. Login | 2. Registrar | 0. Sair");
                    String op = scanner.nextLine();
                    
                    if (op.equals("1")) {
                        System.out.print("Usuario: "); String u = scanner.nextLine();
                        String s = lerSenha("Senha: ", scanner);
                        if (server.login(u, s, clientCallback)) {
                            meuUsuario = u;
                            logado = true;
                            System.out.println("Login com sucesso!");
                        } else {
                            System.out.println("Falha no login.");
                        }
                    } else if (op.equals("2")) {
                        System.out.print("Novo Usuario: "); String u = scanner.nextLine();
                        String s = lerSenha("Nova Senha: ", scanner);
                        server.registrar(u, s);
                        System.out.println("Registrado! Faça login.");
                    } else if (op.equals("0")) {
                        System.exit(0);
                    }
                } else {
                    // Menu Logado
                    System.out.println("\nComandos: /users, /groups, /msg [user] [txt], /file [user] [path], /cgrupo [nome] [rotativo? t/f], /join [grupo], /gmsg [grupo] [txt], /aprovar [grupo] [user], /banir [grupo] [user], /sair [grupo], /logout");
                    System.out.print("> ");
                    String input = scanner.nextLine();
                    String[] parts = input.split(" ", 3);
                    String cmd = parts[0];

                    try {
                        switch (cmd) {
                            case "/users":
                                System.out.println("Online: " + server.listarUsuariosOnline());
                                break;
                            case "/groups":
                                System.out.println("Grupos: " + server.listarGrupos());
                                break;
                            case "/msg": // /msg joao Ola tudo bem
                                if(parts.length < 3) System.out.println("Use: /msg [user] [texto]");
                                else server.enviarMensagemPrivada(meuUsuario, parts[1], parts[2]);
                                break;
                            case "/file": // /file joao C:/imagem.png
                                if(parts.length < 3) System.out.println("Use: /file [user] [caminho]");
                                else {
                                    File f = new File(parts[2]);
                                    if(f.exists()) {
                                        byte[] content = Files.readAllBytes(f.toPath());
                                        server.enviarArquivo(meuUsuario, parts[1], f.getName(), content);
                                        System.out.println("Arquivo enviado.");
                                    } else {
                                        System.out.println("Arquivo nao encontrado.");
                                    }
                                }
                                break;
                            case "/cgrupo": // /cgrupo TI true
                                if(parts.length < 3) System.out.println("Use: /cgrupo [nome] [apagarSeSair? true/false]");
                                else server.criarGrupo(parts[1], meuUsuario, Boolean.parseBoolean(parts[2]));
                                break;
                            case "/join":
                                server.solicitarEntradaGrupo(meuUsuario, parts[1]);
                                System.out.println("Solicitacao enviada.");
                                break;
                            case "/aprovar":
                                server.aprovarEntrada(meuUsuario, parts[2], parts[1]);
                                System.out.println("Comando enviado.");
                                break;
                            case "/banir":
                                if (parts.length < 3) System.out.println("Use: /banir [grupo] [usuario]");
                                else server.banirUsuarioDoGrupo(meuUsuario, parts[2], parts[1]);
                                break;
                            case "/gmsg":
                                if(parts.length < 3) System.out.println("Use: /gmsg [grupo] [texto]");
                                else server.enviarMensagemGrupo(meuUsuario, parts[1], parts[2]);
                                break;
                            case "/sair":
                                server.sairDoGrupo(meuUsuario, parts[1]);
                                break;
                            case "/logout":
                                server.logout(meuUsuario);
                                logado = false;
                                meuUsuario = null;
                                break;
                            default:
                                System.out.println("Comando invalido.");
                        }
                    } catch (Exception ex) {
                        System.out.println("Erro: " + ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
