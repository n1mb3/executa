package process_jsp;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Hashtable;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import br.com.startware.process.util.ConstantesCampos;
import process_jsp.db.Agenda;
import process_jsp.db.Arquivos;
import process_jsp.db.CartaPrecatoria;
import process_jsp.db.Conta;
import process_jsp.db.Ementa;
import process_jsp.db.Excluir;
import process_jsp.db.Jurisprudencia;
import process_jsp.db.Log;
import process_jsp.db.Pericia;
import process_jsp.db.Peticao;
import process_jsp.db.Processo;
import process_jsp.db.Recurso;
import process_jsp.db.SelectGenerico;
import process_jsp.db.Sentenca;
import process_jsp.db.Testemunha;
import process_jsp.relatorios.precatorio.descritivo.Precatorio;
import process_jsp.rh.UsuarioSistema;
import process_jsp.rh.acesso.Perfil;
import process_jsp.rh.acesso.PerfilDAO;
import process_jsp.rh.acesso.PermissaoModulo;
import process_jsp.rh.acesso.Permissoes;
import process_jsp.rh.config.Padrao;
import startware.GeraLog;
import startware.util.config.ConfigManager;
import startware.util.mail.Contatos;
import startware.util.mail.SendMail;


/**
 * <p>Company: StartWare</p>
 * @author Éber Teles
 * @version 1.0
 */
public class Executa extends UsuarioSistema {
    public String nomeDB = "";
    public String nomeDBReal = "";
    public String usuarioDB = "";
    public boolean autocommit = false;
    public String pesq_cod_processo;
    public String funcao;
    public int retorno;
    public int codigo;
    public String formatoData;
    public String erro;
    public String nomeServidor;
    protected String resposta;
    protected String[] respostaArray;
    protected int licencas = 2;
    protected String unidade = "";
    public String operador = "";
    private String path;

    public final String OCULTA = "style=\"display:none\"";
    public final String DESATIVA = "disabled";

    /* Constates para controle dos includes de menus */
    public final String MENU_PRINCIPAL = "menuPrincpal";
    public final String MENU_ADM = "menuAdm";
    public final String MENU_SEM = "semMenu";
    public final String MENU_DESATIVADO = "";
    public final String MENU_SENHA = "menuSenha";

    private HashMap perfis = null;

    //Variáveis para serem utilizadas no LOG do método geraLog (grava na tabela LOGTAB)
    String pastaLog = "";
    int idProcessoLog = 0;

    public Executa() {
    }
    
    
	/**
	 * Retorna a senha criptgrafada, dado o código do usuário 
	 * @param codUsuario Código do usuário
	 * @return senha Retorna a senha do usuário criptografada(md5)
	 * */
	public String getSenhaCript(String codUsuario){
		PreparedStatement ps  = null;
		Connection conexao    = null;
		ResultSet rs          = null;
		
		
		try{
			String query = "SELECT SENHA FROM JUWDB01.LOGIN WHERE ID_USUARIO=?";
			
			conexao = Conexao.getInstance().getConnection();
			
			ps = conexao.prepareStatement(query);
			ps.setInt(1,FormataValor.strToInt(codUsuario));
			rs = ps.executeQuery();
			
			if(rs.next()){
				return rs.getString("SENHA");
			}
			return null;
			
		}catch(Exception exception){
			exception.printStackTrace();
		}finally{
			Conexao.fecharConexao(conexao);
		}		
		return null;
		
	}

    public void setConfigManager(
						        ServletContext application,
						        HttpSession session,
						        String pathDaAplicacao) {
        setPath(pathDaAplicacao);
        if (configuracao == null || usuarioDB == null || cliente == null) {
            session.setAttribute("isConfigManager", "false");
        }
        if (!FormataValor.strToBoolean((String) session.getAttribute("isConfigManager"))) {

            ConfigManager configManager = ConfigManager.getConfigManager();
            nomeDB      = configManager.getConfig("sistema.nomeDB");
            usuarioDB   = configManager.getConfig("sistema.usuarioDB");
            nomeDBReal  = configManager.getConfig("sistema.nomeDBReal");
            autocommit  = FormataValor.strToBoolean(configManager.getConfig("sistema.autocommit"));
            formatoData = configManager.getConfig("sistema.formatoData");
            ambiente    = configManager.getConfig("sistema.ambiente");
            localApps   = configManager.getConfig("sistema.localApps");
            versao      = configManager.getConfig("sistema.versao");
            cliente     = configManager.getConfig("sistema.cliente");
            dataVersao  = configManager.getConfig("sistema.dataVersao");
            repositorio = configManager.getConfig("sistema.repositorio");
            setConfiguracao(usuarioDB,this);

            configuracao = (Padrao) application.getAttribute("configuracoesAplicacao");
            if (configuracao == null) {

                setConfiguracao(usuarioDB, this);
                application.setAttribute("configuracoesAplicacao", configuracao);
            }
            session.setAttribute("isConfigManager", "true");
        }
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setNomeDB(String nomeDB) {
        this.nomeDB = nomeDB;
    }

    public void setUsuarioDB(String usuarioDB) {
        this.usuarioDB = usuarioDB;
    }

    public void setNomeDBReal(String nomeDBReal) {
        this.nomeDBReal = nomeDBReal;
    }

    public void setFormatoData(String formato) {
        this.formatoData = formato;
    }

    public void setAmbiente(String ambiente) {
        this.ambiente = ambiente;
    }

    public void setLocalApps(String localApps) {
        this.localApps = localApps;
    }

    public void setVersao(String versao) {
        this.versao = versao;
    }

    public void setUnidade(String unidade) {
        this.unidade = unidade.toUpperCase();
    }

    public void setOperador(String operador) {
        this.operador = operador.toUpperCase();
    }

    public int setLogin(String[] parametros) {
        super.setUsuarioDB(nomeDB, usuarioDB);
        int resultadoLogin = checkLogin(parametros);
        if (resultadoLogin == 0) {
            setPermissoes();
        }
        return resultadoLogin;
    }

    public void setCodprocesso(String pesq_cod_processo) {

        this.pesq_cod_processo = pesq_cod_processo;
    }

    public String getCodprocesso() {
        return pesq_cod_processo;
    }
    /**
     * Insere um precatório no Banco
     * @param precatorio precatório a ser inserido
     * @return  true - se conseguiu
     *          false - se não conseguiu
     */
    //Comentado, pois SulAmérica não tem Precatório
    public boolean inserePrecatorio(Precatorio precatorio) {
//        try {
//            String msgLog = "";
//            String modulo = "";
//            if (precatorio.insere(this.formatoData)) {
//                msgLog =
//                    "INSERIU O PRECATORIO "
//                        + precatorio.getNumeroInternoPrecatorio()
//                        + " NO PROCESSO "
//                        + precatorio.getNumeroAcaoOrig();
//                modulo = "PRECATORIOS";
//                Log log = new Log(nomeDB, usuarioDB, formatoData);
//                log.geraLog(codUsuario, idRegional, ip, funcao, msgLog, modulo);
//                return true;
//            } else {
//                return false;
//            }
//        } catch (Exception ex) {
//            Executa executa = new Executa();
//            executa.log("Excluir -  : " + ex.toString());
//            return false;
//        }
    	return false;
    }
    public String getSiglaDepartamento(int idDepartamento) {
    	Connection con = Conexao.getInstance().getConnection();
        String sql =
            "SELECT SIGLA_DPTO FROM "
                + usuarioDB
                + "DEPARTAMENTO WHERE COD_DPTO = "
                + idDepartamento;
        String sigla = "";
		Statement stmt = null;
        ResultSet rs = null;

        try {
        	stmt = con.createStatement();
            rs = stmt.executeQuery(sql);
            if (rs.next()) {
                sigla = rs.getString("SIGLA_DPTO");
            }
        } catch (SQLException e) {
        	GeraLog.error("Erro em process_jsp.Executa.getSiglaDepartamento(): \nSQL : " + sql, e);
		} finally {
			Conexao.fecharConexao(con, stmt, rs);
		}

        return sigla;

    }
	public synchronized int getProximaPasta(String sigla, String ano){
		return getProximaPasta("", sigla,ano);
	}

    public synchronized int getProximaPasta(String tpProcesso, String sigla, String ano){

    	Connection con = Conexao.getInstance().getConnection("Em Executa.getProximaPasta() : linha 249");

        String sql =
            "SELECT CONTADOR, ANO FROM "
                + usuarioDB
                + "GERADOR_PASTA WHERE BASE = '"
                + sigla
                + "' AND ANO = '"
                + ano + "'";

        int sequencial = 0;
        int anoAtual = FormataValor.strToInt(ano);
        boolean update = false;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.createStatement();
            rs = stmt.executeQuery(sql);

            if (rs.next()) {
                sequencial = FormataValor.strToInt(rs.getString("CONTADOR"));
                sequencial++;
				if("processoAdm".equals(tpProcesso)){
	                update = setUltimaPastaUsada(con,sequencial, anoAtual, sigla, true);
				}
            } else {
            	sequencial = 1;
            	//Testa o tipo do processo, pois processo administrativo 
            	//não tem trigger para fazer insert no contadotr de pasta
            	if("processoAdm".equals(tpProcesso)){
            		update = setUltimaPastaUsada(con, sequencial, anoAtual, sigla, false);
            	}
            }
        } catch (SQLException se) {
            log("EXECUTA.getProximaPasta  = " + se.toString()+ " - sql: "+sql);
        } finally {
            Conexao.fecharConexao(con,stmt,rs);
        }
        return sequencial;

    }
    
