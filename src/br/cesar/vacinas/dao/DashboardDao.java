package br.cesar.vacinas.dao;

import br.cesar.vacinas.db.Database;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dados do dashboard. Os métodos da seção "Estatística" alimentam os gráficos entregues
 * na disciplina de Estatística (E3).
 *
 * Filtro de UBS: cnes null = todas. Todos os gráficos filtram pela UBS de REFERÊNCIA do
 * paciente (Paciente.cnes), porque doses pendentes não têm UBS de aplicação.
 */
public final class DashboardDao {

    /** Trecho de WHERE do filtro de UBS; recebe o cnes duas vezes (ver comUbs). Os espaços nas pontas
     *  são de propósito: o Java apaga o espaço final das linhas de um text block. */
    private static final String FILTRO_UBS = " (? IS NULL OR p.cnes = ?) ";

    private static Object[] comUbs(String cnes) { return new Object[] { cnes, cnes }; }

    /** Lista do filtro de UBS do topo. */
    public List<Map<String, Object>> listarUbs() throws SQLException {
        return Database.query("SELECT cnes, nome FROM UBS ORDER BY nome");
    }

    // ==== ESTATÍSTICA ====

    /**
     * Gráfico 1 · barras agrupadas. Doses aplicadas por faixa etária e sexo.
     * Variáveis: faixa etária (qualitativa ordinal, idade NA DATA DA APLICAÇÃO) × sexo (nominal).
     */
    public List<Map<String, Object>> dosesPorFaixaEtariaESexo(String cnes) throws SQLException {
        return Database.query("""
            SELECT CASE WHEN t.idade < 1  THEN 1
                        WHEN t.idade < 5  THEN 2
                        WHEN t.idade < 10 THEN 3
                        WHEN t.idade < 20 THEN 4
                        WHEN t.idade < 60 THEN 5
                        ELSE 6 END AS ordem,
                   CASE WHEN t.idade < 1  THEN 'Menos de 1'
                        WHEN t.idade < 5  THEN '1 a 4'
                        WHEN t.idade < 10 THEN '5 a 9'
                        WHEN t.idade < 20 THEN '10 a 19'
                        WHEN t.idade < 60 THEN '20 a 59'
                        ELSE '60 ou mais' END AS faixa,
                   t.sexo, COUNT(*) AS doses
              FROM (SELECT p.sexo, TIMESTAMPDIFF(YEAR, p.data_nascimento, r.data_aplicacao) AS idade
                      FROM Registro_Dose r
                      JOIN Paciente p ON p.cns = r.cns
                     WHERE r.status = 'APLICADA'
                       AND """ + FILTRO_UBS + """
                   ) t
             GROUP BY ordem, faixa, t.sexo
             ORDER BY ordem, t.sexo""", comUbs(cnes));
    }

    /**
     * Gráfico 2 · linha. Doses aplicadas por mês nos últimos 24 meses (inclui o mês atual).
     * Variável: contagem mensal (quantitativa discreta) ao longo do tempo. Meses sem dose não
     * vêm do banco; o front completa com zero.
     */
    public Map<String, Object> dosesPorMes(String cnes) throws SQLException {
        Map<String, Object> inicio = Database.queryUm("""
            SELECT DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 23 MONTH), '%Y-%m') AS primeiro,
                   DATE_FORMAT(CURDATE(), '%Y-%m') AS ultimo""");
        List<Map<String, Object>> meses = Database.query("""
            SELECT DATE_FORMAT(r.data_aplicacao, '%Y-%m') AS mes, COUNT(*) AS doses
              FROM Registro_Dose r
              JOIN Paciente p ON p.cns = r.cns
             WHERE r.status = 'APLICADA'
               AND r.data_aplicacao >= DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL 23 MONTH)
               AND """ + FILTRO_UBS + """
             GROUP BY mes
             ORDER BY mes""", comUbs(cnes));
        Map<String, Object> r = new LinkedHashMap<>(inicio);
        r.put("meses", meses);
        return r;
    }

    /**
     * Gráfico 3 · barras horizontais. Distribuição das doses por situação.
     * Variável: situação (qualitativa nominal). ATRASADA não é status gravado: é uma dose
     * PENDENTE com data prevista já vencida.
     */
    public List<Map<String, Object>> dosesPorSituacao(String cnes) throws SQLException {
        return Database.query("""
            SELECT t.situacao, COUNT(*) AS doses,
                   ROUND(100 * COUNT(*) / SUM(COUNT(*)) OVER (), 1) AS percentual
              FROM (SELECT CASE WHEN r.status = 'PENDENTE' AND r.data_prevista < CURDATE() THEN 'ATRASADA'
                                ELSE r.status END AS situacao
                      FROM Registro_Dose r
                      JOIN Paciente p ON p.cns = r.cns
                     WHERE """ + FILTRO_UBS + """
                   ) t
             GROUP BY t.situacao
             ORDER BY doses DESC""", comUbs(cnes));
    }

    /**
     * Gráfico 4 · histograma. Idade atual dos pacientes em classes de 10 anos, com as medidas
     * resumo (média, mediana, desvio padrão amostral, mínimo e máximo).
     * Variável: idade (quantitativa contínua).
     */
    public Map<String, Object> idadePacientes(String cnes) throws SQLException {
        String idades = """
            SELECT TIMESTAMPDIFF(YEAR, p.data_nascimento, CURDATE()) AS idade
              FROM Paciente p
             WHERE """ + FILTRO_UBS;

        List<Map<String, Object>> classes = Database.query("""
            SELECT FLOOR(i.idade / 10) * 10 AS inicio, COUNT(*) AS pacientes
              FROM (""" + idades + """
                   ) i
             GROUP BY inicio
             ORDER BY inicio""", comUbs(cnes));

        // Mediana: média dos 1 ou 2 valores do meio, numerados com ROW_NUMBER.
        Map<String, Object> resumo = Database.queryUm("""
            SELECT COUNT(*) AS n,
                   ROUND(AVG(i.idade), 1)         AS media,
                   ROUND(STDDEV_SAMP(i.idade), 1) AS desvio_padrao,
                   MIN(i.idade) AS minimo,
                   MAX(i.idade) AS maximo,
                   (SELECT ROUND(AVG(o.idade), 1)
                      FROM (SELECT x.idade,
                                   ROW_NUMBER() OVER (ORDER BY x.idade) AS pos,
                                   COUNT(*) OVER ()                     AS total
                              FROM (""" + idades + """
                                   ) x) o
                     WHERE o.pos IN (FLOOR((o.total + 1) / 2), CEIL((o.total + 1) / 2))) AS mediana
              FROM (""" + idades + """
                   ) i""", cnes, cnes, cnes, cnes);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("classes", classes);
        r.put("resumo", resumo);
        return r;
    }
}
