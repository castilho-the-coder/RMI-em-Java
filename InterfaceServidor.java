import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

// Interface para o Servidor
public interface InterfaceServidor extends Remote {
    // Autenticação
    boolean registrar(String usuario, String senha) throws RemoteException;
    boolean login(String usuario, String senha, InterfaceCliente clientRef) throws RemoteException;
    void logout(String usuario) throws RemoteException;

    // Listas
    List<String> listarUsuariosOnline() throws RemoteException;
    List<String> listarGrupos() throws RemoteException;

    // Chat Privado
    void enviarMensagemPrivada(String remetente, String destinatario, String mensagem) throws RemoteException;
    void enviarArquivo(String remetente, String destinatario, String nomeArquivo, byte[] dados) throws RemoteException;

    // Gestão de Grupos
    boolean criarGrupo(String nomeGrupo, String criador, boolean apagarSeAdminSair) throws RemoteException;
    boolean solicitarEntradaGrupo(String usuario, String nomeGrupo) throws RemoteException;
    void aprovarEntrada(String admin, String usuarioAprovado, String nomeGrupo) throws RemoteException;
    void enviarMensagemGrupo(String remetente, String nomeGrupo, String mensagem) throws RemoteException;
    void banirUsuarioDoGrupo(String admin, String usuarioAlvo, String nomeGrupo) throws RemoteException;
    
    // Gestão/Exclusão
    void banirUsuarioGlobal(String solicitante, String usuarioAlvo) throws RemoteException;
    void sairDoGrupo(String usuario, String nomeGrupo) throws RemoteException;
    
    // Novas funcionalidades
    Map<String, List<String>> obterSolicitacoesPorGrupo(String usuarioAdmin) throws RemoteException;
    boolean ehAdminDoGrupo(String usuario, String grupo) throws RemoteException;
}