	private synchronized boolean setUltimaPastaUsada(int sequencial,
											int ano,
											String base,
											boolean tipoQuery){
		Connection con = null ;	
		boolean update = false;											
		try{
			con = Conexao.getInstance().getConnection("em Executa.setUltimaPastaUsada() : linha 292");
			update  = setUltimaPastaUsada(con, sequencial, ano, base, tipoQuery);
		}catch(Exception e){
			GeraLog.error("Erro em process_jsp.Executa.setUltimaPastaUsada(): \n : ", e);
		}finally{
			Conexao.fecharConexao(con);
		}
		return update;
	}
    
    private synchronized boolean setUltimaPastaUsada(Connection con,
							        int sequencial,
							        int ano,
							        String base,
							        boolean tipoQuery) {

		Statement stmt = null;
        //se tipo query true: fazer Update, false: fazer insert
        boolean update = false;
        String sql = "";
        if (tipoQuery == true) {
            sql =
                "UPDATE "
                    + usuarioDB
                    + "GERADOR_PASTA SET CONTADOR = "
                    + sequencial
                    + " WHERE BASE ='"
                    + base
                    + "' AND ANO ="
                    + ano;
        } else {
            sql =
                "INSERT INTO "
                    + usuarioDB
                    + "GERADOR_PASTA(BASE,ANO,CONTADOR) VALUES ('"
                    + base
                    + "', "
                    + ano
                    + ","
                    + sequencial
                    + ")";
        }
        try {
        	stmt = con.createStatement();
            if (stmt.executeUpdate(sql) > 0) {
                update = true;
            }
        } catch (SQLException e) {
        	GeraLog.error("Erro em process_jsp.Executa.setUltimaPastaUsada(): \nSQL : " + sql, e);
		} finally {
			Conexao.fecharConexao(stmt);
		}
        return update;
    }

    /**
     *
     * @param precatorio
     * @return boolean
     */
    //Comentado porque SulAmérica não tem Precatório.
    public boolean deletePrecatorio(Precatorio precatorio) {
//        String msgLog = "";
//        String modulo = "";
//        try {
//            if (precatorio.deletePrecatorio()) {
//                msgLog =
//                    "EXCLUIU O PRECATORIO "
//                        + precatorio.getNumeroInternoPrecatorio();
//                modulo = "PRECATORIOS";
//                Log log = new Log(nomeDB, usuarioDB, formatoData);
//                log.geraLog(codUsuario, idRegional, ip, funcao, msgLog, modulo);
//                return true;
//            } else {
//                return false;
//            }
//        } catch (Exception ex) {
//            log("EXECUTA.deletePrecatorio - rs = " + ex.toString());
//            return false;
//        }
    	return false;
    }
    /**
     * Altera as informações do precatório no Banco
     * @param precatorio precatório a ser atualizado
     * @return  true - se conseguiu
     *          false - se não conseguiu
     */
    //Comentado porque SulAmérica não tem Precatório.
    public boolean alteraPrecatorio(Precatorio precatorio) {
//        String msgLog = "";
//        String modulo = "";
//        try {
//            if (precatorio.altera(this.formatoData)) {
//                msgLog =
//                    "ALTEROU O PRECATORIO "
//                        + precatorio.getNumeroInternoPrecatorio()
//                        + " NO PROCESSO "
//                        + precatorio.getNumeroAcaoOrig();
//                modulo = "PRECATORIOS";
//                Log log = new Log(nomeDB, usuarioDB, formatoData);
//                log.geraLog(codUsuario, idRegional, ip, funcao, msgLog, modulo);
//                return true;
//            } else {
//                return false;
//            }
//        } catch (Exception ex) {
//            log("EXECUTA.alteraPrecatorio  = " + ex.toString());
//            return false;
//        }
    	return false;
    }
    /**
     * Insere um precatório complementar
     * @param precatorio precatorio a ser inserido
     * @return  true - se conseguiu
     *          false - se não conseguiu
     */
    //Comentado porque SulAmérica não tem Precatório.
    public boolean insereComplementar(Precatorio precatorio) {
//        String msgLog = "";
//        String modulo = "";
//        try {
//            if (precatorio.insere(this.formatoData)) {
//                msgLog =
//                    "INSERIU O PRECATORIO COMPLEMENTAR "
//                        + precatorio.getNumeroInternoPrecatorio()
//                        + " NO PRECATORIO "
//                        + precatorio.getNumeroInternoPai(Integer.parseInt(precatorio.getIdPai()));
//                modulo = "PRECATORIOS";
//                Log log = new Log(nomeDB, usuarioDB, formatoData);
//                log.geraLog(codUsuario, idRegional, ip, funcao, msgLog, modulo);
//                return true;
//            } else {
//                return false;
//            }
//        } catch (Exception ex) {
//            log("EXECUTA.insereComplementar = " + ex.toString());
//            return false;
//        }
    	return false;
    }

