// Aba Consultas: as 10 perguntas do minimundo, o SQL enviado e o resultado.
const consultas = {
  lista: null,

  async carregar() {
    const aba = document.getElementById('aba-consultas');
    if (!consultas.lista) {
      consultas.lista = await api.get('/api/consultas');
      aba.innerHTML = `
        <h2>Consultas do minimundo</h2>
        <form id="form-consulta">
          <select name="numero" id="consulta-numero">
            ${consultas.lista.map(c => `<option value="${c.numero}">${c.numero}. ${ui.esc(c.titulo)}</option>`).join('')}
          </select>
          <span id="consulta-param"></span>
          <button type="submit">Executar</button>
        </form>
        <p id="consulta-pergunta"></p>
        <p class="vazio" id="consulta-recursos"></p>
        <pre id="consulta-sql"></pre>
        <p id="consulta-total"></p>
        <div id="consulta-resultado"></div>`;
      document.getElementById('consulta-numero').addEventListener('change', () => consultas.selecionar());
      document.getElementById('form-consulta').addEventListener('submit', e => {
        e.preventDefault();
        consultas.executar().catch(err => console.error(err));
      });
      consultas.selecionar();
    }
  },

  atual() {
    const n = Number(document.getElementById('consulta-numero').value);
    return consultas.lista.find(c => c.numero === n);
  },

  selecionar() {
    const c = consultas.atual();
    document.getElementById('consulta-pergunta').innerHTML = `<b>Pergunta:</b> ${ui.esc(c.pergunta)}`;
    document.getElementById('consulta-recursos').textContent = 'Recursos SQL: ' + c.recursos;
    document.getElementById('consulta-sql').textContent = c.sql;
    document.getElementById('consulta-param').innerHTML = c.parametro
      ? `<label>${ui.esc(c.parametro.rotulo)}
           <input name="${ui.esc(c.parametro.nome)}" value="${ui.esc(c.parametro.exemplo)}" required>
         </label>`
      : '';
    consultas.executar().catch(err => console.error(err));
  },

  async executar() {
    const c = consultas.atual();
    const params = {};
    if (c.parametro) params[c.parametro.nome] = document.querySelector(`#consulta-param input`).value.trim();
    const total = document.getElementById('consulta-total');
    const destino = document.getElementById('consulta-resultado');
    total.textContent = 'Executando...';
    try {
      const r = await api.get('/api/consultas/' + c.numero, params);
      total.textContent = `${r.linhas.length} linha(s)`;
      destino.innerHTML = ui.tabela(r.linhas);
    } catch (e) {
      total.textContent = '';
      destino.innerHTML = '';
      throw e;
    }
  },
};
