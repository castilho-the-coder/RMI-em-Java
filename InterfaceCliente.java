import java.rmi.Remote;
import java.rmi.RemoteException;

// Interface para o Cliente (Callback para receber mensagens)
public interface InterfaceCliente extends Remote {
    void receberMensagem(String remetente, String mensagem, boolean isPrivado) throws RemoteException;
    void receberArquivo(String remetente, String nomeArquivo, byte[] dados) throws RemoteException;
    void notificar(String mensagem) throws RemoteException; // Para avisos do sistema
}