    public void envia(String funcao, String[] parametros) {
        this.funcao = funcao;
        String msgLog = "";
        String modulo = "";
        switch (Integer.parseInt(funcao)) {
            case 5 :
                retorno = setLogin(parametros);
				msgLog =
					"O USUÁRIO "
						+ parametros[0]
						+ " EFETUOU O LOGIN COM SUCESSO.";
                break;

            case 105 :
                Excluir procDel = new Excluir(usuarioDB);
                retorno =
                    procDel.executa(
                        "PROCESSO",
                        "COD_PROCESSO",
                        parametros[0]);
                procDel.executa(
                    "UltimoAndamento",
                    "ID_PROCESSO",
                    parametros[0]);
                procDel.executa(
                    "Andamento_Internet",
                    "ID_PROCESSO",
                    parametros[0]);
                procDel.executa(
                    "Andamento_Internet1",
                    "ID_PROCESSO",
                    parametros[0]);
                procDel.executa("Netjur", "ID_PROCESSO", parametros[0]);
                procDel.executa(
                    "Observacao",
                    "ID_PROCESSO",
                    parametros[0]);
                procDel.executa("Apenso", "ID_PROCESSO", parametros[0]);
                procDel.executa(
                    "processo_Preposto",
                    "ID_PROCESSO",
                    parametros[0]);
                procDel.executa(
                    "Litispendente",
                    "ID_PROCESSO",
                    parametros[0]);
                procDel.executa(
                    "Carta_Precatoria",
                    "ID_PROCESSO",
                    parametros[0]);
                procDel.executa(
                    "Processo_Partic_Vinc",
                    "ID_PROCESSO",
                    parametros[0]);
                procDel.executa(
                    "Processo_Pedido",
                    "ID_PROCESSO",
                    parametros[0]);
                procDel.executa("Andamento", "ID_PROCESSO", parametros[0]);
                procDel.executa("Agenda", "ID_PROCESSO", parametros[0]);
                procDel.executa(
                    "Lancamento_Conta",
                    "ID_PROCESSO",
                    parametros[0]);
                procDel.executa("Risco", "ID_PROCESSO", parametros[0]);
                procDel.executa(
                    "historico_Risco",
                    "ID_PROCESSO",
                    parametros[0]);
                procDel.executa("Peticao", "ID_PROCESSO", parametros[0]);
                procDel.executa(
                    "Estrategia",
                    "ID_PROCESSO",
                    parametros[0]);
                procDel.executa("Sentenca", "ID_PROCESSO", parametros[0]);
                procDel.executa(
                    "Processo_Tribunal",
                    "ID_PROCESSO",
                    parametros[0]);
                procDel.executa(
                    "Processo_Penhora",
                    "ID_PROCESSO",
                    parametros[0]);
                procDel.executa("Caminho", "ID_PROCESSO", parametros[0]);
                procDel.executa(
                    "Testemunha",
                    "ID_PROCESSO",
                    parametros[0]);
                procDel.executa("Pericia", "ID_PROCESSO", parametros[0]);
                procDel.executa(
                    "Processo_Advogado",
                    "ID_PROCESSO",
                    parametros[0]);
                procDel.executa(
                    "Processo_Tributo",
                    "ID_PROCESSO",
                    parametros[0]);
                procDel.executa(
                    "Processo_pedido",
                    "ID_PROCESSO",
                    parametros[0]);
                procDel.executa("PROCESSO", "COD_PROCESSO", parametros[0]);
                msgLog = "EXCLUIU O PROCESSO" + parametros[1];
                modulo = "feitos";
                break;

            case 107 :
                Excluir cartaDel = new Excluir(usuarioDB);
                retorno =
                    cartaDel.executa(
                        "CARTA_PRECATORIA",
                        "COD_CARTA",
                        parametros[0]);
                msgLog =
                    "EXCLUIU A CARTA PRECATORIA "
                        + parametros[1]
                        + " NO PROCESSO "
                        + getNumProcesso(parametros[2]);
                modulo = "feitos";
                break;
            case 108 :
                Excluir contaDel = new Excluir(usuarioDB);
                retorno =
                    contaDel.executa(
                        "LANCAMENTO_CONTA",
                        "COD_LANCAMENTO",
                        parametros[0]);
                msgLog =
                    "EXCLUIU O LANCAMENTO CONTA "
                        + parametros[1]
                        + " NO PROCESSO "
                        + getNumProcesso(parametros[2]);
                modulo = "feitos";
                break;
            case 109 :
                Excluir peticDel = new Excluir(usuarioDB);
                retorno =
                    peticDel.executa(
                        "PETICAO",
                        "COD_PETICAO",
                        parametros[0]);
                msgLog =
                    "EXCLUIU A PETICAO "
                        + parametros[1]
                        + " NO PROCESSO "
                        + getNumProcesso(parametros[2]);
                modulo = "feitos";
                break;
            case 110 :
                Excluir ageDel = new Excluir(usuarioDB);
                retorno =
                    ageDel.executa("AGENDA", "COD_AGENDA", parametros[0]);
                msgLog =
                    "EXCLUIU UM COMPROMISSO "
                        + parametros[0]
                        + " NO PROCESSO "
                        + getNumProcesso(parametros[0]);
                modulo = "feitos";
                break;
            case 111 :
                Excluir testDel = new Excluir(usuarioDB);
                retorno =
                    testDel.executa(
                        "TESTEMUNHA",
                        "COD_TESTEMUNHA",
                        parametros[0]);
                msgLog =
                    "EXCLUIU A TESTEMUNHA "
                        + parametros[1]
                        + " NO PROCESSO "
                        + getNumProcesso(parametros[2]);
                modulo = "feitos";
                break;
            case 112 :
                Excluir periciaDel = new Excluir(usuarioDB);
                retorno =
                    periciaDel.executa(
                        "PERICIA",
                        "COD_PERICIA",
                        parametros[0]);
                msgLog =
                    "EXCLUIU A PERICIA "
                        + parametros[1]
                        + " NO PROCESSO "
                        + getNumProcesso(parametros[2]);
                modulo = "feitos";
                break;
            case 114 :
                Recurso recursoDel = new Recurso(nomeDB, usuarioDB);
                //////// ("Parametro[0]: "+parametros[0]);
                retorno = recursoDel.delete(parametros);
                msgLog =
                    "EXCLUIU O RECURSO "
                        + parametros[1]
                        + " NO PROCESSO "
                        + getNumProcesso(parametros[2]);
                modulo = "feitos";
                break;
            case 125 :
                //Excluir arquivoDel = new Excluir(usuarioDB);
                Arquivos arquivoDel = new Arquivos(nomeDB, usuarioDB);
                retorno = arquivoDel.delete(parametros);
                msgLog =
                    "DELETOU O ARQUIVO "
                        + parametros[2]
                        + " DO PROCESSO N°"
                        + parametros[4];
                modulo = parametros[3];

                //////// ("DEPOIS DO IFmodulo="+modulo);
                break;
            case 127 :
                Excluir jurispDel = new Excluir(usuarioDB);
                retorno =
                    jurispDel.executa(
                        "JURISPRUDENCIA",
                        "COD_JURIP",
                        parametros[0]);
                msgLog = "DELETOU A JURISPRUDENCIA " + parametros[1];
                modulo = "feitos";
                break;
            case 130 :
                Excluir autorDel = new Excluir(usuarioDB);
                retorno =
                    autorDel.executa(
                        "PRECATORIO_AUTORCREDOR",
                        "ID_PARTICIPANTE",
                        parametros[0]);
                msgLog =
                    "EXCLUIU O AUTOR CREDOR "
                        + parametros[0]
                        + " NO PRECATORIO "
                        + getNumProcesso(parametros[1]);
                modulo = "PRECATORIO";
                break;
            case 205 :
                Processo proceAdd = new Processo(this, idEscritorio);
                retorno = proceAdd.insere(parametros);
                if (retorno == 0) {
                    if (parametros.length > 19) {
                        parametros[19] =
                            String.valueOf(proceAdd.getCodProcesso());
                        ////// ("parametros[19] = " + parametros[19]);
                    }
                    respostaArray = proceAdd.getCodprocesso(parametros);
                    int idProcessoAdm = FormataValor.strToInt(parametros[21]);
                    if (idProcessoAdm > 0) {
                        proceAdd.vincularProcessos(
                            idProcessoAdm,
                            FormataValor.strToInt(respostaArray[0]));
                    }
                }
                msgLog = "INSERIU O PROCESSO " + parametros[0];
                modulo = "feitos";
                break;
            case 207 :
                CartaPrecatoria cartaAdd =
                    new CartaPrecatoria(nomeDB, usuarioDB);
                retorno = cartaAdd.insere(parametros);
                msgLog =
                    "INSERIU A CARTA PRECATORIA "
                        + parametros[4]
                        + " NO PROCESSO "
                        + getNumProcesso(parametros[0]);
                modulo = "feitos";
                break;
            case 208 :
                Conta contaAdd = new Conta(nomeDB, usuarioDB);
                retorno = contaAdd.insere(parametros);
                msgLog =
                    "INSERIU CONTA "
                        + parametros[8]
                        + " NO PROCESSO "
                        + getNumProcesso(parametros[0]);
                modulo = "feitos";
                break;
            case 209 :
                Peticao peticaoAdd = new Peticao(nomeDB, usuarioDB);
                retorno = peticaoAdd.insere(parametros);
                msgLog =
                    "INSERIU PETICAO "
                        + parametros[2]
                        + " NO PROCESSO "
                        + getNumProcesso(parametros[0]);
                modulo = "feitos";
                break;
            case 210 :
                Agenda ageAdd = new Agenda(this);
                retorno = ageAdd.insere(parametros);
                msgLog =
                    "INSERIU NO PRAZO "
                        + parametros[0]
                        + " NO PROCESSO "
                        + getNumProcesso(parametros[0]);
                modulo = "feitos";
                break;
            case 211 :
                Testemunha testAdd = new Testemunha(nomeDB, usuarioDB);
                retorno = testAdd.insere(parametros);
                msgLog =
                    "INSERIU A TESTEMUNHA "
                        + parametros[0]
                        + " NO PROCESSO "
                        + getNumProcesso(parametros[5]);
                modulo = "feitos";
                break;
            case 212 :
                Pericia periciaAdd = new Pericia(nomeDB, usuarioDB);
                retorno = periciaAdd.insere(parametros);
                msgLog =
                    "INSERIU PERICIA "
                        + parametros[4]
                        + " NO PROCESSO "
                        + getNumProcesso(parametros[0]);
                modulo = "feitos";
                break;
            case 214 :
                Recurso recursoAdd = new Recurso(nomeDB, usuarioDB);
                retorno = recursoAdd.insere(parametros);
                msgLog =
                    "INSERIU O RECURSO "
                        + parametros[4]
                        + " NO PROCESSO "
                        + getNumProcesso(parametros[0]);
                modulo = "feitos";
                break;
            case 225 :
                Arquivos arqAdd = new Arquivos(nomeDB, usuarioDB);
                retorno = arqAdd.insere(parametros);
                msgLog =
                    "INSERIU UM CAMINHO DO DOCUMENTO NA TELA DE "
                        + parametros[3]
                        + " COM A DESCRIÇÃO: "
                        + parametros[1];
                modulo = parametros[4];
                break;
            case 227 :
                Jurisprudencia jurisAdd =
                    new Jurisprudencia(nomeDB, usuarioDB);
                retorno = jurisAdd.insere(parametros);
                msgLog = "INSERIU A JURISPRUDENCIA " + parametros[0];
                modulo = "feitos";
                break;
            case 250 :
                Sentenca sentAdd = new Sentenca(nomeDB, usuarioDB);
                retorno = sentAdd.insere(parametros);
                modulo = "feitos";
                break;
            case 307 :
                CartaPrecatoria cartaUpd =
                    new CartaPrecatoria(nomeDB, usuarioDB);
                cartaUpd.altera(parametros);
                retorno = 0;
                msgLog =
                    "ALTEROU A CARTA PRECATORIA "
                        + parametros[3]
                        + " NO PROCESSO "
                        + getNumProcesso(parametros[9]);
                modulo = "feitos";
                break;
            case 308 :
                Conta contaUpd = new Conta(nomeDB, usuarioDB);
                contaUpd.altera(parametros);
                retorno = 0;
                msgLog =
                    "ALTEROU A CONTA "
                        + parametros[7]
                        + " NO PROCESSO "
                        + getNumProcesso(parametros[9]);
                modulo = "feitos";
                break;
            case 309 :
                Peticao peticaoUpd = new Peticao(nomeDB, usuarioDB);
                peticaoUpd.altera(parametros);
                retorno = 0;
                msgLog =
                    "ALTEROU A PETICAO DE CONTEÚDO "
                        + parametros[3]
                        + " NO PROCESSO "
                        + getNumProcesso(parametros[0]);
                modulo = "feitos";
                break;
            case 310 :
                Agenda addUpd = new Agenda(this);
                addUpd.altera(parametros);
                retorno = 0;
                msgLog =
                    "ALTEROU A AGENDA "
                        + parametros[2]
                        + " NO PROCESSO "
                        + getNumProcesso(parametros[6]);
                modulo = "feitos";
                break;
            case 311 :
                Testemunha testUpd = new Testemunha(nomeDB, usuarioDB);
                testUpd.altera(parametros);
                retorno = 0;
                msgLog =
                    "ALTEROU A TESTEMUNHA "
                        + parametros[1]
                        + " NO PROCESSO "
                        + getNumProcesso(parametros[6]);
                modulo = "feitos";
                break;
            case 312 :
                Pericia periciaUpd = new Pericia(nomeDB, usuarioDB);
                periciaUpd.altera(parametros);
                retorno = 0;
                msgLog =
                    "ALTEROU A PERICIA "
                        + parametros[3]
                        + " NO PROCESSO "
                        + getNumProcesso(parametros[7]);
                modulo = "feitos";
                break;
            case 314 :
                Recurso recursoUpd = new Recurso(nomeDB, usuarioDB);
                recursoUpd.altera(parametros);
                retorno = 0;
                msgLog =
                    "ALTEROU O RECURSO "
                        + parametros[4]
                        + " NO PROCESSO "
                        + getNumProcesso(parametros[0]);
                modulo = "feitos";
                break;
            case 327 :
                //////// ("Entrei jurisprudencia executa");
                Jurisprudencia jurisUpd =
                    new Jurisprudencia(nomeDB, usuarioDB);
                jurisUpd.altera(parametros);
                msgLog =
                    "ALTEROU A JURISPRUDENCIA COM MATÉRIA: " + parametros[6];
                modulo = "feitos";
                break;
            case 333 :
                Sentenca sentencaUpd = new Sentenca(nomeDB, usuarioDB);
                sentencaUpd.altera(parametros);
                msgLog = "ALTEROU A SENTENCA " + parametros[2];
                modulo = "feitos";
                break;
            case 334 :
                //////// ("Entrei jurisprudencia executa");
                Ementa ementaUpd = new Ementa(nomeDB, usuarioDB);
                ementaUpd.altera(parametros);
                msgLog = "ALTEROU A EMENTA " + parametros[0];
                modulo = "feitos";
                break;
            case 400 :
                Agenda comproSel1 = new Agenda(this);
                resposta = comproSel1.seleciona(1, codUsuario);
                retorno = 0;
                msgLog = "";
                modulo = "feitos";
                break;
            case 402 :
                Agenda comproSel2 = new Agenda(this);
                resposta = comproSel2.seleciona(2, codUsuario);
                retorno = 0;
                msgLog = "";
                modulo = "feitos";
                break;
            case 408 :
                Arquivos arqSel = new Arquivos(nomeDB, usuarioDB);
                resposta = arqSel.seleciona(1, parametros);
                retorno = 0;
                msgLog = "";
                modulo = "feitos";
                break;

            case 410 :
                SelectGenerico seleciona = new SelectGenerico(this);
                //////// ("dentro do executa"+parametros[0]+"-"+parametros[1]+"-"+parametros[2]);
                resposta = seleciona.seleciona(1, parametros);
                retorno = 0;
                msgLog = "";
                break;

            case 412 :
                Conta contaSel = new Conta(nomeDB, usuarioDB);
                resposta = contaSel.seleciona(1, parametros);
                retorno = 0;
                msgLog = "";
                break;
            case 414 :
                Ementa ementaSel1 = new Ementa(nomeDB, usuarioDB);
                resposta = ementaSel1.seleciona(2, parametros);
                retorno = 0;
                msgLog = "";
                break;

            case 416 :
                Ementa ementaInsertNada = new Ementa(nomeDB, usuarioDB);
                retorno = ementaInsertNada.insereNada(parametros);
                msgLog = "INSERIU EMENTA " + parametros[0];
                break;

            case 418 :
                Pericia periciaSel1 = new Pericia(nomeDB, usuarioDB);
                resposta = periciaSel1.seleciona(2, parametros);
                retorno = 0;
                msgLog = "";
                break;

            case 419 :
                Pericia periciaSel = new Pericia(nomeDB, usuarioDB);
                resposta = periciaSel.seleciona(1, parametros);
                retorno = 0;
                msgLog = "";
                break;

            case 421 :
                Peticao peticaoSel = new Peticao(nomeDB, usuarioDB);
                resposta = peticaoSel.seleciona(1, parametros);
                retorno = 0;
                msgLog = "";
                break;

            case 437 :
                Sentenca decisaoSel = new Sentenca(nomeDB, usuarioDB);
                resposta = decisaoSel.seleciona(1, parametros);
                retorno = 0;
                msgLog = "";
                break;

            case 438 :
                Testemunha testeSel = new Testemunha(nomeDB, usuarioDB);
                resposta = testeSel.seleciona(1, parametros);
                retorno = 0;
                msgLog = "";
                break;
            case 440 :
                Sentenca sentencaInsertNada =
                    new Sentenca(nomeDB, usuarioDB);
                retorno = sentencaInsertNada.insereNada(parametros);
                break;

            case 447 :
                Recurso recursoSel = new Recurso(nomeDB, usuarioDB);
                resposta = recursoSel.seleciona(1, parametros);
                retorno = 0;
                msgLog = "";
                break;
            case 450 :
                Sentenca decSel = new Sentenca(nomeDB, usuarioDB);
                resposta = decSel.decisaoSel(1, parametros);
                retorno = 0;
                msgLog = "";
                break;

            case 451 :
                Sentenca sentencaSel = new Sentenca(nomeDB, usuarioDB);
                resposta = sentencaSel.selecionaSentenca(1, parametros);
                retorno = 0;
                msgLog = "";
                break;

                
            default :
                retorno = 63;
        }

        if (geraLog == 1 && retorno == 0 && !msgLog.equals("")) {
            Log log = new Log(nomeDB, usuarioDB, formatoData);
            log.geraLog(codUsuario, idRegional, ip, funcao, msgLog, modulo.toUpperCase(), getIdProcessoLog(), getPastaLog());
        }
    }

