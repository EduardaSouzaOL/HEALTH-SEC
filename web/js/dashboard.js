// Aba Dashboard: filtro de UBS no topo e seção "Estatística" (gráficos da disciplina de Estatística).
// Os indicadores (H13) entram acima da seção Estatística e também devem ler dashboard.cnes().
const dashboard = {
  montado: false,

  async carregar() {
    if (!dashboard.montado) {
      const ubs = await api.get('/api/dashboard/ubs');
      document.getElementById('aba-dashboard').innerHTML = `
        <div class="filtro-topo">
          <h2>Dashboard</h2>
          <label>UBS
            <select id="dash-ubs">
              <option value="">Todas as UBS</option>${ui.opcoes(ubs, 'cnes', 'nome')}
            </select>
          </label>
        </div>
        <section class="secao">
          <h2>Estatística</h2>
          <p class="vazio">Gráficos entregues na disciplina de Estatística, calculados direto no banco.
            O filtro de UBS considera a UBS de referência do paciente.</p>
          <div class="graficos" id="dash-estatistica"></div>
        </section>`;
      document.getElementById('dash-ubs').addEventListener('change', () =>
        dashboard.atualizar().catch(e => console.error(e)));
      dashboard.montado = true;
    }
    await dashboard.atualizar();
  },

  cnes() { return document.getElementById('dash-ubs').value || undefined; },

  async atualizar() {
    await dashboard.estatistica(dashboard.cnes());
  },

  // ==== ESTATÍSTICA ====
  async estatistica(cnes) {
    const base = '/api/dashboard/estatistica/';
    const [faixas, meses, situacao, idades] = await Promise.all([
      api.get(base + 'faixa-etaria-sexo', { cnes }),
      api.get(base + 'doses-por-mes', { cnes }),
      api.get(base + 'situacao-doses', { cnes }),
      api.get(base + 'idade-pacientes', { cnes }),
    ]);
    document.getElementById('dash-estatistica').innerHTML = [
      dashboard.graficoFaixaSexo(faixas),
      dashboard.graficoMeses(meses),
      dashboard.graficoSituacao(situacao),
      dashboard.graficoIdade(idades),
    ].join('');
  },

  // Moldura comum: título, pergunta respondida, variáveis, gráfico, legenda e tabela dos dados.
  figura({ titulo, pergunta, variaveis, grafico, legenda = '', linhas }) {
    return `<figure>
      <figcaption>${ui.esc(titulo)}</figcaption>
      <p class="pergunta">${ui.esc(pergunta)}</p>
      ${linhas.length ? grafico + legenda : '<p class="vazio">Sem dados para esta UBS.</p>'}
      <p class="variaveis">${ui.esc(variaveis)}</p>
      <details><summary>Ver dados</summary>${ui.tabela(linhas)}</details>
    </figure>`;
  },

  FAIXAS: ['Menos de 1', '1 a 4', '5 a 9', '10 a 19', '20 a 59', '60 ou mais'],
  SEXOS: [{ sigla: 'F', nome: 'Feminino', cor: 'var(--serie-1)' }, { sigla: 'M', nome: 'Masculino', cor: 'var(--serie-2)' }],

  graficoFaixaSexo(linhas) {
    const valor = (faixa, sexo) => Number(linhas.find(l => l.faixa === faixa && l.sexo === sexo)?.doses ?? 0);
    const series = dashboard.SEXOS.map(s => ({ ...s, valores: dashboard.FAIXAS.map(f => valor(f, s.sigla)) }));
    return dashboard.figura({
      titulo: 'Doses aplicadas por faixa etária e sexo',
      pergunta: 'Em quais faixas etárias e em qual sexo se concentram as doses aplicadas?',
      variaveis: 'Faixa etária em anos, idade na data da aplicação (qualitativa ordinal) × sexo (qualitativa nominal). Barras agrupadas.',
      grafico: charts.barrasAgrupadas({ categorias: dashboard.FAIXAS, series, rotulo: 'Doses aplicadas por faixa etária e sexo' }),
      legenda: charts.legenda(series),
      linhas: linhas.map(l => ({ 'Faixa etária': l.faixa, Sexo: l.sexo, Doses: l.doses })),
    });
  },

  MESES: ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'],

  graficoMeses(r) {
    // Completa com zero os meses sem dose, de r.primeiro até r.ultimo (aaaa-mm).
    const todos = [];
    let [ano, mes] = r.primeiro.split('-').map(Number);
    for (let k = `${ano}-${String(mes).padStart(2, '0')}`; k <= r.ultimo; k = `${ano}-${String(mes).padStart(2, '0')}`) {
      todos.push(k);
      if (++mes > 12) { mes = 1; ano++; }
    }
    const doses = Object.fromEntries(r.meses.map(m => [m.mes, Number(m.doses)]));
    const rotulo = k => `${dashboard.MESES[Number(k.slice(5)) - 1]}/${k.slice(2, 4)}`;
    const total = r.meses.reduce((s, m) => s + Number(m.doses), 0);
    return dashboard.figura({
      titulo: 'Doses aplicadas por mês (últimos 24 meses)',
      pergunta: 'Como o número de doses aplicadas variou ao longo do tempo?',
      variaveis: `Doses aplicadas no mês (quantitativa discreta) ao longo do tempo. Total no período: ${charts.num(total)}; média de ${charts.num((total / todos.length).toFixed(1))} por mês.`,
      grafico: charts.linha({ rotulos: todos.map(rotulo), valores: todos.map(k => doses[k] ?? 0), rotulo: 'Doses aplicadas por mês' }),
      linhas: r.meses.length ? todos.map(k => ({ Mês: rotulo(k), Doses: doses[k] ?? 0 })) : [],
    });
  },

  SITUACOES: {
    APLICADA:  { nome: 'Aplicada',  cor: 'var(--bom)' },
    PENDENTE:  { nome: 'Pendente',  cor: 'var(--serie-1)' },
    ATRASADA:  { nome: 'Atrasada',  cor: 'var(--critico)' },
    CANCELADA: { nome: 'Cancelada', cor: 'var(--neutro)' },
  },

  graficoSituacao(linhas) {
    const itens = linhas.map(l => ({
      rotulo: dashboard.SITUACOES[l.situacao]?.nome ?? l.situacao,
      cor: dashboard.SITUACOES[l.situacao]?.cor ?? 'var(--neutro)',
      valor: Number(l.doses), pct: Number(l.percentual),
    }));
    return dashboard.figura({
      titulo: 'Situação das doses registradas',
      pergunta: 'Qual a proporção de doses aplicadas, pendentes, atrasadas e canceladas?',
      variaveis: 'Situação da dose (qualitativa nominal): frequência absoluta e relativa. Atrasada = pendente com data prevista vencida.',
      grafico: charts.barrasH({ itens, rotulo: 'Situação das doses registradas' }),
      linhas: itens.map(i => ({ Situação: i.rotulo, Doses: i.valor, '%': i.pct })),
    });
  },

  graficoIdade(r) {
    const largura = 10, s = r.resumo;
    // Completa as classes vazias entre a menor e a maior.
    const cont = Object.fromEntries(r.classes.map(c => [Number(c.inicio), Number(c.pacientes)]));
    const inicios = r.classes.map(c => Number(c.inicio));
    const classes = [];
    for (let i = Math.min(...inicios); i <= Math.max(...inicios); i += largura) classes.push({ inicio: i, valor: cont[i] ?? 0 });
    const marcas = s.n > 0 ? [
      { valor: Number(s.media), nome: 'Média', classe: 'media' },
      { valor: Number(s.mediana), nome: 'Mediana', classe: 'mediana' },
    ] : [];
    return dashboard.figura({
      titulo: 'Distribuição da idade dos pacientes',
      pergunta: 'Qual o perfil de idade dos pacientes atendidos?',
      variaveis: s.n > 0
        ? `Idade atual em anos (quantitativa contínua), classes de ${largura} anos. n = ${s.n} · média ${charts.num(s.media)} · mediana ${charts.num(s.mediana)} · desvio padrão ${charts.num(s.desvio_padrao ?? 0)} · mín. ${s.minimo} · máx. ${s.maximo}.`
        : 'Idade atual em anos (quantitativa contínua).',
      grafico: classes.length ? charts.histograma({ classes, largura, marcas, unidade: ' anos', rotulo: 'Histograma da idade dos pacientes' }) : '',
      legenda: charts.legenda([{ nome: 'Pacientes na classe', cor: 'var(--serie-1)' }, ...marcas]),
      linhas: classes.map(c => ({ 'Idade (anos)': `${c.inicio} a ${c.inicio + largura - 1}`, Pacientes: c.valor })),
    });
  },
};
