package br.cesar.vacinas.api;

import br.cesar.vacinas.dao.ConsultaDao;
import br.cesar.vacinas.db.Database;
import br.cesar.vacinas.http.HttpError;
import br.cesar.vacinas.http.Router;
import java.util.Map;   // usado pelas rotas que devolvem {"mensagem": ...}

/**
 * Todos os endpoints: MÉTODO + caminho -> método do DAO.
 * Cada fatia fica no seu bloco. Acrescente rotas; não reorganize os blocos dos colegas.
 */
public final class Routes {
    public static void registrar(Router r) {
        r.get("/api/health", req -> Database.queryUm("SELECT VERSION() AS versao, DATABASE() AS banco"));

        // ==== CATÁLOGOS (listas dos selects) ====
        // ==== CONSULTAS (João Arthur) ====
        ConsultaDao consultas = new ConsultaDao();
        r.get("/api/consultas", req -> consultas.listar());
        // As consultas 3 e 6 exigem ?cns= ou ?id_lote=.
        r.get("/api/consultas/{numero}", req -> {
            int numero;
            try { numero = Integer.parseInt(req.path("numero")); }
            catch (NumberFormatException e) { throw new HttpError(400, "Número de consulta inválido"); }
            ConsultaDao.Consulta c = consultas.buscar(numero);
            if (c == null) throw new HttpError(404, "Consulta " + numero + " não existe (use 1 a 10)");
            Object valor = null;
            if (c.parametro() != null) {
                String nome = c.parametro().nome();
                if (nome.equals("cns")) {
                    String cns = req.reqStr("cns");
                    if (!cns.matches("\\d{15}")) throw new HttpError(400, "O CNS deve ter 15 dígitos");
                    valor = cns;
                } else {
                    valor = req.reqInt(nome);
                }
            }
            return Map.of("numero", numero, "linhas", consultas.executar(c, valor));
        });

        // ==== DASHBOARD (Cauã) ====
        // ==== ESTOQUE (João Pedro) ====
        // ==== PACIENTES (Davila) ====
    }
}