    //Setters and Getters para auxiliar o LOG que será gravado na LOGTAB
    public void setPastaLog(String pastaLog){
    	this.pastaLog = pastaLog;
    }
    public String getPastaLog(){
    	return this.pastaLog;
    }
    public void setIdProcessoLog(int idProcessoLog){
    	this.idProcessoLog = idProcessoLog;
    }
    public int getIdProcessoLog(){
    	return this.idProcessoLog;
    }
//	public void envia(String funcao, String[] parametros) {
//		envia(funcao, parametros);
//	}

    public void envia(String funcao) {
        String[] parametros = {
        };
        envia(funcao, parametros);
    }

//	public void envia( String funcao) {
//		envia(funcao, con);
//	}

    public boolean geraLog(String tabela, String mensagem) {
        Log gLog = new Log(nomeDB, usuarioDB, formatoData);
        return gLog.geraLog(codUsuario, idRegional, ip, "", mensagem, tabela.toUpperCase(),
        		getIdProcessoLog(),getPastaLog());
    }

    public boolean geraLog(Connection con, String tabela, String mensagem) {
        Log gLog = new Log(nomeDB, usuarioDB, formatoData);
        return gLog.geraLog(con,codUsuario, idRegional, ip, "", mensagem, tabela.toUpperCase(),
        		getIdProcessoLog(),getPastaLog());
    }

//	public boolean geraLog(String tabela, String mensagem) {
//		return geraLog(tabela, mensagem);
//	}

