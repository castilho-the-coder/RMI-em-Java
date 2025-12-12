import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

public class ClienteGUI extends UnicastRemoteObject implements InterfaceCliente {
    private final InterfaceServidor server;
    private String usuarioLogado;
    
    private JFrame frame;
    private CardLayout cardLayout;
    private JPanel cards;
    
    private JTextField loginUsuario;
    private JPasswordField loginSenha;
    private JLabel loginStatus;
    
    private JTextField cadastroUsuario;
    private JPasswordField cadastroSenha;
    private JPasswordField cadastroConfirmar;
    
    private JTextArea chatArea;
    private JTextArea chatGrupoArea;
    private JTabbedPane chatTabs;
    private String lastSystemMessage = null;
    private long lastSystemMessageAt = 0L;
    private DefaultListModel<String> usuariosModel;
    private DefaultListModel<String> gruposModel;
    private DefaultListModel<String> solicitacoesModel;
    private JList<String> usuariosLista;
    private JList<String> gruposLista;
    private JList<String> solicitacoesLista;
    private JTextField mensagemInput;
    private JTextField mensagemGrupoInput;
    private JLabel usuarioLabel;
    private String chatAtualPrivado = null;
    private String chatAtualGrupo = null;
    private Timer refreshTimer;
    
    protected ClienteGUI(InterfaceServidor server) throws RemoteException {
        super();
        this.server = server;
    }
    
    // CALLBACK RMI - receberMensagem
    // Usuario A envia mensagem via enviarMensagemPrivada(), servidor valida e chama receberMensagem() do Usuario B (CALLBACK), GUI do Usuario B atualiza em tempo real
    @Override
    public void receberMensagem(String remetente, String mensagem, boolean isPrivado) throws RemoteException {
        String prefixo = isPrivado ? "[PRIVADO] " : "[GRUPO] ";
        if (isPrivado) {
            appendChatPrivado(prefixo + remetente + ": " + mensagem);
        } else {
            appendChatGrupo(prefixo + remetente + ": " + mensagem);
        }
    }
    
    // CALLBACK RMI - receberArquivo
    // Usuario A envia arquivo via enviarArquivo(), servidor busca Cliente B conectado e invoca receberArquivo() (CALLBACK), usuario B salva o arquivo no diretorio "downloads_<usuario>"
    @Override
    public void receberArquivo(String remetente, String nomeArquivo, byte[] dados) throws RemoteException {
        appendChatPrivado("[ARQUIVO] Recebido '" + nomeArquivo + "' de " + remetente);
        salvarArquivo(nomeArquivo, dados);
    }
    
    // CALLBACK RMI - notificar
    // Evento ocorre no servidor (ex: usuario entrou em grupo), servidor invoca notificar() de todos os membros do grupo (CALLBACK), notificacao aparece na aba atualmente selecionada

    @Override
    public void notificar(String mensagem) throws RemoteException {
        String msg = "[SISTEMA] " + mensagem;
        long now = System.currentTimeMillis();
        // Deduplica mensagens idênticas dentro de uma janela curta (1.5s)
        if (msg.equals(lastSystemMessage) && (now - lastSystemMessageAt) < 1500) {
            return;
        }
        lastSystemMessage = msg;
        lastSystemMessageAt = now;

        // Envia para a aba atualmente selecionada
        if (chatTabs != null && chatTabs.getSelectedIndex() == 1) {
            appendChatGrupo(msg);
        } else {
            appendChatPrivado(msg);
        }
    }
    
    private void appendChat(String mensagem) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(mensagem + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }
    
