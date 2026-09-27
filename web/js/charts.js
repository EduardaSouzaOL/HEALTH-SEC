// Gráficos em SVG desenhados à mão (sem biblioteca). Cada função devolve o texto do <svg>.
// Cores por papel em style.css (--serie-1, --bom, --critico...). Tooltip: qualquer elemento
// com data-tip="..." mostra o texto ao passar o mouse.
const charts = {
  L: 520, A: 260,                                   // viewBox; o SVG ocupa 100% da largura
  M: { top: 22, right: 12, bottom: 40, left: 44 },  // margens da área de plotagem

  // Eixo Y "redondo": máximo e passo em 1, 2 ou 5 × 10^n, com cerca de 4 divisões.
  escala(max) {
    if (!(max > 0)) return { max: 1, passo: 1 };
    const bruto = max / 4, pot = 10 ** Math.floor(Math.log10(bruto));
    const passo = [1, 2, 5, 10].map(m => m * pot).find(p => p >= bruto);
    return { max: Math.ceil(max / passo) * passo, passo: Math.max(1, passo) };
  },

  num(v) { return Number(v).toLocaleString('pt-BR'); },

  // Grade horizontal + rótulos do eixo Y. Devolve também a função y(valor) -> pixel.
  eixoY(maxDados) {
    const { L, A, M } = charts, e = charts.escala(maxDados);
    const y = v => A - M.bottom - (v / e.max) * (A - M.top - M.bottom);
    let svg = '';
    for (let v = 0; v <= e.max; v += e.passo) {
      svg += `<line class="grade" x1="${M.left}" x2="${L - M.right}" y1="${y(v)}" y2="${y(v)}"/>
              <text class="eixo" x="${M.left - 6}" y="${y(v) + 4}" text-anchor="end">${charts.num(v)}</text>`;
    }
    return { svg, y };
  },

  svg(corpo, rotulo) {
    return `<svg viewBox="0 0 ${charts.L} ${charts.A}" role="img" aria-label="${ui.esc(rotulo)}">${corpo}</svg>`;
  },

  // Barra vertical com topo arredondado e base reta, apoiada na linha de base.
  barra(x, larg, yTopo, yBase, cor, tip) {
    const h = yBase - yTopo;
    if (h <= 0) return '';
    const r = Math.min(4, larg / 2, h);
    return `<path class="marca" fill="${cor}" data-tip="${ui.esc(tip)}"
      d="M${x},${yBase} V${yTopo + r} Q${x},${yTopo} ${x + r},${yTopo} H${x + larg - r}
         Q${x + larg},${yTopo} ${x + larg},${yTopo + r} V${yBase} Z"/>`;
  },

  // Barras agrupadas: uma categoria no eixo X, uma barra por série dentro do grupo.
  // series: [{nome, cor, valores: [n por categoria]}]
  barrasAgrupadas({ categorias, series, rotulo }) {
    const { L, A, M } = charts;
    const max = Math.max(0, ...series.flatMap(s => s.valores));
    const { svg: grade, y } = charts.eixoY(max);
    const faixa = (L - M.left - M.right) / categorias.length;
    const larg = Math.min(26, (faixa * 0.7) / series.length), gap = 2;
    let corpo = grade;
    categorias.forEach((cat, i) => {
      const grupo = series.length * larg + (series.length - 1) * gap;
      const x0 = M.left + i * faixa + (faixa - grupo) / 2;
      series.forEach((s, j) => {
        const v = s.valores[i], x = x0 + j * (larg + gap);
        corpo += charts.barra(x, larg, y(v), y(0), s.cor, `${cat} · ${s.nome}: ${charts.num(v)}`);
        if (v > 0) corpo += `<text class="valor" x="${x + larg / 2}" y="${y(v) - 4}" text-anchor="middle">${charts.num(v)}</text>`;
      });
      corpo += `<text class="eixo" x="${M.left + i * faixa + faixa / 2}" y="${A - M.bottom + 16}" text-anchor="middle">${ui.esc(cat)}</text>`;
    });
    corpo += `<line class="base" x1="${M.left}" x2="${L - M.right}" y1="${y(0)}" y2="${y(0)}"/>`;
    return charts.svg(corpo, rotulo);
  },

  // Linha com pontos (série temporal). Rotula só o eixo a cada `cadaN` pontos, o maior valor e o último.
  linha({ rotulos, valores, rotulo, cadaN = 3 }) {
    const { L, A, M } = charts;
    const { svg: grade, y } = charts.eixoY(Math.max(0, ...valores));
    const passo = (L - M.left - M.right) / Math.max(1, valores.length - 1);
    const x = i => M.left + i * passo;
    const iMax = valores.indexOf(Math.max(...valores));
    let corpo = grade + `<line class="base" x1="${M.left}" x2="${L - M.right}" y1="${y(0)}" y2="${y(0)}"/>`;
    corpo += `<polyline class="traco" points="${valores.map((v, i) => `${x(i)},${y(v)}`).join(' ')}"/>`;
    valores.forEach((v, i) => {
      corpo += `<circle class="ponto" cx="${x(i)}" cy="${y(v)}" r="3.5"/>`;
      // Alvo invisível maior que o ponto, para o tooltip ser fácil de acertar.
      corpo += `<rect class="alvo" x="${x(i) - passo / 2}" y="${M.top}" width="${passo}" height="${A - M.top - M.bottom}"
                 data-tip="${ui.esc(rotulos[i])}: ${charts.num(v)} dose(s)"/>`;
      if (i % cadaN === 0 || i === valores.length - 1) {
        corpo += `<text class="eixo" x="${x(i)}" y="${A - M.bottom + 16}" text-anchor="middle">${ui.esc(rotulos[i])}</text>`;
      }
      if ((i === iMax || i === valores.length - 1) && v > 0) {
        corpo += `<text class="valor" x="${x(i)}" y="${y(v) - 8}" text-anchor="middle">${charts.num(v)}</text>`;
      }
    });
    return charts.svg(corpo, rotulo);
  },

  // Barras horizontais com rótulo à esquerda e "n (p%)" à direita. itens: [{rotulo, valor, pct, cor}]
  barrasH({ itens, rotulo }) {
    const { L } = charts, esq = 96, dir = 90, alt = 26, gap = 12, topo = 8;
    const A = topo * 2 + itens.length * (alt + gap) - gap;
    const max = Math.max(1, ...itens.map(i => i.valor));
    let corpo = '';
    itens.forEach((it, k) => {
      const y = topo + k * (alt + gap), w = (it.valor / max) * (L - esq - dir);
      corpo += `<text class="rotulo" x="${esq - 8}" y="${y + alt / 2 + 4}" text-anchor="end">${ui.esc(it.rotulo)}</text>`;
      if (w > 0) {
        const r = Math.min(4, w);
        corpo += `<path class="marca" fill="${it.cor}" data-tip="${ui.esc(it.rotulo)}: ${charts.num(it.valor)} (${it.pct}%)"
          d="M${esq},${y} H${esq + w - r} Q${esq + w},${y} ${esq + w},${y + r} V${y + alt - r}
             Q${esq + w},${y + alt} ${esq + w - r},${y + alt} H${esq} Z"/>`;
      }
      corpo += `<text class="valor" x="${esq + w + 6}" y="${y + alt / 2 + 4}">${charts.num(it.valor)} (${charts.num(it.pct)}%)</text>`;
    });
    corpo += `<line class="base" x1="${esq}" x2="${esq}" y1="0" y2="${A}"/>`;
    return `<svg viewBox="0 0 ${L} ${A}" role="img" aria-label="${ui.esc(rotulo)}">${corpo}</svg>`;
  },

  // Histograma: classes contíguas [inicio, inicio + largura) e linhas verticais de referência.
  // classes: [{inicio, valor}] em ordem; marcas: [{valor, nome, classe}] (ex.: média, mediana).
  histograma({ classes, largura, marcas = [], rotulo, unidade = '' }) {
    const { L, A, M } = charts;
    const { svg: grade, y } = charts.eixoY(Math.max(0, ...classes.map(c => c.valor)));
    const xMin = classes[0].inicio, xMax = classes.at(-1).inicio + largura;
    const x = v => M.left + ((v - xMin) / (xMax - xMin)) * (L - M.left - M.right);
    let corpo = grade;
    classes.forEach(c => {
      const x0 = x(c.inicio) + 1, w = x(c.inicio + largura) - x(c.inicio) - 2;  // 2px de respiro entre barras
      corpo += charts.barra(x0, w, y(c.valor), y(0), 'var(--serie-1)',
        `${c.inicio} a ${c.inicio + largura - 1}${unidade}: ${charts.num(c.valor)}`);
    });
    classes.concat({ inicio: xMax }).forEach(c =>
      corpo += `<text class="eixo" x="${x(c.inicio)}" y="${A - M.bottom + 16}" text-anchor="middle">${c.inicio}</text>`);
    corpo += `<line class="base" x1="${M.left}" x2="${L - M.right}" y1="${y(0)}" y2="${y(0)}"/>`;
    marcas.forEach((m, k) => {
      const xm = x(m.valor), ancora = xm > L - 90 ? 'end' : 'start', dx = ancora === 'end' ? -4 : 4;
      corpo += `<line class="referencia ${m.classe}" x1="${xm}" x2="${xm}" y1="${M.top - 8}" y2="${y(0)}"/>
                <text class="rotulo" x="${xm + dx}" y="${M.top - 10 + k * 13}" text-anchor="${ancora}">${ui.esc(m.nome)} ${charts.num(m.valor)}</text>`;
    });
    return charts.svg(corpo, rotulo);
  },

  // Legenda em HTML. itens: [{nome, cor}] (quadrado) ou [{nome, classe}] (traço de linha de referência).
  legenda(itens) {
    return `<div class="legenda">${itens.map(i => i.classe
      ? `<span><svg width="18" height="10"><line class="referencia ${i.classe}" x1="0" x2="18" y1="5" y2="5"/></svg>${ui.esc(i.nome)}</span>`
      : `<span><i style="background:${i.cor}"></i>${ui.esc(i.nome)}</span>`).join('')}</div>`;
  },
};

// Tooltip único para todos os gráficos (delegação de eventos no documento).
document.addEventListener('mousemove', e => {
  let tip = document.getElementById('tooltip-grafico');
  const alvo = e.target.closest?.('[data-tip]');
  if (!alvo) { if (tip) tip.hidden = true; return; }
  if (!tip) {
    tip = Object.assign(document.createElement('div'), { id: 'tooltip-grafico' });
    document.body.appendChild(tip);
  }
  tip.textContent = alvo.dataset.tip;
  tip.hidden = false;
  tip.style.left = Math.min(e.clientX + 12, innerWidth - tip.offsetWidth - 8) + 'px';
  tip.style.top = (e.clientY + 14) + 'px';
});