    public String getNumProcesso(String codProcesso) {
        Hashtable numTipoProcesso = getNumTipoProcesso(codProcesso);
        String numProcesso =
            FormataValor.strNull(
                (String) numTipoProcesso.get("numeroProcesso"));
        return numProcesso;
    }

    public Hashtable getNumTipoProcesso(String codProcesso) {
    	Connection con = Conexao.getInstance().getConnection();
    	Statement stmt = null;
    	ResultSet rs = null;

        Hashtable numTipoProcesso = new Hashtable();
        String sql = "SELECT NUM_PROCESSO, TIPO_PROCESSO "
                + "FROM "
                + usuarioDB
                + "PROCESSO "
                + "WHERE COD_PROCESSO = "
                + codProcesso;

        try {
			stmt = con.createStatement();
			rs = stmt.executeQuery(sql);
            if (rs.next()) {
                numTipoProcesso.put(
                    "numeroProcesso",
                    rs.getString("NUM_PROCESSO"));
                numTipoProcesso.put(
                    "tipoProcesso",
                    rs.getString("TIPO_PROCESSO"));
            }
        } catch (SQLException e) {
        	GeraLog.error("Erro em process_jsp.Executa.getNumTipoProcesso(): " +
        			"\nSQL : " + sql, e);
		} finally {
			Conexao.fecharConexao(con, stmt, rs);
		}
        return numTipoProcesso;
    }
    // Este metodo serve para procura do numero do processo no banco
    public String procuraProcessoExistente(
								        String numProcesso,
								        String numTribunal) {
    	Connection con = Conexao.getInstance().getConnection();
		Statement stmt = null;
        ResultSet rs = null;

        String sql =
            "SELECT COD_PROCESSO FROM "
                + usuarioDB
                + "PROCESSO WHERE NUM_PROCESSO = '"
                + numProcesso
                + "' AND ID_TRIBUNAL = "
                + numTribunal;
        String resul = "";

        try {
        	stmt = con.createStatement();
            rs = stmt.executeQuery(sql);
            if (rs.next()) {
                resul = rs.getString("COD_PROCESSO");
            }

        } catch (SQLException e) {
        	GeraLog.error("Erro em process_jsp.Executa.procuraProcessoExistente(): \nSQL : " + sql, e);
		} finally {
			Conexao.fecharConexao(con, stmt, rs);
		}
        return resul;
    }
    /**
     * Procura se já existe uma pasta com o mesmo número da pasta gerada na hora de inserir
     * um novo processo. Se existir uma pasta igual a que foi gerada o método retorna true.
     * @param pasta - é a string gerada antes de inserir um novo processo na página insere.jsp.
     * @return Se existir uma pasta igual a que foi gerada o método retorna true
     */
    public boolean procuraPastaExistente(String pasta) {
    	Connection con = Conexao.getInstance().getConnection();
    	Statement stmt = null;
    	ResultSet rs = null;

    	boolean resultado = false;
    	int qtdPasta = 0;

    	String sql =
    		"SELECT COUNT(1) FROM "
    		+ usuarioDB
    		+ "PROCESSO WHERE PASTA = '"+pasta+"'";

    	try {
    		stmt = con.createStatement();
    		rs = stmt.executeQuery(sql);
    		if (rs.next()) {
    			qtdPasta = rs.getInt(1);
    		}
	    	if(qtdPasta > 0){
	    		resultado = true;
	    	}

    	} catch (SQLException e) {
    		GeraLog.error("Erro em process_jsp.Executa.procuraPastaExistente(): \nSQL : " + sql, e);
    	} finally {
    		Conexao.fecharConexao(con, stmt, rs);
    	}
    	return resultado;
    }

