package br.cesar.vacinas.dao;

import br.cesar.vacinas.db.Database;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * As 10 perguntas do minimundo (seção 1.3). O SQL é o mesmo de db/consultas.sql,
 * com "?" no lugar de @cns e @id_lote: ao mudar uma consulta, mude nos dois arquivos.
 */
public final class ConsultaDao {

    public record Parametro(String nome, String rotulo, String exemplo) {}

    public record Consulta(int numero, String titulo, String pergunta, String recursos,
                           String sql, Parametro parametro) {}

    private static final List<Consulta> CONSULTAS = List.of(

        new Consulta(1, "Doses atrasadas por UBS",
            "Quais pacientes estão com doses atrasadas (data prevista vencida e status pendente), por UBS?",
            "INNER JOIN (4 tabelas), WHERE, ORDER BY, DATEDIFF",
            """
            SELECT u.nome AS ubs, p.nome AS paciente, v.nome AS vacina,
                   r.numero_dose AS dose, r.data_prevista,
                   DATEDIFF(CURDATE(), r.data_prevista) AS dias_de_atraso
              FROM Registro_Dose r
              JOIN Paciente p ON p.cns = r.cns
              JOIN UBS u      ON u.cnes = p.cnes
              JOIN Vacina v   ON v.codigo_vacina = r.codigo_vacina
             WHERE r.status = 'PENDENTE'
               AND r.data_prevista < CURDATE()
             ORDER BY u.nome, dias_de_atraso DESC""",
            null),

        new Consulta(2, "Cobertura vacinal por vacina e UBS",
            "Qual a cobertura vacinal (percentual de pacientes com o esquema completo) por vacina e por UBS? "
                + "Considera as doses já devidas até hoje (aplicadas ou com data prevista vencida).",
            "Subconsulta na cláusula FROM com HAVING, agregação (SUM, COUNT), GROUP BY, JOIN, percentual",
            """
            SELECT v.nome AS vacina, u.nome AS ubs,
                   COUNT(*) AS pacientes_com_doses_devidas,
                   SUM(t.completo) AS esquema_completo,
                   ROUND(100 * SUM(t.completo) / COUNT(*), 1) AS cobertura_pct
              FROM (SELECT r.cns, r.codigo_vacina,
                           (SUM(r.status <> 'APLICADA' AND r.data_prevista <= CURDATE()) = 0) AS completo
                      FROM Registro_Dose r
                     GROUP BY r.cns, r.codigo_vacina
                    HAVING SUM(r.status = 'APLICADA' OR r.data_prevista <= CURDATE()) > 0) t
              JOIN Paciente p ON p.cns = t.cns
              JOIN UBS u      ON u.cnes = p.cnes
              JOIN Vacina v   ON v.codigo_vacina = t.codigo_vacina
             GROUP BY v.codigo_vacina, v.nome, u.cnes, u.nome
             ORDER BY v.nome, u.nome""",
            null),

        new Consulta(3, "Carteira de vacinação de um paciente",
            "Qual a carteira de vacinação completa de um paciente?",
            "LEFT JOIN (doses pendentes não têm lote/profissional/UBS), parâmetro, ORDER BY",
            """
            SELECT v.nome AS vacina, r.numero_dose AS dose, r.status,
                   r.data_prevista, r.data_aplicacao,
                   l.numero_lote AS lote, l.fabricante,
                   pr.nome AS profissional, u.nome AS ubs_aplicacao, r.local_anatomico
              FROM Registro_Dose r
              JOIN Vacina v             ON v.codigo_vacina = r.codigo_vacina
              LEFT JOIN Lote l          ON l.id_lote = r.id_lote
              LEFT JOIN Profissional pr ON pr.id_profissional = r.id_profissional
              LEFT JOIN UBS u           ON u.cnes = r.cnes
             WHERE r.cns = ?
             ORDER BY v.nome, r.numero_dose""",
            new Parametro("cns", "CNS do paciente (15 dígitos)", "107939763812637")),

        new Consulta(4, "Lotes vencidos ou a vencer em 30 dias",
            "Quais lotes estão vencidos ou vencem em 30 dias, e quanto resta em cada UBS?",
            "JOIN (4 tabelas), CASE, DATE_ADD, WHERE com filtro de saldo",
            """
            SELECT l.numero_lote AS lote, v.nome AS vacina, l.data_validade,
                   CASE WHEN l.data_validade < CURDATE() THEN 'VENCIDO'
                        ELSE 'VENCE EM ATÉ 30 DIAS' END AS situacao,
                   u.nome AS ubs, e.quantidade_disponivel AS saldo
              FROM Lote l
              JOIN Vacina v      ON v.codigo_vacina = l.codigo_vacina
              JOIN Estoque_UBS e ON e.id_lote = l.id_lote
              JOIN UBS u         ON u.cnes = e.cnes
             WHERE l.data_validade <= DATE_ADD(CURDATE(), INTERVAL 30 DAY)
               AND e.quantidade_disponivel > 0
             ORDER BY l.data_validade, u.nome""",
            null),

        new Consulta(5, "Estoque atual por vacina e UBS",
            "Qual o estoque atual de cada vacina em cada UBS (só lotes dentro da validade)?",
            "JOIN (4 tabelas), SUM, GROUP BY, HAVING",
            """
            SELECT v.nome AS vacina, u.nome AS ubs,
                   SUM(e.quantidade_disponivel) AS doses_disponiveis
              FROM Estoque_UBS e
              JOIN Lote l   ON l.id_lote = e.id_lote
              JOIN Vacina v ON v.codigo_vacina = l.codigo_vacina
              JOIN UBS u    ON u.cnes = e.cnes
             WHERE l.data_validade >= CURDATE()
             GROUP BY v.codigo_vacina, v.nome, u.cnes, u.nome
            HAVING SUM(e.quantidade_disponivel) > 0
             ORDER BY v.nome, u.nome""",
            null),

        new Consulta(6, "Rastreabilidade de um lote",
            "Quais pacientes receberam doses de um determinado lote (do fabricante até o paciente)?",
            "JOIN (5 tabelas), parâmetro, ORDER BY",
            """
            SELECT l.fabricante, l.numero_lote AS lote, v.nome AS vacina,
                   p.nome AS paciente, p.cns, r.data_aplicacao,
                   pr.nome AS profissional, u.nome AS ubs_aplicacao
              FROM Lote l
              JOIN Vacina v         ON v.codigo_vacina = l.codigo_vacina
              JOIN Registro_Dose r  ON r.id_lote = l.id_lote
              JOIN Paciente p       ON p.cns = r.cns
              JOIN Profissional pr  ON pr.id_profissional = r.id_profissional
              JOIN UBS u            ON u.cnes = r.cnes
             WHERE l.id_lote = ?
             ORDER BY r.data_aplicacao, p.nome""",
            new Parametro("id_lote", "Id do lote", "1")),

        new Consulta(7, "Doses aplicadas por profissional por mês",
            "Quantas doses cada profissional aplicou por mês?",
            "JOIN, DATE_FORMAT, COUNT, GROUP BY com duas colunas",
            """
            SELECT DATE_FORMAT(r.data_aplicacao, '%Y-%m') AS mes,
                   pr.nome AS profissional, pr.cargo,
                   COUNT(*) AS doses_aplicadas
              FROM Registro_Dose r
              JOIN Profissional pr ON pr.id_profissional = r.id_profissional
             WHERE r.status = 'APLICADA'
             GROUP BY mes, pr.id_profissional, pr.nome, pr.cargo
             ORDER BY mes DESC, doses_aplicadas DESC""",
            null),

        new Consulta(8, "Crianças menores de 5 anos com esquema incompleto",
            "Quais crianças menores de 5 anos têm esquema incompleto, e quem são seus responsáveis?",
            "Auto-relacionamento (LEFT JOIN Paciente com Paciente), subconsulta correlacionada no SELECT, "
                + "GROUP_CONCAT, TIMESTAMPDIFF",
            """
            SELECT p.nome AS crianca,
                   TIMESTAMPDIFF(YEAR, p.data_nascimento, CURDATE()) AS idade_anos,
                   resp.nome AS responsavel,
                   (SELECT GROUP_CONCAT(t.telefone SEPARATOR ' / ')
                      FROM Paciente_Telefone t
                     WHERE t.cns = resp.cns) AS telefones_do_responsavel,
                   COUNT(*) AS doses_nao_aplicadas
              FROM Paciente p
              JOIN Registro_Dose r     ON r.cns = p.cns
              LEFT JOIN Paciente resp  ON resp.cns = p.cns_responsavel
             WHERE p.data_nascimento > DATE_SUB(CURDATE(), INTERVAL 5 YEAR)
               AND r.status = 'PENDENTE'
             GROUP BY p.cns, p.nome, p.data_nascimento, resp.cns, resp.nome
             ORDER BY doses_nao_aplicadas DESC, p.nome""",
            null),

        new Consulta(9, "Profissionais supervisionados por coordenador",
            "Quais profissionais são supervisionados por cada coordenador?",
            "Auto-relacionamento (JOIN Profissional com Profissional), JOIN com UBS",
            """
            SELECT sup.nome AS coordenador, sub.nome AS supervisionado,
                   sub.cargo, u.nome AS ubs_do_supervisionado
              FROM Profissional sup
              JOIN Profissional sub ON sub.id_supervisor = sup.id_profissional
              JOIN UBS u            ON u.cnes = sub.cnes
             ORDER BY sup.nome, sub.nome""",
            null),

        new Consulta(10, "Doses aplicadas por faixa etária e sexo",
            "Quantas doses foram aplicadas por faixa etária (na data da aplicação) e sexo?",
            "Subconsulta na cláusula FROM com CASE, TIMESTAMPDIFF, GROUP BY, COUNT",
            """
            SELECT t.faixa_etaria, t.sexo, COUNT(*) AS doses_aplicadas
              FROM (SELECT p.sexo,
                           CASE
                             WHEN TIMESTAMPDIFF(MONTH, p.data_nascimento, r.data_aplicacao) < 12 THEN 1
                             WHEN TIMESTAMPDIFF(MONTH, p.data_nascimento, r.data_aplicacao) < 60 THEN 2
                             WHEN TIMESTAMPDIFF(YEAR,  p.data_nascimento, r.data_aplicacao) < 12 THEN 3
                             WHEN TIMESTAMPDIFF(YEAR,  p.data_nascimento, r.data_aplicacao) < 18 THEN 4
                             WHEN TIMESTAMPDIFF(YEAR,  p.data_nascimento, r.data_aplicacao) < 60 THEN 5
                             ELSE 6 END AS ordem,
                           CASE
                             WHEN TIMESTAMPDIFF(MONTH, p.data_nascimento, r.data_aplicacao) < 12 THEN '0 a 11 meses'
                             WHEN TIMESTAMPDIFF(MONTH, p.data_nascimento, r.data_aplicacao) < 60 THEN '1 a 4 anos'
                             WHEN TIMESTAMPDIFF(YEAR,  p.data_nascimento, r.data_aplicacao) < 12 THEN '5 a 11 anos'
                             WHEN TIMESTAMPDIFF(YEAR,  p.data_nascimento, r.data_aplicacao) < 18 THEN '12 a 17 anos'
                             WHEN TIMESTAMPDIFF(YEAR,  p.data_nascimento, r.data_aplicacao) < 60 THEN '18 a 59 anos'
                             ELSE '60 anos ou mais' END AS faixa_etaria
                      FROM Registro_Dose r
                      JOIN Paciente p ON p.cns = r.cns
                     WHERE r.status = 'APLICADA') t
             GROUP BY t.ordem, t.faixa_etaria, t.sexo
             ORDER BY t.ordem, t.sexo""",
            null)
    );

