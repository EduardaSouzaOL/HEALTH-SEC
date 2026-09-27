package br.cesar.vacinas.api;

import br.cesar.vacinas.dao.DashboardDao;
import br.cesar.vacinas.db.Database;
import br.cesar.vacinas.http.HttpError;
import br.cesar.vacinas.http.Request;
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
        // ==== DASHBOARD (Cauã) ====
        DashboardDao dashboard = new DashboardDao();
        r.get("/api/dashboard/ubs", req -> dashboard.listarUbs());
        // Estatística: todas aceitam ?cnes= (vazio = todas as UBS).
        r.get("/api/dashboard/estatistica/faixa-etaria-sexo", req -> dashboard.dosesPorFaixaEtariaESexo(cnes(req)));
        r.get("/api/dashboard/estatistica/doses-por-mes", req -> dashboard.dosesPorMes(cnes(req)));
        r.get("/api/dashboard/estatistica/situacao-doses", req -> dashboard.dosesPorSituacao(cnes(req)));
        r.get("/api/dashboard/estatistica/idade-pacientes", req -> dashboard.idadePacientes(cnes(req)));
        // ==== ESTOQUE (João Pedro) ====
        // ==== PACIENTES (Davila) ====
    }

    /** Filtro de UBS do dashboard: null = todas; se vier, precisa ter os 7 dígitos do CNES. */
    private static String cnes(Request req) {
        String cnes = req.str("cnes");
        if (cnes != null && !cnes.matches("\\d{7}")) throw new HttpError(400, "CNES deve ter 7 dígitos");
        return cnes;
    }
}