    public String getResposta() {
        return resposta;
    }

    public void setResposta(String resposta) {
        this.resposta = resposta;
    }

    public String[] getRespostaArray() {
        return respostaArray;
    }

    public void setRespostaArray(String[] respostaArray) {
        this.respostaArray = respostaArray;
    }

    public String getUnidade() {
        return unidade;
    }

    public String getOperador() {
        return operador;
    }

    public String getCabecalho() {
        String cabecalho =
            "<p style=\"text-align:right; text-indent:0; font-size:8pt\"><b>"
                + unidade.toLowerCase()
                + "."
                + operador.toLowerCase()
                + "</b><BR>"
                + FormataValor.getHoje()
                + " "
                + Data.getHora()
                + "</p>";
        return cabecalho;
    }


    public void log(String mensagemLog, boolean enviaEmail) {
        /*if (ambiente.equals("homo")) {
              //// ("Msg -> "+mensagem);
          }*/
        //original
        //this.log.debug("[" + configuracao.getNomeAplicacao() + "] -> " + mensagemLog);
        //alteraçao
        log.debug("[" + this.nomeUsuario + "] -> " + mensagemLog);

        /***ALTERACAO
                if(enviaEmail) {
                    Contatos contatos = new Contatos("PROCESSWEB", "suporte@startware.com.br");
                    //Contatos contatos = new Contatos("BCJUR", "bacen@startware.com.br");
                    contatos.setDestinatario("TO", "Startware", configuracao.getEmailLog());
                    contatos.setDestinatario("CC", "Startware", "julio@startware.com.br");
                    String mensagem	= nomeServidor + "<BR><BR>" +
                            "IP: " + ip + "<BR>" +
                            "Matrícula: " + matricula + "<BR>" +
                            "Nome: " + nomeUsuario + "<BR>" +
                            "Usuário: " + getUnidade() + "." + getOperador() + "<BR>" +
                            "Mensagem: " + mensagemLog + "<BR><BR>";

                    if(SendMail.enviar(contatos, configuracao.getSmtp(), "## ERRO PROCESSWEB ## - "+configuracao.getApelidoEmpresa() + " - "+ambiente, mensagem)) {
                        //if(SendMail.enviar(contatos, "mail.policentro.com.br", "## ERRO BCJUR ##", mensagem)) {
                        log("sendError - sucesso");
                    }
                    else {
                        log("sendError - falha");
                    }
                }
        ****/
    }

    public void log(String mensagem) {
        log(FormataValor.strNull(mensagem), false);
    }
    public void system(String mensagem) {
        if (!ambiente.equals("prod")) {
            //// (mensagem);
        }
    }
    public void printStackTrace(Exception ex) {
        if (!ambiente.equals("prod")) {
            ex.printStackTrace();
        }
    }

    public boolean isPerfil(HttpServletRequest request, String perfil) {
        if (configuracao.isSegurancaDeclarativa()) {
            return request.isUserInRole(perfil);
        } else {
            return true;
        }
    }

    public boolean isPerfil(HttpServletRequest request, String[] perfil) {
        boolean acesso = false;
        if (perfil[0].equals("TIPO_USUARIO")) {
            if (tipoUsuario.equals(String.valueOf(ConstantesCampos.SULA_TIPO_ADM_JUR))) {
                acesso = true;
            } else {
                acesso = false;
            }
        } else {
            for (int i = 0; i < perfil.length; i++) {
                //log(perfil[i]);
                if (!acesso) {
                    acesso = isPerfil(request, perfil[i]);
                }
            }
        }
        return acesso;
    }

    public Perfil isPerfil(String tabela, int codTab) {
        return isPerfil(tabela + codTab);
    }

    public Perfil isPerfil(String tabela) {

        if (perfis == null) {
            perfis = PerfilDAO.getPerfis(this);
        }

        ////// ("CHAVES: "+perfis.keySet());

        Perfil perfil = (Perfil) perfis.get(tabela);

        return perfil == null ? new Perfil() : perfil;
    }

    public void sendError(Exception ex, String sql) {
        /* desativado temporariamente 20:03 10/7/2006
          sql = FormataValor.strNull(sql);
          Contatos contatos = new Contatos("PROCESSWEB", "suporte@startware.com.br");
          //Contatos contatos = new Contatos("BCJUR", "bacen@startware.com.br");
          contatos.setDestinatario("TO", "Startware", configuracao.getEmailLog());
          contatos.setDestinatario("CC", "Startware", "desenvolvimento@startware.com.br");

          String mensagem	= nomeServidor + "<BR><BR>" +
                  "IP: " + ip + "<BR>" +
                  "Matrícula: " + matricula + "<BR>" +
                  "Nome: " + nomeUsuario + "<BR>" +
                  "Usuário: " + getUnidade() + "." + getOperador() + "<BR>" +
                  "Sql: "+sql+"<BR>"+
                  "Erro: " + getStackTrace(ex)+ "<BR><BR>";

          if(SendMail.enviar(contatos, configuracao.getSmtp(),"## ERRO PROCESSWEB ## - "+configuracao.getApelidoEmpresa() + " - "+ambiente, mensagem)) {
              log("sendError - sucesso");
          }
          else {
              log("sendError - falha");
          }*/ //Reativar Posteriormente

        ////// ("sendError.sql = " + sql);
        //ex.printStackTrace();
        log("sql  [" + sql + "] = " + ex.toString());

    }

