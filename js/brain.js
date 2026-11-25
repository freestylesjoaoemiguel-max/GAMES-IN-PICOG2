'use strict';
/* Elenco de personagens + "IA" de conversa em português (funciona offline).
   Opcional: defina localStorage.pico_ai_url para um endpoint seu (POST {system,message,history} -> {reply}) e usar um LLM de verdade. */

const ROSTER = {
  lobby: [
    { id: 'pico', robot: true, name: 'Pico', role: 'Assistente IA', home: [2.2, -3.2], wander: false, pitch: 1.5, color: 0x4285f4,
      greet: ['Bip-bop! Eu sou o Pico, a IA do PicoVerso. Pergunte qualquer coisa ou escolha um mundo!', 'Olá, viajante! Quer jogar? Diga "mapas" e eu te mostro os mundos.'] }
  ],
  village: [
    { id: 'rosa', name: 'Dona Rosa', role: 'padeira', home: [5.5, 4], skin: 0xe8b88a, shirt: 0xf2a6b5, pants: 0x7a4b8a, hair: 0xb9b9c4, hairStyle: 'bun', accessory: 'apron', pitch: 1.3,
      fav: { comida: 'pão de queijo quentinho', cor: 'rosa', hobby: 'assar pão ao nascer do sol' },
      greet: ['Bom dia, querido! Acabei de tirar o pão do forno. Quer conversar?', 'Oi, oi! Que bom ver gente nova na vila!'] },
    { id: 'tiao', name: 'Seu Tião', role: 'fazendeiro', home: [-7.5, -5], skin: 0xc98e63, shirt: 0x4f8a4b, pants: 0x3b4a7a, hair: 0x5a3d2b, hairStyle: 'short', hat: 'straw', hatColor: 0xe3c46a, pitch: 0.75,
      fav: { comida: 'feijão tropeiro', cor: 'verde', hobby: 'cuidar da plantação' },
      greet: ['Eita, visita! Chega mais, sô!', 'Bão demais te ver por aqui, rapaz!'] },
    { id: 'luna', name: 'Luna', role: 'criança curiosa', home: [7.5, -6.5], scale: 0.72, skin: 0xf1c9a5, shirt: 0xffd23f, pants: 0x3b82f6, hair: 0x2b1b12, hairStyle: 'long', pitch: 1.7,
      fav: { comida: 'sorvete de morango', cor: 'amarelo', hobby: 'procurar estrelas escondidas' },
      greet: ['Oi!! Você é novo? Eu sei onde tem estrelas brilhantes!', 'Uhul, alguém pra brincar!'] },
    { id: 'beto', name: 'Capitão Beto', role: 'guarda da vila', home: [-6.5, 7.5], skin: 0x8a5a3c, shirt: 0x2c4a9a, pants: 0x222a44, hair: 0x111111, hairStyle: 'none', hat: 'guard', hatColor: 0xcfd6e6, accessory: 'beard', pitch: 0.7,
      fav: { comida: 'churrasco', cor: 'azul', hobby: 'patrulhar a praça' },
      greet: ['Atenção! Aqui quem fala é o Capitão Beto. A vila está segura!', 'Salve, cidadão! Precisa de ajuda?'] }
  ],
  desert: [
    { id: 'amira', name: 'Amira', role: 'comerciante', home: [5, 5], skin: 0xc88b5a, shirt: 0xd1385f, pants: 0xf2c14e, hair: 0x1a1210, hairStyle: 'long', hat: 'turban', hatColor: 0x7b2d8a, pitch: 1.35,
      fav: { comida: 'tâmaras com mel', cor: 'roxo', hobby: 'negociar tapetes' },
      greet: ['Bem-vindo ao oásis! Tenho os melhores tapetes do deserto!', 'Olá, viajante! A sombra é de graça, a conversa também.'] },
    { id: 'khalid', name: 'Khalid', role: 'guia do deserto', home: [-7, -5.5], skin: 0xa86f46, shirt: 0xf3e6c8, pants: 0x8a5a2b, hair: 0x1a1210, hairStyle: 'short', hat: 'turban', hatColor: 0xffffff, accessory: 'beard', pitch: 0.8,
      fav: { comida: 'cuscuz', cor: 'branco', hobby: 'ler as estrelas' },
      greet: ['As dunas mudam, mas eu conheço cada uma. Quer um guia?', 'Que o vento esteja a seu favor, viajante.'] },
    { id: 'zeca', name: 'Zeca', role: 'menino do acampamento', home: [8, -6], scale: 0.75, skin: 0xd9a073, shirt: 0x2fa37a, pants: 0xf0d9a0, hair: 0x2b1b12, hairStyle: 'short', pitch: 1.65,
      fav: { comida: 'pão sírio', cor: 'verde', hobby: 'brincar com a fogueira' },
      greet: ['Ei! Já viu uma pirâmide de perto? Elas são ENORMES!', 'Oi! Quer ver a fogueira? É quentinha!'] },
    { id: 'omar', name: 'Mestre Omar', role: 'arqueólogo', home: [-5.5, 8], skin: 0xb98056, shirt: 0x8a6d3b, pants: 0x4a3a22, hair: 0xdddddd, hairStyle: 'short', hat: 'straw', hatColor: 0x8a6d3b, accessory: 'glasses', pitch: 0.9,
      fav: { comida: 'chá de hortelã', cor: 'marrom', hobby: 'decifrar hieróglifos' },
      greet: ['Fascinante! Mais um aventureiro! Já ouviu falar das estrelas perdidas?', 'Cuidado onde pisa: pode haver história sob seus pés!'] }
  ],
  snow: [
    { id: 'helga', name: 'Vovó Helga', role: 'tricoteira', home: [5.5, 4.5], skin: 0xf0cfb4, shirt: 0xb83a4b, pants: 0x4a3a5a, hair: 0xe9e9ef, hairStyle: 'bun', accessory: 'scarf', scarfColor: 0xffd166, pitch: 1.25,
      fav: { comida: 'chocolate quente', cor: 'vermelho', hobby: 'tricotar cachecóis' },
      greet: ['Venha se aquecer, meu bem! Tenho chocolate quente!', 'Ah, que bom ter companhia nesse friozão!'] },
    { id: 'nico', name: 'Nico', role: 'menino do trenó', home: [-7, -5], scale: 0.75, skin: 0xf1c9a5, shirt: 0x2a9d8f, pants: 0x264653, hair: 0x6b3f1d, hairStyle: 'short', hat: 'beanie', hatColor: 0xe76f51, accessory: 'scarf', scarfColor: 0xe9c46a, pitch: 1.7,
      fav: { comida: 'biscoito de gengibre', cor: 'laranja', hobby: 'descer a ladeira de trenó' },
      greet: ['Eba! Alguém pra brincar na neve!', 'Cuidado, o chão escorrega! Hihi!'] },
    { id: 'lars', name: 'Lars', role: 'lenhador', home: [7.5, -6.5], skin: 0xd9a784, shirt: 0xb5342b, pants: 0x2c3a55, hair: 0xc77d2b, hairStyle: 'short', hat: 'beanie', hatColor: 0x2c3a55, accessory: 'beard', pitch: 0.7,
      fav: { comida: 'sopa de abóbora', cor: 'vermelho xadrez', hobby: 'cortar lenha' },
      greet: ['Hohoho! Mais um aventureiro! Entra, tem lenha de sobra!', 'Frio bom pra trabalhar, né?']},
    { id: 'ivy', name: 'Dra. Ivy', role: 'cientista da aurora', home: [-5.5, 8], skin: 0xe2b48f, shirt: 0xf4f4f8, pants: 0x4a4e69, hair: 0x7b2cbf, hairStyle: 'long', accessory: 'glasses', pitch: 1.4,
      fav: { comida: 'fondue', cor: 'roxo', hobby: 'estudar a aurora boreal' },
      greet: ['Olhe para o céu! A aurora está dançando hoje!', 'Olá! Estou medindo o brilho das estrelas daqui.'] }
  ]
};