    /** Metadados das 10 consultas para o seletor da tela (não executa nada). */
    public List<Map<String, Object>> listar() {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Consulta c : CONSULTAS) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("numero", c.numero());
            m.put("titulo", c.titulo());
            m.put("pergunta", c.pergunta());
            m.put("recursos", c.recursos());
            m.put("sql", c.sql());
            m.put("parametro", c.parametro() == null ? null : Map.of(
                "nome", c.parametro().nome(),
                "rotulo", c.parametro().rotulo(),
                "exemplo", c.parametro().exemplo()));
            lista.add(m);
        }
        return lista;
    }

    public Consulta buscar(int numero) {
        for (Consulta c : CONSULTAS) if (c.numero() == numero) return c;
        return null;
    }

    /** valor é o CNS (consulta 3) ou o id do lote (consulta 6); nas demais é ignorado. */
    public List<Map<String, Object>> executar(Consulta c, Object valor) throws SQLException {
        return c.parametro() == null ? Database.query(c.sql()) : Database.query(c.sql(), valor);
    }

    /** Consulta 10 pronta para o gráfico de faixa etária × sexo do dashboard (E2). */
    public List<Map<String, Object>> dosesPorFaixaEtariaESexo() throws SQLException {
        return executar(buscar(10), null);
    }
}