    public void sendError(
        String sql,
        Exception ex,
        HttpServletRequest request,
        HttpServletResponse response,
        ServletConfig config) {
        /* Desativado temporariamente 20:03 10/7/2006
                  String pathDaAplicacao	= config.getServletContext().getRealPath("");
          //		FileOutputStream os		= null;
          //		try {
          //			os = new FileOutputStream(pathDaAplicacao + "/WEB-INF/log.txt");
          //		}
          //		catch (FileNotFoundException e) {
          //			log(e.toString());
          //		}
          //		PrintWriter printer		= new PrintWriter(os,true);
          //		ex.printStackTrace(printer);

                  Contatos contatos = new Contatos("PROCESSWEB", "suporte@startware.com.br");
                  //Contatos contatos = new Contatos("BCJUR", "bacen@startware.com.br");
                  contatos.setDestinatario("TO", "Startware", configuracao.getEmailLog());
                  contatos.setDestinatario("CC", "Startware", "desenvolvimento@startware.com.br");
                  String mensagem	= nomeServidor + "\n\n" +
                          "IP: " + ip + "\n" +
                          "Matrícula: " + matricula + "\n" +
                          "Nome: " + nomeUsuario + "\n" +
                          "Usuário: " + getUnidade() + "." + getOperador() + "\n" +
                          "Arquivo: " + request.getRequestURI() + "\n\n";

                  //if(SendMail.enviar(contatos, configuracao.getSmtp(), "## ERRO BCJUR ##", mensagem, pathDaAplicacao + "/WEB-INF/log.txt")) {
                  if(SendMail.enviar(contatos, configuracao.getSmtp(),"## ERRO PROCESSWEB ## - "+configuracao.getApelidoEmpresa() + " - "+ambiente, mensagem, pathDaAplicacao + "/WEB-INF/log.txt")) {
                      log("sendError - sucesso");
                  }
                  else {
                      log("sendError - falha");
                  }

                  erro	= request.getRequestURI() + "<br>" + ex.toString(); // Mensagem na página de Erro.
                  try {
                      response.sendError(500);
                  }
                  catch (IOException e) {
                      log(e.toString());
                  }
          */ //Reativar Posteriormente.

        ////// (request.getRequestURL().toString());
        //ex.printStackTrace();
        log(
            "url ["
                + request.getRequestURL().toString()
                + "] = sql ["
                + sql
                + "] >> "
                + ex.toString());
        log.error("ERRO: ",ex);

    }

    public void sendError(
        Exception ex,
        HttpServletRequest request,
        HttpServletResponse response,
        ServletConfig config) {
        /* Desativado temporariamente 20:03 10/7/2006
                  String pathDaAplicacao	= config.getServletContext().getRealPath("");
          //		FileOutputStream os		= null;
          //		try {
          //			os = new FileOutputStream(pathDaAplicacao + "/WEB-INF/log.txt");
          //		}
          //		catch (FileNotFoundException e) {
          //			log(e.toString());
          //		}
          //		PrintWriter printer		= new PrintWriter(os,true);
          //		ex.printStackTrace(printer);

                  Contatos contatos = new Contatos("PROCESSWEB", "suporte@startware.com.br");
                  //Contatos contatos = new Contatos("BCJUR", "bacen@startware.com.br");
                  contatos.setDestinatario("TO", "Startware", configuracao.getEmailLog());
                  contatos.setDestinatario("CC", "Startware", "desenvolvimento@startware.com.br");
                  String mensagem	= nomeServidor + "\n\n" +
                          "IP: " + ip + "\n" +
                          "Matrícula: " + matricula + "\n" +
                          "Nome: " + nomeUsuario + "\n" +
                          "Usuário: " + getUnidade() + "." + getOperador() + "\n" +
                          "Arquivo: " + request.getRequestURI() + "\n\n";

                  //if(SendMail.enviar(contatos, configuracao.getSmtp(), "## ERRO BCJUR ##", mensagem, pathDaAplicacao + "/WEB-INF/log.txt")) {
                  if(SendMail.enviar(contatos, configuracao.getSmtp(),"## ERRO PROCESSWEB ## - "+configuracao.getApelidoEmpresa() + " - "+ambiente, mensagem, pathDaAplicacao + "/WEB-INF/log.txt")) {
                      log("sendError - sucesso");
                  }
                  else {
                      log("sendError - falha");
                  }

                  erro	= request.getRequestURI() + "<br>" + ex.toString(); // Mensagem na página de Erro.
                  try {
                      response.sendError(500);
                  }
                  catch (IOException e) {
                      log(e.toString());
                  }
          */ //Reativar Posteriormente.

        ////// (request.getRequestURL().toString());
        //ex.printStackTrace();
        log("url [" + request.getRequestURL().toString() + "] = " + ex);
        log.error("ERRO: ",ex);

    }

    public void sendError(
        NullPointerException ex,
        HttpServletRequest request,
        HttpServletResponse response,
        ServletConfig config) {
        /* Desativado temporariamente 20:03 10/7/2006
                  String pathDaAplicacao	= config.getServletContext().getRealPath("");
          //		FileOutputStream os		= null;
          //		try {
          //			os = new FileOutputStream(pathDaAplicacao + "/WEB-INF/log.txt");
          //		}
          //		catch (FileNotFoundException e) {
          //			log(e.toString());
          //		}
          //		PrintWriter printer		= new PrintWriter(os,true);
          //		ex.printStackTrace(printer);

                  Contatos contatos = new Contatos("PROCESSWEB", "suporte@startware.com.br");
                  //Contatos contatos = new Contatos("BCJUR", "bacen@startware.com.br");
                  contatos.setDestinatario("TO", "Startware", configuracao.getEmailLog());
                  contatos.setDestinatario("CC", "Startware", "desenvolvimento@startware.com.br");
                  String mensagem	= nomeServidor + "\n\n" +
                          "IP: " + ip + "\n" +
                          "Matrícula: " + matricula + "\n" +
                          "Nome: " + nomeUsuario + "\n" +
                          "Usuário: " + getUnidade() + "." + getOperador() + "\n" +
                          "Arquivo: " + request.getRequestURI() + "\n\n";

                  //if(SendMail.enviar(contatos, configuracao.getSmtp(), "## ERRO BCJUR ##", mensagem, pathDaAplicacao + "/WEB-INF/log.txt")) {
                  if(SendMail.enviar(contatos, configuracao.getSmtp(),"## ERRO PROCESSWEB ## - "+configuracao.getApelidoEmpresa() + " - "+ambiente, mensagem, pathDaAplicacao + "/WEB-INF/log.txt")) {
                      log("sendError - sucesso");
                  }
                  else {
                      log("sendError - falha");
                  }

                  erro	= request.getRequestURI() + "<br>" + ex.toString(); // Mensagem na página de Erro.
                  try {
                      response.sendError(500);
                  }
                  catch (IOException e) {
                      log(e.toString());
                  }
          */ //Reativar Posteriormente.

        ////// (request.getRequestURL().toString());
        //ex.printStackTrace();
        log(
            "url ["
                + request.getRequestURL().toString()
                + "] = "
                + ex.toString());

    }

    public void sendMessage(String aviso) {
        Contatos contatos = new Contatos("PROCESSWEB", "suporte@startware.com.br");
        contatos.setDestinatario("TO", "Startware", configuracao.getEmailLog());
        String mensagem =
            nomeServidor
                + "<BR><BR>"
                + "IP: "
                + ip
                + "<BR>"
                + "Matrícula: "
                + matricula
                + "<BR>"
                + "Nome: "
                + nomeUsuario
                + "<BR>"
                + "Usuário: "
                + getUnidade()
                + "."
                + getOperador()
                + "<BR>"
                + "Mensagem: "
                + aviso
                + "<BR><BR>";

        if (SendMail
            .enviar(
                contatos,
                "200.162.121.250",
                "## ERRO PROCESSWEB ## - "
                    + configuracao.getApelidoEmpresa()
                    + " - "
                    + ambiente,
                mensagem)) {
            log("sendError - sucesso");
        } else {
            log("sendError - falha");
        }
    }