const MAPINFO = {
  lobby:   { name: 'Saguão PicoVerso', desc: 'o saguão espacial onde tudo começa', weather: 'O céu aqui é cheio de estrelas e nunca chove.' },
  village: { name: 'Vila Verde', desc: 'uma vila de campo com fonte, moinho e casas coloridas', weather: 'O sol está lindo, com um ventinho gostoso e passarinhos cantando.' },
  desert:  { name: 'Oásis do Pôr do Sol', desc: 'um oásis no deserto com pirâmides distantes', weather: 'O sol está se pondo e o ar ainda está quentinho.' },
  snow:    { name: 'Vale Nevado', desc: 'um vale de neve com cabanas e aurora boreal', weather: 'Está bem frio, neva devagar e a aurora brilha no céu.' }
};

const Brain = (() => {
  const mem = { name: null, topics: {}, last: {} };
  try { const m = JSON.parse(localStorage.getItem('pico_mem') || '{}'); if (m.name) mem.name = m.name; } catch (e) {}
  const save = () => { try { localStorage.setItem('pico_mem', JSON.stringify({ name: mem.name })); } catch (e) {} };
  const norm = s => s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9?! ]/g, ' ').replace(/\s+/g, ' ').trim();
  const cap = s => s.charAt(0).toUpperCase() + s.slice(1);
  const pick = (arr, key) => {
    let i = Math.floor(Math.random() * arr.length);
    if (key && arr.length > 1) { if (i === mem.last[key]) i = (i + 1) % arr.length; mem.last[key] = i; }
    return arr[i];
  };
  const nm = () => mem.name || 'amigo';

  const JOKES = [
    'Por que o computador foi ao médico? Porque estava com vírus!',
    'O que a vaca disse para o fazendeiro? Muuuito obrigada pelo pasto!',
    'Qual é o cúmulo da velocidade? Fechar a porta de um carro e pegar o dedo do vento!',
    'Por que o livro de matemática ficou triste? Porque tinha muitos problemas!',
    'O que o oceano disse para a praia? Nada, só deu uma onda!',
    'Por que o astronauta terminou o namoro? Ele precisava de espaço!',
    'Qual é o animal mais antigo? A zebra, porque é em preto e branco!'
  ];

  function rules(npc, ctx) {
    const r = [];
    const R = (re, fn) => r.push([re, fn]);
    R(/(meu nome e|me chamo|pode me chamar de|meu nome eh)\s+([a-z]{2,})/, m => {
      mem.name = cap(m[2]); save();
      return pick([`Prazer, ${mem.name}! Vou lembrar do seu nome.`, `${mem.name}... que nome bonito! Agora somos amigos.`], 'name');
    });
    R(/(tudo bem|como (voce )?(esta|vai)|como vc|td bem)/, () =>
      pick(['Estou ótimo, obrigado por perguntar! E você?', 'Tudo ótimo! O dia está lindo para uma aventura.', 'Muito bem! Conversar com você melhora meu dia.'], 'how'));
    R(/(quem (e )?(voce|vc)|seu nome|como (voce )?se chama|qual (e )?seu nome)/, () =>
      npc.robot ? 'Eu sou o Pico, uma inteligência artificial que mora no PicoVerso. Posso conversar, dar dicas e contar piadas!'
        : `Eu sou ${npc.name}, ${npc.role} daqui. E você, como se chama?`);
    R(/(voce (e|eh) (uma )?(ia|robo|real|humano|gente)|e uma ia|e um robo)/, () =>
      npc.robot ? 'Sou sim, uma IA! Mas bem simpática, prometo.'
        : 'Sou um personagem do PicoVerso com inteligência artificial! Para mim, tudo isso aqui é bem real.');
    R(/(o que (voce )?faz|trabalho|profissao|seu emprego)/, () =>
      npc.robot ? 'Eu ajudo os visitantes: dou dicas, converso e abro os mapas.' : `Sou ${npc.role}. ${cap(npc.fav.hobby)} é minha paixão!`);
    R(/(estrela|tesouro|missao|quest|objetivo|o que fazer|como (jogar|ganhar|vencer)|ajuda|dica)/, () => {
      if (ctx.mapId === 'lobby') return 'Escolha um mapa no menu e depois procure as estrelas douradas espalhadas pelo mundo. Pegue todas para vencer!';
      const left = ctx.starsTotal - ctx.starsFound;
      if (left <= 0) return 'Você já pegou todas as estrelas! Que campeão! Que tal visitar outro mundo?';
      return `Faltam ${left} estrela${left > 1 ? 's' : ''}! ${ctx.starHint ? ctx.starHint() : 'Olhe atrás das casas e dentro delas!'}`;
    });
    R(/(casa|moradia|morar|entrar|porta|abrir)/, () => 'Para entrar numa casa, aponte para a porta e clique para abri-la. Tem estrelas escondidas lá dentro, sabia?');
    R(/(mapa|lugar|onde (estou|estamos)|aqui|mundo)/, () => `Estamos em ${ctx.mapName}: ${MAPINFO[ctx.mapId].desc}. Nos mapas do menu você pode viajar para outros mundos!`);
    R(/(clima|tempo|frio|calor|sol\b|neve|chuva|vento)/, () => MAPINFO[ctx.mapId].weather);
    R(/(piada|engracad|rir|brincadeira|divert)/, () => pick(JOKES, 'joke'));
    R(/(obrigad|valeu|brigad|agradec)/, () => pick(['Imagina, ' + nm() + '! Estou aqui para isso.', 'De nada! Foi um prazer ajudar.', 'Sempre às ordens!'], 'thx'));
    R(/(tchau|adeus|ate (logo|mais|breve)|flw|falou|vou embora)/, () => pick([`Até logo, ${nm()}! Volte sempre!`, 'Tchau, tchau! Boa aventura!', 'Foi ótimo conversar! Até a próxima!'], 'bye'));
    R(/(comida|comer|fome|cor favorita|cor\b|hobby|gosta|favorit)/, () => {
      if (npc.robot) return 'Eu gosto de eletricidade, a cor azul e de aprender com você!';
      return `Minha comida favorita é ${npc.fav.comida}, minha cor é ${npc.fav.cor} e adoro ${npc.fav.hobby}. E você?`;
    });
    R(/(idade|quantos anos)/, () => npc.robot ? 'Fui ligado hoje! Tenho zero dias e muita curiosidade.' : 'Idade é só um número, mas o suficiente para ter muitas histórias!');
    R(/(controle|andar|mover|touch|clicar|botao|teclado|menu)/, () =>
      'Deslize o touch de trás para frente para andar, para os lados para girar. Clique para escolher botões, pessoas e portas. O botão lateral abre o menu.');
    R(/(musica|cantar|cancao|som)/, () => pick(['Escute a música do mundo, é composta na hora, só para você!', 'La la lá... eu cantaria, mas só sei fazer bip-bop.'], 'mus'));
    R(/(lindo|bonit|legal|incrivel|massa|top|show|maravilh)/, () => pick(['Fico feliz que tenha gostado! Este lugar é especial.', 'Verdade, né? Cada cantinho tem um charme.'], 'nice'));
    R(/(triste|cansad|sozinho|chateado)/, () => `Poxa, ${nm()}... respire fundo. Conversar ajuda, e eu estou aqui com você.`);
    R(/(amo voce|gosto de voce|te amo|amigo)/, () => 'Que fofo! Você também é meu amigo favorito do PicoVerso.');
    R(/\b(oi|ola|eai|e ai|opa|hey|hello|salve|bom dia|boa tarde|boa noite)\b/, () =>
      pick([`Olá, ${nm()}! Em que posso ajudar?`, `Oi, ${nm()}! Que bom te ver de novo!`, `E aí, ${nm()}! Tudo certo por aqui?`], 'hi'));
    R(/(sim|claro|com certeza|ok|certo)$/, () => pick(['Ótimo!', 'Combinado, então!', 'Perfeito!'], 'yes'));
    R(/(nao|nunca|talvez)$/, () => pick(['Tudo bem, sem pressa.', 'Entendi! Quem sabe depois.'], 'no'));
    return r;
  }

  function reply(npc, text, ctx) {
    const t = norm(text);
    if (!t) return 'Hum? Não ouvi direito. Pode repetir?';
    const nm0 = text.match(/(?:meu nome (?:é|e|eh)|me chamo|pode me chamar de)\s+([A-Za-zÀ-ÿ]{2,})/i);
    if (nm0) { mem.name = cap(nm0[1].toLowerCase()); save(); return pick([`Prazer, ${mem.name}! Vou lembrar do seu nome.`, `${mem.name}... que nome bonito! Agora somos amigos.`], 'name'); }
    for (const [re, fn] of rules(npc, ctx)) {
      const m = t.match(re);
      if (m) return fn(m);
    }
    const words = t.replace(/[?!]/g, '').split(' ').filter(w => w.length > 3 && !/^(isso|esse|essa|muito|para|como|pode|voce|sobre|quero|queria)$/.test(w));
    const topic = words.sort((a, b) => b.length - a.length)[0];
    if (/\?$/.test(t)) {
      return topic
        ? pick([`Boa pergunta sobre "${topic}", ${nm()}! Não sei tudo, mas acho que dá para descobrir explorando o mundo.`, `Hmm, "${topic}"... deixe-me pensar. Talvez outro morador saiba mais!`], 'q')
        : pick(['Boa pergunta! Ainda estou aprendendo, mas gosto de pensar nisso.', 'Hmm, difícil! Que tal me perguntar sobre o lugar ou sobre estrelas?'], 'q2');
    }
    return topic
      ? pick([`Interessante, "${topic}"! Conte mais, ${nm()}.`, `Nunca pensei muito em "${topic}", mas adoro aprender coisas novas.`, `${cap(topic)}, é? Isso me lembra ${npc.robot ? 'meus circuitos' : npc.fav.hobby}!`], 'g')
      : pick(['Entendi! E o que mais?', 'Hmm, interessante. Pode contar mais?', 'Que legal! Continue.'], 'g2');
  }

  const delay = ms => new Promise(r => setTimeout(r, ms));
  async function ask(npc, text, ctx) {
    let url = null; try { url = localStorage.getItem('pico_ai_url'); } catch (e) {}
    if (url) {
      try {
        const sys = `Você é ${npc.name}, ${npc.role} no mapa "${ctx.mapName}" do jogo PicoVerso. Responda em português, em no máximo duas frases curtas, no tom do personagem.`;
        const r = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ system: sys, message: text, player: mem.name }) });
        const j = await r.json(); if (j && j.reply) return String(j.reply);
      } catch (e) { /* cai no modo offline */ }
    }
    await delay(450 + Math.random() * 500);
    return reply(npc, text, ctx);
  }

  return { ask, reply, mem, setName(n) { mem.name = n; save(); }, getName() { return mem.name; }, cap };
})();