    private void salvarArquivo(String nomeArquivo, byte[] dados) {
        try {
            File file = new File("downloads_" + usuarioLogado + File.separator + nomeArquivo);
            file.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(dados);
            }
            appendChat("[OK] Arquivo salvo em: " + file.getAbsolutePath());
        } catch (IOException e) {
            appendChat("[ERRO] Falha ao salvar: " + e.getMessage());
        }
    }
    
    public void iniciar() {
        frame = new JFrame("WhatsUT - Chat RMI");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(1000, 700);
        frame.setLocationRelativeTo(null);
        
        cardLayout = new CardLayout();
        cards = new JPanel(cardLayout);
        
        cards.add(criarPainelLogin(), "login");
        cards.add(criarPainelCadastro(), "cadastro");
        cards.add(criarPainelPrincipal(), "principal");
        
        frame.setContentPane(cards);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                sair();
            }
        });
        
        frame.setVisible(true);
        cardLayout.show(cards, "login");
    }
    
    private JPanel criarPainelLogin() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(240, 248, 255));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 10, 15, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Titulo centralizado
        JLabel titulo = new JLabel("WhatsUT");
        titulo.setFont(new Font("Arial", Font.BOLD, 32));
        titulo.setForeground(new Color(70, 130, 180));
        gbc.gridwidth = 2;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(titulo, gbc);
        
        JLabel subtitulo = new JLabel("Sistema de Chat RMI");
        subtitulo.setFont(new Font("Arial", Font.ITALIC, 12));
        subtitulo.setForeground(Color.GRAY);
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 10, 20, 10);
        panel.add(subtitulo, gbc);
        
        // Reset insets
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Usuario
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("Usuario:"), gbc);
        loginUsuario = new JTextField(15);
        loginUsuario.setFont(new Font("Arial", Font.PLAIN, 12));
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(loginUsuario, gbc);
        
        // Senha
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        panel.add(new JLabel("Senha:"), gbc);
        loginSenha = new JPasswordField(15);
        loginSenha.setFont(new Font("Arial", Font.PLAIN, 12));
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(loginSenha, gbc);
        
        // Status
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(15, 10, 10, 10);
        loginStatus = new JLabel();
        loginStatus.setFont(new Font("Arial", Font.PLAIN, 11));
        panel.add(loginStatus, gbc);
        
        // Botoes
        JPanel botoesPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        botoesPanel.setBackground(new Color(240, 248, 255));
        
        JButton loginBtn = new JButton("Login");
        loginBtn.setPreferredSize(new Dimension(90, 35));
        loginBtn.setFont(new Font("Arial", Font.BOLD, 12));
        loginBtn.addActionListener(e -> fazerLogin());
        botoesPanel.add(loginBtn);
        
        JButton cadastroBtn = new JButton("Cadastro");
        cadastroBtn.setPreferredSize(new Dimension(90, 35));
        cadastroBtn.setFont(new Font("Arial", Font.BOLD, 12));
        cadastroBtn.addActionListener(e -> cardLayout.show(cards, "cadastro"));
        botoesPanel.add(cadastroBtn);
        
        JButton sairBtn = new JButton("Sair");
        sairBtn.setPreferredSize(new Dimension(90, 35));
        sairBtn.setFont(new Font("Arial", Font.BOLD, 12));
        sairBtn.addActionListener(e -> sair());
        botoesPanel.add(sairBtn);
        
        gbc.gridy = 5;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(10, 10, 10, 10);
        panel.add(botoesPanel, gbc);
        
        return panel;
    }
    
    private JPanel criarPainelCadastro() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(240, 248, 255));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 10, 15, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Titulo centralizado
        JLabel titulo = new JLabel("WhatsUT");
        titulo.setFont(new Font("Arial", Font.BOLD, 32));
        titulo.setForeground(new Color(70, 130, 180));
        gbc.gridwidth = 2;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(titulo, gbc);
        
        JLabel subtitulo = new JLabel("Criar Nova Conta");
        subtitulo.setFont(new Font("Arial", Font.ITALIC, 12));
        subtitulo.setForeground(Color.GRAY);
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 10, 20, 10);
        panel.add(subtitulo, gbc);
        
        // Reset insets
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Usuario
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("Usuario:"), gbc);
        cadastroUsuario = new JTextField(15);
        cadastroUsuario.setFont(new Font("Arial", Font.PLAIN, 12));
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(cadastroUsuario, gbc);
        
        // Senha
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        panel.add(new JLabel("Senha:"), gbc);
        cadastroSenha = new JPasswordField(15);
        cadastroSenha.setFont(new Font("Arial", Font.PLAIN, 12));
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(cadastroSenha, gbc);
        
        // Confirmar Senha
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0;
        panel.add(new JLabel("Confirmar:"), gbc);
        cadastroConfirmar = new JPasswordField(15);
        cadastroConfirmar.setFont(new Font("Arial", Font.PLAIN, 12));
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(cadastroConfirmar, gbc);
        
        // Botoes
        JPanel botoesPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        botoesPanel.setBackground(new Color(240, 248, 255));
        
        JButton cadastrarBtn = new JButton("Cadastrar");
        cadastrarBtn.setPreferredSize(new Dimension(90, 35));
        cadastrarBtn.setFont(new Font("Arial", Font.BOLD, 12));
        cadastrarBtn.addActionListener(e -> fazerCadastro());
        botoesPanel.add(cadastrarBtn);
        
        JButton voltarBtn = new JButton("Voltar");
        voltarBtn.setPreferredSize(new Dimension(90, 35));
        voltarBtn.setFont(new Font("Arial", Font.BOLD, 12));
        voltarBtn.addActionListener(e -> {
            cardLayout.show(cards, "login");
            limparCadastro();
        });
        botoesPanel.add(voltarBtn);
        
        gbc.gridwidth = 2;
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.insets = new Insets(15, 10, 10, 10);
        panel.add(botoesPanel, gbc);
        
        return panel;
    }
    
    private JPanel criarPainelPrincipal() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        JPanel headerPanel = criarHeader();
        panel.add(headerPanel, BorderLayout.NORTH);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        
        JPanel leftPanel = criarPainelEsquerdo();
        splitPane.setLeftComponent(leftPanel);
        splitPane.setDividerLocation(250);
        
        JPanel rightPanel = criarPainelChat();
        splitPane.setRightComponent(rightPanel);
        
        panel.add(splitPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel criarHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(70, 130, 180));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        usuarioLabel = new JLabel("Usuario: " + usuarioLogado);
        usuarioLabel.setForeground(Color.WHITE);
        usuarioLabel.setFont(new Font("Arial", Font.BOLD, 14));
        panel.add(usuarioLabel, BorderLayout.WEST);
        
        JLabel titulo = new JLabel("WhatsUT - Chat RMI");
        titulo.setForeground(Color.WHITE);
        titulo.setFont(new Font("Arial", Font.BOLD, 16));
        titulo.setHorizontalAlignment(JLabel.CENTER);
        panel.add(titulo, BorderLayout.CENTER);
        
        JButton logoutBtn = new JButton("Logout");
        logoutBtn.addActionListener(e -> fazerLogout());
        panel.add(logoutBtn, BorderLayout.EAST);
        
        return panel;
    }
    
    private JPanel criarPainelEsquerdo() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new TitledBorder("Contatos e Grupos"));
        
        JTabbedPane tabbedPane = new JTabbedPane();
        
        // Aba de usuarios online
        usuariosModel = new DefaultListModel<>();
        usuariosLista = new JList<>(usuariosModel);
        usuariosLista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        usuariosLista.addListSelectionListener(e -> selecionarUsuario());
        tabbedPane.addTab("Online", new JScrollPane(usuariosLista));
        
        // Aba de grupos
        JPanel gruposPanel = new JPanel(new BorderLayout(5, 5));
        gruposModel = new DefaultListModel<>();
        gruposLista = new JList<>(gruposModel);
        gruposLista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        gruposLista.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selecionarGrupo();
            }
        });
        
        JPanel gruposToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        
        JButton criarGrupoBtn = new JButton("Criar");
        criarGrupoBtn.addActionListener(e -> dialogCriarGrupo());
        gruposToolbar.add(criarGrupoBtn);
        
        JButton entrarGrupoBtn = new JButton("Entrar");
        entrarGrupoBtn.addActionListener(e -> dialogEntrarGrupo());
        gruposToolbar.add(entrarGrupoBtn);
        
        JButton sairGrupoBtn = new JButton("Sair");
        sairGrupoBtn.addActionListener(e -> sairDoGrupo());
        gruposToolbar.add(sairGrupoBtn);
        
        gruposPanel.add(gruposToolbar, BorderLayout.NORTH);
        gruposPanel.add(new JScrollPane(gruposLista), BorderLayout.CENTER);
        tabbedPane.addTab("Grupos", gruposPanel);
        
        // Aba de solicitacoes
        JPanel solicitacoesPanel = new JPanel(new BorderLayout(5, 5));
        solicitacoesModel = new DefaultListModel<>();
        solicitacoesLista = new JList<>(solicitacoesModel);
        solicitacoesLista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        JPanel solicitacoesToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        
        JButton aceitarBtn = new JButton("Aceitar");
        aceitarBtn.addActionListener(e -> aceitarSolicitacao());
        solicitacoesToolbar.add(aceitarBtn);
        
        JButton recusarBtn = new JButton("Recusar");
        recusarBtn.addActionListener(e -> recusarSolicitacao());
        solicitacoesToolbar.add(recusarBtn);
        
        solicitacoesPanel.add(solicitacoesToolbar, BorderLayout.NORTH);
        solicitacoesPanel.add(new JScrollPane(solicitacoesLista), BorderLayout.CENTER);
        tabbedPane.addTab("Solicitacoes", solicitacoesPanel);
        
        panel.add(tabbedPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel criarPainelChat() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new TitledBorder("Chat"));
        
        // Abas para separar chats privados e de grupos
        chatTabs = new JTabbedPane();
        
        // Aba de Chat Privado
        JPanel painelChatPrivado = new JPanel(new BorderLayout(5, 5));
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        painelChatPrivado.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        
        JPanel inputPanelPrivado = criarPainelEntradaPrivado();
        painelChatPrivado.add(inputPanelPrivado, BorderLayout.SOUTH);
        
        chatTabs.addTab("Chat Privado", painelChatPrivado);
        
        // Aba de Chat de Grupo
        JPanel painelChatGrupo = new JPanel(new BorderLayout(5, 5));
        chatGrupoArea = new JTextArea();
        chatGrupoArea.setEditable(false);
        chatGrupoArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        chatGrupoArea.setLineWrap(true);
        chatGrupoArea.setWrapStyleWord(true);
        painelChatGrupo.add(new JScrollPane(chatGrupoArea), BorderLayout.CENTER);
        
        JPanel inputPanelGrupo = criarPainelEntradaGrupo();
        painelChatGrupo.add(inputPanelGrupo, BorderLayout.SOUTH);
        
        chatTabs.addTab("Chat de Grupo", painelChatGrupo);
        
        panel.add(chatTabs, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel criarPainelEntradaPrivado() {
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        
        JPanel selectorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        selectorPanel.add(new JLabel("Chat com:"));
        JLabel userLabel = new JLabel();
        userLabel.setFont(new Font("Arial", Font.BOLD, 12));
        userLabel.setForeground(new Color(70, 130, 180));
        selectorPanel.add(userLabel);
        inputPanel.add(selectorPanel, BorderLayout.NORTH);
        
        JPanel msgPanel = new JPanel(new BorderLayout(5, 0));
        mensagemInput = new JTextField();
        mensagemInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    enviarMensagemPrivada();
                }
            }
        });
        msgPanel.add(mensagemInput, BorderLayout.CENTER);
        
        JPanel botoesPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        
        JButton enviarButton = new JButton("Enviar");
        enviarButton.addActionListener(e -> enviarMensagemPrivada());
        botoesPanel.add(enviarButton);
        
        JButton arquivoBtn = new JButton("Arquivo");
        arquivoBtn.addActionListener(e -> enviarArquivo(true));
        botoesPanel.add(arquivoBtn);
        
        msgPanel.add(botoesPanel, BorderLayout.EAST);
        inputPanel.add(msgPanel, BorderLayout.CENTER);
        
        return inputPanel;
    }
    
    private JPanel criarPainelEntradaGrupo() {
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        
        JPanel selectorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        selectorPanel.add(new JLabel("Grupo:"));
        JLabel grupoLabel = new JLabel();
        grupoLabel.setFont(new Font("Arial", Font.BOLD, 12));
        grupoLabel.setForeground(new Color(70, 130, 180));
        selectorPanel.add(grupoLabel);
        inputPanel.add(selectorPanel, BorderLayout.NORTH);
        
        JPanel msgPanel = new JPanel(new BorderLayout(5, 0));
        mensagemGrupoInput = new JTextField();
        mensagemGrupoInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    enviarMensagemGrupo();
                }
            }
        });
        msgPanel.add(mensagemGrupoInput, BorderLayout.CENTER);
        
        JPanel botoesPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        
        JButton enviarButton = new JButton("Enviar");
        enviarButton.addActionListener(e -> enviarMensagemGrupo());
        botoesPanel.add(enviarButton);
        
        JButton arquivoBtn = new JButton("Arquivo");
        arquivoBtn.addActionListener(e -> enviarArquivo(false));
        botoesPanel.add(arquivoBtn);
        
        msgPanel.add(botoesPanel, BorderLayout.EAST);
        inputPanel.add(msgPanel, BorderLayout.CENTER);
        
        return inputPanel;
    }
    
    private void fazerLogin() {
        String usuario = loginUsuario.getText().trim();
        String senha = new String(loginSenha.getPassword());
        
        if (usuario.isEmpty() || senha.isEmpty()) {
            loginStatus.setText("Preencha todos os campos");
            loginStatus.setForeground(Color.RED);
            return;
        }
        
        try {
            if (server.login(usuario, senha, this)) {
                usuarioLogado = usuario;
                usuarioLabel.setText("Usuario: " + usuarioLogado);
                cardLayout.show(cards, "principal");
                iniciarAtualizacoes();
                loginStatus.setText("");
            } else {
                loginStatus.setText("Usuario ou senha invalidos");
                loginStatus.setForeground(Color.RED);
            }
        } catch (RemoteException ex) {
            loginStatus.setText("Erro de conexao");
            loginStatus.setForeground(Color.RED);
        }
    }
    
    private void fazerCadastro() {
        String usuario = cadastroUsuario.getText().trim();
        String senha = new String(cadastroSenha.getPassword());
        String confirmar = new String(cadastroConfirmar.getPassword());
        
        if (usuario.isEmpty() || senha.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Preencha todos os campos", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (!senha.equals(confirmar)) {
            JOptionPane.showMessageDialog(frame, "As senhas nao coincidem", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            if (server.registrar(usuario, senha)) {
                JOptionPane.showMessageDialog(frame, "Cadastro realizado!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                cardLayout.show(cards, "login");
                limparCadastro();
            } else {
                JOptionPane.showMessageDialog(frame, "Usuario ja existe", "Erro", JOptionPane.ERROR_MESSAGE);
            }
        } catch (RemoteException ex) {
            JOptionPane.showMessageDialog(frame, "Erro de conexao", "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void iniciarAtualizacoes() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
        }
        
        refreshTimer = new Timer();
        refreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                atualizarUsuariosOnline();
                atualizarGrupos();
                atualizarSolicitacoesPendentes();
            }
        }, 0, 2000);
    }
    
    private void atualizarUsuariosOnline() {
        try {
            List<String> usuarios = server.listarUsuariosOnline();
            SwingUtilities.invokeLater(() -> {
                usuariosModel.clear();
                for (String u : usuarios) {
                    if (!u.equals(usuarioLogado)) {
                        usuariosModel.addElement(u);
                    }
                }
            });
        } catch (RemoteException e) {
        }
    }
    
    private void atualizarGrupos() {
        try {
            List<String> grupos = server.listarGrupos();
            SwingUtilities.invokeLater(() -> {
                gruposModel.clear();
                for (String g : grupos) {
                    gruposModel.addElement(g);
                }
            });
        } catch (RemoteException e) {
        }
    }
    
    private void atualizarSolicitacoesPendentes() {
        try {
            Map<String, List<String>> solicitacoes = server.obterSolicitacoesPorGrupo(usuarioLogado);
            SwingUtilities.invokeLater(() -> {
                solicitacoesModel.clear();
                for (Map.Entry<String, List<String>> entry : solicitacoes.entrySet()) {
                    String grupo = entry.getKey();
                    List<String> usuarios = entry.getValue();
                    for (String usuario : usuarios) {
                        solicitacoesModel.addElement(usuario + " - " + grupo);
                    }
                }
            });
        } catch (RemoteException e) {
        }
    }
    
    private void selecionarUsuario() {
        int idx = usuariosLista.getSelectedIndex();
        if (idx >= 0) {
            chatAtualPrivado = usuariosModel.getElementAt(idx);
            appendChatPrivado("[INFO] Chat privado com " + chatAtualPrivado + " selecionado");
        }
    }
    
    private void selecionarGrupo() {
        int idx = gruposLista.getSelectedIndex();
        if (idx >= 0) {
            chatAtualGrupo = gruposModel.getElementAt(idx);
            appendChatGrupo("[INFO] Grupo '" + chatAtualGrupo + "' selecionado");
        }
    }
    
    private void appendChatPrivado(String mensagem) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(mensagem + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }
    
    private void appendChatGrupo(String mensagem) {
        SwingUtilities.invokeLater(() -> {
            chatGrupoArea.append(mensagem + "\n");
            chatGrupoArea.setCaretPosition(chatGrupoArea.getDocument().getLength());
        });
    }
    
    private void enviarMensagemPrivada() {
        if (chatAtualPrivado == null) {
            JOptionPane.showMessageDialog(frame, "Selecione um contato", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String mensagem = mensagemInput.getText().trim();
        if (mensagem.isEmpty()) {
            return;
        }
        
        try {
            server.enviarMensagemPrivada(usuarioLogado, chatAtualPrivado, mensagem);
            appendChatPrivado("[VOCE] " + mensagem);
            mensagemInput.setText("");
        } catch (RemoteException ex) {
            JOptionPane.showMessageDialog(frame, "Erro ao enviar", "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void enviarMensagemGrupo() {
        if (chatAtualGrupo == null) {
            JOptionPane.showMessageDialog(frame, "Selecione um grupo", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String mensagem = mensagemGrupoInput.getText().trim();
        if (mensagem.isEmpty()) {
            return;
        }
        
        try {
            server.enviarMensagemGrupo(usuarioLogado, chatAtualGrupo, mensagem);
            appendChatGrupo("[VOCE] " + mensagem);
            mensagemGrupoInput.setText("");
        } catch (RemoteException ex) {
            JOptionPane.showMessageDialog(frame, "Erro ao enviar", "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void enviarArquivo(boolean ehPrivado) {
        String destinatario = ehPrivado ? chatAtualPrivado : chatAtualGrupo;
        if (destinatario == null) {
            JOptionPane.showMessageDialog(frame, "Selecione um contato", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(frame);
        
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                byte[] dados = new byte[(int) file.length()];
                try (FileInputStream fis = new FileInputStream(file)) {
                    fis.read(dados);
                }
                
                server.enviarArquivo(usuarioLogado, destinatario, file.getName(), dados);
                String msg = "[ARQUIVO] '" + file.getName() + "' enviado";
                if (ehPrivado) {
                    appendChatPrivado(msg);
                } else {
                    appendChatGrupo(msg);
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Erro ao enviar", "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void dialogCriarGrupo() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        JTextField nomeField = new JTextField();
        JCheckBox deletarCheckbox = new JCheckBox("Deletar se eu sair?");
        
        panel.add(new JLabel("Nome:"));
        panel.add(nomeField);
        panel.add(deletarCheckbox);
        panel.add(new JLabel(""));
        
        int result = JOptionPane.showConfirmDialog(frame, panel, "Criar Grupo", JOptionPane.OK_CANCEL_OPTION);
        
        if (result == JOptionPane.OK_OPTION && !nomeField.getText().trim().isEmpty()) {
            try {
                String nome = nomeField.getText().trim();
                if (server.criarGrupo(nome, usuarioLogado, deletarCheckbox.isSelected())) {
                    appendChat("[OK] Grupo '" + nome + "' criado!");
                    atualizarGrupos();
                } else {
                    JOptionPane.showMessageDialog(frame, "Grupo ja existe", "Erro", JOptionPane.ERROR_MESSAGE);
                }
            } catch (RemoteException ex) {
                JOptionPane.showMessageDialog(frame, "Erro ao criar", "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void dialogEntrarGrupo() {
        Object[] grupos = gruposModel.toArray();
        if (grupos.length == 0) {
            JOptionPane.showMessageDialog(frame, "Nenhum grupo disponivel", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String grupo = (String) JOptionPane.showInputDialog(frame, "Selecione um grupo:", "Entrar em Grupo",
                JOptionPane.QUESTION_MESSAGE, null, grupos, grupos[0]);
        
        if (grupo != null) {
            try {
                if (server.solicitarEntradaGrupo(usuarioLogado, grupo)) {
                    appendChat("[OK] Solicitacao de entrada enviada! Aguardando aprovacao do admin...");
                } else {
                    JOptionPane.showMessageDialog(frame, "Erro ao solicitar", "Erro", JOptionPane.ERROR_MESSAGE);
                }
            } catch (RemoteException ex) {
                JOptionPane.showMessageDialog(frame, "Erro", "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void sairDoGrupo() {
        if (chatAtualGrupo == null) {
            JOptionPane.showMessageDialog(frame, "Selecione um grupo", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        int confirm = JOptionPane.showConfirmDialog(frame, 
            "Sair do grupo '" + chatAtualGrupo + "'?",
            "Confirmar", JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                server.sairDoGrupo(usuarioLogado, chatAtualGrupo);
                appendChatGrupo("[INFO] Voce saiu do grupo");
                chatAtualGrupo = null;
                atualizarGrupos();
            } catch (RemoteException ex) {
                JOptionPane.showMessageDialog(frame, "Erro ao sair", "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void aceitarSolicitacao() {
        int idx = solicitacoesLista.getSelectedIndex();
        if (idx < 0) {
            JOptionPane.showMessageDialog(frame, "Selecione uma solicitacao", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String item = solicitacoesModel.getElementAt(idx);
        String[] partes = item.split(" - ");
        if (partes.length < 2) {
            return;
        }
        
        String usuario = partes[0];
        String grupo = partes[1];
        
        try {
            server.aprovarEntrada(usuarioLogado, usuario, grupo);
            appendChat("[OK] Usuario " + usuario + " aprovado no grupo " + grupo);
            solicitacoesModel.removeElementAt(idx);
        } catch (RemoteException ex) {
            JOptionPane.showMessageDialog(frame, "Erro ao aprovar", "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void recusarSolicitacao() {
        int idx = solicitacoesLista.getSelectedIndex();
        if (idx < 0) {
            JOptionPane.showMessageDialog(frame, "Selecione uma solicitacao", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String item = solicitacoesModel.getElementAt(idx);
        String[] partes = item.split(" - ");
        if (partes.length < 2) {
            return;
        }
        
        String usuario = partes[0];
        String grupo = partes[1];
        
        appendChat("[RECUSADO] Solicitacao de " + usuario + " para " + grupo + " foi recusada");
        solicitacoesModel.removeElementAt(idx);
    }
    
    private void fazerLogout() {
        try {
            server.logout(usuarioLogado);
            if (refreshTimer != null) {
                refreshTimer.cancel();
            }
            usuarioLogado = null;
            chatAtualPrivado = null;
            chatAtualGrupo = null;
            cardLayout.show(cards, "login");
            limparLogin();
        } catch (RemoteException ex) {
            JOptionPane.showMessageDialog(frame, "Erro ao logout", "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void sair() {
        try {
            if (usuarioLogado != null) {
                server.logout(usuarioLogado);
            }
            if (refreshTimer != null) {
                refreshTimer.cancel();
            }
        } catch (RemoteException ignored) {}
        System.exit(0);
    }
    
    private void limparLogin() {
        loginUsuario.setText("");
        loginSenha.setText("");
        loginStatus.setText("");
    }
    
    private void limparCadastro() {
        cadastroUsuario.setText("");
        cadastroSenha.setText("");
        cadastroConfirmar.setText("");
    }
    
    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            InterfaceServidor server = (InterfaceServidor) registry.lookup("WhatsUTService");
            
            SwingUtilities.invokeLater(() -> {
                try {
                    ClienteGUI cliente = new ClienteGUI(server);
                    cliente.iniciar();
                } catch (RemoteException ex) {
                    JOptionPane.showMessageDialog(null, 
                        "Erro ao criar cliente", 
                        "Erro", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                }
            });
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, 
                "Erro ao conectar ao servidor", 
                "Erro de Conexao", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}