    public Hashtable getInfoUsuario(String campo, String tipoPesquisa) {
    	Connection con = Conexao.getInstance().getConnection();
		Statement stmt = null;

        Hashtable infoUsuario = new Hashtable();
        String sql = "";
        if (tipoPesquisa.equals("matricula")) {
            sql =
                "SELECT U.NOME_USUARIO, L.BLOQUEADO, U.MAT_USUARIO, U.E_MAIL, L.LOGIN, L.SENHA, L.SENHAS_ANTIGAS "
                    + "FROM "
                    + usuarioDB
                    + "USUARIO U "
                    + "LEFT OUTER JOIN "
                    + usuarioDB
                    + "LOGIN L ON (U.COD_USUARIO = L.ID_USUARIO) "
                    + "WHERE UPPER(U.MAT_USUARIO) = '"
                    + campo.toUpperCase()
                    + "'";
        } else if (tipoPesquisa.equals("codigo")) {
            sql =
                "SELECT U.NOME_USUARIO, L.BLOQUEADO, U.MAT_USUARIO, U.E_MAIL, L.LOGIN, L.SENHA, L.SENHAS_ANTIGAS "
                    + "FROM "
                    + usuarioDB
                    + "USUARIO U "
                    + "LEFT OUTER JOIN "
                    + usuarioDB
                    + "LOGIN L ON (U.COD_USUARIO = L.ID_USUARIO) "
                    + "WHERE U.COD_USUARIO = "
                    + campo
                    + "";
        } else {
            sql =
                "SELECT U.NOME_USUARIO, L.BLOQUEADO, U.MAT_USUARIO, U.E_MAIL, L.LOGIN, L.SENHA, L.SENHAS_ANTIGAS "
                    + "FROM "
                    + usuarioDB
                    + "USUARIO U "
                    + "LEFT OUTER JOIN "
                    + usuarioDB
                    + "LOGIN L ON (U.COD_USUARIO = L.ID_USUARIO) "
                    + "WHERE UPPER(L.LOGIN) = '"
                    + campo.toUpperCase()
                    + "'";
        }

        ResultSet rsInfoUsuario = null;
        try {
        	stmt = con.createStatement();
            rsInfoUsuario = stmt.executeQuery(sql);
            if (rsInfoUsuario.next()) {
                infoUsuario.put("nomeUsuario", FormataValor.strNull(rsInfoUsuario.getString("NOME_USUARIO")));
                infoUsuario.put("matUsuario", FormataValor.strNull(rsInfoUsuario.getString("MAT_USUARIO")));
                infoUsuario.put("emailUsuario", FormataValor.strNull(rsInfoUsuario.getString("E_MAIL")));
                infoUsuario.put("loginUsuario", FormataValor.strNull(rsInfoUsuario.getString("LOGIN")));
                infoUsuario.put("senhaUsuario", FormataValor.strNull(rsInfoUsuario.getString("SENHA")));
                infoUsuario.put("bloqueado", FormataValor.strNull(rsInfoUsuario.getString("BLOQUEADO")));
                infoUsuario.put("senhasAntigas", FormataValor.strNull(rsInfoUsuario.getString("SENHAS_ANTIGAS")));
            }
        } catch (SQLException e) {
        	GeraLog.error("Erro em process_jsp.Executa.getInfoUsuario(): \nSQL : " + sql, e);
		} finally {
			Conexao.fecharConexao(con, stmt, rsInfoUsuario);
		}
        return infoUsuario;
    }

    public String getColuna(String tabela1,String tabela2,String coluna,String where) {
    	Connection con = Conexao.getInstance().getConnection("em Executa.getColuna linha:1607");
		Statement stmt = null;

        String tipoValor = "";
        String sql = "SELECT " + coluna + " FROM " + usuarioDB + tabela1;
        if (!tabela2.equals("")) {
            sql += ", " + usuarioDB + tabela2;
        }
        sql += " WHERE " + where;

        ResultSet rsTipoValor = null;

        try {
        	stmt = con.createStatement();
            rsTipoValor = stmt.executeQuery(sql);
            if (rsTipoValor.next()) {
                tipoValor = FormataValor.strNull(rsTipoValor.getString(coluna));
            }
        } catch (SQLException e) {
        	GeraLog.error("Erro em process_jsp.Executa.getColuna(): \nSQL : " + sql, e);
		} finally {
			Conexao.fecharConexao(con, stmt, rsTipoValor);
		}
        return tipoValor;
    }

    public String getColuna(String tabela, String coluna, String where) {
        return getColuna(tabela, "", coluna, where);
    }

    public String getUnidadeEmpresa() {
        String unidade =
            getColuna(
                "ESCRITORIO_CONTRATADO",
                "SIGLA_ESCRITORIO",
                " COD_ESCRITORIO = " + FormataValor.strNullInt(idEscritorio));
        if (unidade.equals("")) {
            unidade = "process";
        }
        return unidade;
    }

    public String getUnidadeEmpresaLogin() {
    		Connection con = Conexao.getInstance().getConnection();
    		Statement stmt = null;

            String unidade = "SUCOJ";
            String sql = "SELECT NVL(SIGLA_ESCRITORIO,'process') SIGLA_ESCRITORIO FROM " + usuarioDB + "ESCRITORIO_CONTRATADO" +
                         " WHERE COD_ESCRITORIO = " + FormataValor.strNullInt(idEscritorio);

            ResultSet rsTipoValor = null;

            try {
            	stmt = con.createStatement();
                rsTipoValor = stmt.executeQuery(sql);
                if (rsTipoValor.next()) {
                    unidade = FormataValor.strNull(rsTipoValor.getString("SIGLA_ESCRITORIO"));
                }
                GeraLog.debug("Processo.getUnidadeEmpresa - \nSql:" + sql + " >> unidade : [" + unidade + "]");
            } catch (SQLException e) {
            	GeraLog.error("Erro em process_jsp.Executa.getUnidadeEmpresa(): \nSQL : " + sql, e);
    		} finally {
    			Conexao.fecharConexao(con, stmt, rsTipoValor);
    		}

            return unidade;
        }

    public PermissaoModulo permite(String nomeModulo) {
        PermissaoModulo permissaoModulo =
            permissoes.getPermissaoModulo(
                permissoesUsuarioPorModulo.get(nomeModulo));
        return permissaoModulo;
    }

    /**
     * @param profundidadePath
     * @return retorna o caminho para o cabeçalho do cliente.
     */
    public String getPaginaCabecalho(
        HttpServletRequest req,
        int profundidadePath) {

        String caminho = getCaminho(profundidadePath);

        req.setAttribute("caminho", caminho);

        return caminho + "layout/" + configuracao.getPaginaCabecalho();

    }

    /**
     * @param profundidade
     * @return retorna o caminho relativos dos diretorios
     */
    public String getCaminho(int profundidade) {

        String cab = "../";

        if (profundidade == 0) {

            return "./";
        } else {
            while (profundidade > 1) {
                cab += "../";
                profundidade--;
            }
            return cab;
        }
    }
    
    private void setPermissoes() {
        if (permissoes == null) {
            permissoes = new Permissoes(this);
            perfilDao = new PerfilDAO(this);
        }
        if (permissoesUsuarioPorModulo == null) {
            permissoesUsuarioPorModulo = permissoes.getPermissoes(this.perfil);
            permissoesUsuarioPorCampo  = permissoes.getPermissoesCampo(this.perfil);
            permissoesPorTabela.setCodPerfil(this.perfil);
            permissoesPorTabela.setCodCompanhias(permissoes.getCodCompanhia(this.perfil));
            perfilDao.setaCodigos(permissoesPorTabela);
        }
    }

    public static String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        t.printStackTrace(pw);
        pw.flush();
        sw.flush();
        return sw.toString();
    }
}