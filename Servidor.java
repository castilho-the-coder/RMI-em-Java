import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Classes auxiliares simples
class Grupo {
    String nome;
    String admin;
    boolean apagarSeAdminSair;
    List<String> membros = new ArrayList<>();
    List<String> pendentes = new ArrayList<>(); // Fila de aprovação

    public Grupo(String nome, String admin, boolean apagarSeAdminSair) {
        this.nome = nome;
        this.admin = admin;
        this.apagarSeAdminSair = apagarSeAdminSair;
        this.membros.add(admin);
    }
}

public class Servidor extends UnicastRemoteObject implements InterfaceServidor {
    // Armazenamento em memória (Simulando Banco de Dados)
    private Map<String, String> usuariosCadastrados = new HashMap<>(); // User -> Hash Senha
    private Map<String, InterfaceCliente> usuariosOnline = new ConcurrentHashMap<>();
    private Map<String, Grupo> grupos = new ConcurrentHashMap<>();

    protected Servidor() throws RemoteException { super(); }

    // --- Criptografia (SHA-256) ---
    private String hashSenha(String senha) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(senha.getBytes());
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) hexString.append(String.format("%02x", b));
            return hexString.toString();
        } catch (Exception e) { return senha; }
    }

    // --- Implementação da Interface ---

    @Override
    public synchronized boolean registrar(String usuario, String senha) throws RemoteException {
        if (usuariosCadastrados.containsKey(usuario)) return false;
        usuariosCadastrados.put(usuario, hashSenha(senha));
        System.out.println("Novo usuario registrado: " + usuario);
        return true;
    }

    @Override
    public synchronized boolean login(String usuario, String senha, InterfaceCliente clientRef) throws RemoteException {
        String hashArmazenado = usuariosCadastrados.get(usuario);
        if (hashArmazenado != null && hashArmazenado.equals(hashSenha(senha))) {
            usuariosOnline.put(usuario, clientRef);
            System.out.println(usuario + " logou.");
            return true;
        }
        return false;
    }

    @Override
    public void logout(String usuario) throws RemoteException {
        usuariosOnline.remove(usuario);
        System.out.println(usuario + " deslogou.");
    }

    @Override
    public List<String> listarUsuariosOnline() throws RemoteException {
        return new ArrayList<>(usuariosOnline.keySet());
    }

    @Override
    public List<String> listarGrupos() throws RemoteException {
        return new ArrayList<>(grupos.keySet());
    }

    @Override
    public void enviarMensagemPrivada(String remetente, String destinatario, String mensagem) throws RemoteException {
        InterfaceCliente dest = usuariosOnline.get(destinatario);
        if (dest != null) {
            dest.receberMensagem(remetente, mensagem, true);
        }
    }

    @Override
    public void enviarArquivo(String remetente, String destinatario, String nomeArquivo, byte[] dados) throws RemoteException {
        InterfaceCliente dest = usuariosOnline.get(destinatario);
        if (dest != null) {
            dest.receberArquivo(remetente, nomeArquivo, dados);
        }
    }

    // --- Lógica de Grupos ---

    @Override
    public boolean criarGrupo(String nomeGrupo, String criador, boolean apagarSeAdminSair) throws RemoteException {
        if (grupos.containsKey(nomeGrupo)) return false;
        grupos.put(nomeGrupo, new Grupo(nomeGrupo, criador, apagarSeAdminSair));
        return true;
    }

    @Override
    public boolean solicitarEntradaGrupo(String usuario, String nomeGrupo) throws RemoteException {
        Grupo g = grupos.get(nomeGrupo);
        if (g != null) {
            if (!g.membros.contains(usuario)) {
                g.pendentes.add(usuario);
                // Notificar Admin
                InterfaceCliente adminRef = usuariosOnline.get(g.admin);
                if (adminRef != null) adminRef.notificar("Solicitacao de entrada no grupo " + nomeGrupo + ": " + usuario);
                return true;
            }
        }
        return false;
    }

    @Override
    public void aprovarEntrada(String admin, String usuarioAprovado, String nomeGrupo) throws RemoteException {
        Grupo g = grupos.get(nomeGrupo);
        if (g != null && g.admin.equals(admin) && g.pendentes.contains(usuarioAprovado)) {
            g.pendentes.remove(usuarioAprovado);
            g.membros.add(usuarioAprovado);
            InterfaceCliente userRef = usuariosOnline.get(usuarioAprovado);
            if (userRef != null) userRef.notificar("Voce foi aceito no grupo " + nomeGrupo);
        }
    }

    @Override
    public void enviarMensagemGrupo(String remetente, String nomeGrupo, String mensagem) throws RemoteException {
        Grupo g = grupos.get(nomeGrupo);
        if (g != null && g.membros.contains(remetente)) {
            for (String membro : g.membros) {
                if (!membro.equals(remetente)) { // Não envia para si mesmo
                    InterfaceCliente dest = usuariosOnline.get(membro);
                    if (dest != null) {
                        try {
                            dest.receberMensagem("[" + nomeGrupo + "] " + remetente, mensagem, false);
                        } catch (RemoteException e) {
                            // Cliente pode ter caído sem logout
                        }
                    }
                }
            }
        }
    }

    @Override
    public void banirUsuarioGlobal(String solicitante, String usuarioAlvo) throws RemoteException {
        // Regra simplificada: remove da lista de cadastrados e desloga
        usuariosCadastrados.remove(usuarioAlvo);
        usuariosOnline.remove(usuarioAlvo);
        System.out.println("Usuario " + usuarioAlvo + " banido por " + solicitante);
    }

    @Override
    public void banirUsuarioDoGrupo(String admin, String usuarioAlvo, String nomeGrupo) throws RemoteException {
        Grupo g = grupos.get(nomeGrupo);
        if (g == null) return;
        if (!g.admin.equals(admin)) return; // Somente admin atual pode banir
        if (usuarioAlvo.equals(admin)) return; // Admin não se bane; use sairDoGrupo

        // Remove de membros e pendências
        g.membros.remove(usuarioAlvo);
        g.pendentes.remove(usuarioAlvo);

        // Notifica alvo se estiver online
        InterfaceCliente dest = usuariosOnline.get(usuarioAlvo);
        if (dest != null) dest.notificar("Voce foi banido do grupo " + nomeGrupo);

        // Se não restarem membros, remove grupo
        if (g.membros.isEmpty()) {
            grupos.remove(nomeGrupo);
            System.out.println("Grupo " + nomeGrupo + " removido apos banimento.");
        }
    }

    @Override
    public void sairDoGrupo(String usuario, String nomeGrupo) throws RemoteException {
        Grupo g = grupos.get(nomeGrupo);
        if (g != null && g.membros.contains(usuario)) {
            g.membros.remove(usuario);
            
            // Lógica do requisito 6: Admin sai
            if (g.admin.equals(usuario)) {
                if (g.apagarSeAdminSair || g.membros.isEmpty()) {
                    grupos.remove(nomeGrupo);
                    System.out.println("Grupo " + nomeGrupo + " removido.");
                } else {
                    // Elege novo admin (o próximo da lista)
                    String novoAdmin = g.membros.get(0);
                    g.admin = novoAdmin;
                    InterfaceCliente dest = usuariosOnline.get(novoAdmin);
                    if (dest != null) dest.notificar("Voce e o novo ADMIN do grupo " + nomeGrupo);
                }
            }
        }
    }

    public static void main(String[] args) {
        try {
            InterfaceServidor server = new Servidor();
            boolean useSsl = Boolean.getBoolean("rmi.ssl");

            Registry registry;
            if (useSsl) {
                // Requer que o keystore/truststore estejam configurados via -Djavax.net.ssl.keyStore / trustStore
                registry = LocateRegistry.createRegistry(
                        1099,
                        new SslRMIClientSocketFactory(),
                        new SslRMIServerSocketFactory());
                System.out.println("Servidor WhatsUT pronto com SSL (porta 1099)");
            } else {
                registry = LocateRegistry.createRegistry(1099);
                System.out.println("Servidor WhatsUT pronto (sem SSL) porta 1099");
            }

            registry.rebind("WhatsUTService", server);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
